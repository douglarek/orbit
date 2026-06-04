package build.orbit.agent;

import build.orbit.config.AppConfig;
import build.orbit.config.SystemPromptConfig;
import build.orbit.config.WorkspaceConfig;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.HarnessAgent;

public final class AgentFactory {
    private AgentFactory() {}

    public static HarnessAgent build(AppConfig config, WorkspaceConfig workspace, SystemPromptConfig systemPrompt) {
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name(config.agentName())
                .agentId(config.agentName())
                .sysPrompt(systemPrompt.prompt())
                .workspace(config.configDir())
                .filesystem(new LocalFilesystemSpec().project(workspace.projectRoot()))
                .enablePendingToolRecovery(true)
                .disableMemoryTools()
                .disableMemoryHooks();

        for (String contextFile : workspace.contextFiles()) {
            builder.additionalContextFile(contextFile);
        }

        if (config.model().startsWith("dashscope:") && config.enableThinking() != null) {
            builder.model(buildDashScopeModel(config));
        } else {
            builder.model(config.model());
        }

        return builder.build();
    }

    private static DashScopeChatModel buildDashScopeModel(AppConfig config) {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Environment variable DASHSCOPE_API_KEY is required for " + config.model());
        }

        String modelName = config.model().substring("dashscope:".length());
        return DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .stream(true)
                .enableThinking(config.enableThinking())
                .endpointType(config.dashScopeEndpoint())
                .build();
    }
}
