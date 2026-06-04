package build.orbit.config;

import java.nio.file.Files;
import java.nio.file.Path;

public record SystemPromptConfig(String prompt, Path path, boolean loadedFromFile) {
    private static final String DEFAULT_PROMPT = """
            You are Orbit, a coding agent focused on executing code tasks.
            Use the provided tools to read and write files and run shell commands.
            Principles: understand the request before acting; verify with tools instead of
            guessing; keep changes minimal and correct; briefly summarize what you did.
            """;

    public static SystemPromptConfig load(Path configDir) throws Exception {
        Path path = configDir.resolve("prompts/system.md");
        if (Files.exists(path)) {
            System.out.println("Loading system prompt: " + path.toAbsolutePath());
            return new SystemPromptConfig(Files.readString(path), path, true);
        }
        return new SystemPromptConfig(DEFAULT_PROMPT, path, false);
    }
}
