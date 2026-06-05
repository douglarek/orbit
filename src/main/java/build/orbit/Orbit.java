package build.orbit;

import build.orbit.agent.AgentFactory;
import build.orbit.config.AppConfig;
import build.orbit.config.SystemPromptConfig;
import build.orbit.config.WorkspaceConfig;
import build.orbit.terminal.StreamPrinter;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

public class Orbit {
    public static void main(String[] args) throws Exception {
        AppConfig config;
        try {
            config = AppConfig.from(args);
        } catch (RuntimeException error) {
            System.out.println("Configuration error: " + rootMessage(error));
            return;
        }
        WorkspaceConfig workspace = WorkspaceConfig.load(config.projectRoot(), config.configDir());
        SystemPromptConfig systemPrompt = SystemPromptConfig.load(config.configDir());

        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId(config.sessionId())
                .userId("alice")
                .build();

        System.out.println("Coding agent started. Type exit or quit to stop.");
        System.out.println("Model: " + config.model());
        System.out.println("Thinking: "
                + (config.enableThinking() == null ? "provider default" : config.enableThinking()));
        if (config.model().startsWith("openai:")) {
            System.out.println("OpenAI base URL: "
                    + (config.openAiBaseUrl() == null ? "provider default" : config.openAiBaseUrl()));
        }
        System.out.println("Session: " + config.sessionId());
        System.out.println("Agent: " + config.agentName());
        System.out.println("Config: " + config.configDir());

        HarnessAgent agent = null;
        try (Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .provider("dumb")
                .jni(false)
                .jna(false)
                .jansi(false)
                .ffm(false)
                .build()) {
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .build();
            while (true) {
                String input;
                try {
                    input = reader.readLine("> ").trim();
                } catch (EndOfFileException | UserInterruptException ignored) {
                    break;
                }
                if (input.isEmpty()) {
                    continue;
                }
                if ("exit".equalsIgnoreCase(input) || "quit".equalsIgnoreCase(input)) {
                    break;
                }

                StreamPrinter printer = new StreamPrinter(
                        config.debugEvents(), config.markdownPreview());
                try {
                    if (agent == null) {
                        agent = AgentFactory.build(config, workspace, systemPrompt);
                    }
                    agent.streamEvents(new UserMessage(input), ctx)
                            .doOnNext(printer::print)
                            .blockLast();
                } catch (RuntimeException error) {
                    printer.finishLine();
                    System.out.println("Error: " + rootMessage(error));
                }
            }
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getName() : message;
    }
}
