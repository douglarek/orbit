package build.orbit.agent;

import build.orbit.config.AppConfig;
import build.orbit.config.SystemPromptConfig;
import build.orbit.config.WorkspaceConfig;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.ModelRegistry;
import io.agentscope.core.model.OpenAIChatModel;
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

        registerModelFactories(config);
        builder.model(config.model());

        return builder.build();
    }

    private static void registerModelFactories(AppConfig config) {
        ModelRegistry.registerFactory("dashscope:.*", modelId -> buildDashScopeModel(config, modelId));
        ModelRegistry.registerFactory("openai:.*", modelId -> buildOpenAiCompatibleModel(config, modelId));
    }

    private static OpenAIChatModel buildOpenAiCompatibleModel(AppConfig config, String modelId) {
        String apiKey = config.openAiApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "openai.apiKey is required in " + config.configFile() + " for " + modelId);
        }

        OpenAIChatModel.Builder builder = OpenAIChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelId.substring(modelId.indexOf(':') + 1))
                .stream(true);

        if (config.openAiBaseUrl() != null && !config.openAiBaseUrl().isBlank()) {
            builder.baseUrl(config.openAiBaseUrl());
        }
        if (config.openAiEndpointPath() != null && !config.openAiEndpointPath().isBlank()) {
            builder.endpointPath(config.openAiEndpointPath());
        }

        return builder.build();
    }

    private static DashScopeChatModel buildDashScopeModel(AppConfig config, String modelId) {
        String apiKey = config.dashScopeApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "dashscope.apiKey is required in " + config.configFile() + " for " + modelId);
        }

        return DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelId.substring(modelId.indexOf(':') + 1))
                .stream(true)
                .enableThinking(config.enableThinking())
                .endpointType(config.dashScopeEndpoint())
                .build();
    }
}
