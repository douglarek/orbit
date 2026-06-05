package build.orbit.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public record WorkspaceConfig(Path projectRoot, List<String> contextFiles) {
    public static WorkspaceConfig load(Path projectRoot, Path configDir) throws Exception {
        Files.createDirectories(configDir);

        List<String> contextFiles = new ArrayList<>();
        Path agentsMd = projectRoot.resolve("AGENTS.md");
        if (Files.exists(agentsMd)) {
            System.out.println("Loading AGENTS.md: " + agentsMd.toAbsolutePath());
            return new WorkspaceConfig(projectRoot, contextFiles);
        }

        Path legacyAgentsMd = projectRoot.resolve(".agentscope/workspace/AGENTS.md");
        if (Files.exists(legacyAgentsMd)) {
            System.out.println("Loading AGENTS.md: " + legacyAgentsMd.toAbsolutePath());
            contextFiles.add(".agentscope/workspace/AGENTS.md");
        }
        return new WorkspaceConfig(projectRoot, contextFiles);
    }
}
