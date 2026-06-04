package build.orbit.config;

import io.agentscope.core.model.EndpointType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record AppConfig(
        Path projectRoot,
        Path configDir,
        String agentName,
        String model,
        Boolean enableThinking,
        EndpointType dashScopeEndpoint,
        boolean debugEvents,
        boolean markdownPreview,
        String sessionId) {
    private static final String DEFAULT_MODEL = "dashscope:qwen3.6-plus";
    private static final Pattern UUID_SUFFIX = Pattern.compile(
            "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$");

    public static AppConfig from(String[] args) {
        Path projectRoot = resolveProjectRoot(args);
        Path configDir = resolveConfigDir(args);
        String model = resolveModel(args);
        Boolean enableThinking = resolveEnableThinking(args);
        String sessionId = resolveSessionId(args, projectRoot);
        return new AppConfig(
                projectRoot,
                configDir,
                resolveAgentName(args, sessionId),
                model,
                enableThinking,
                resolveDashScopeEndpoint(args),
                resolveDebugEvents(args),
                resolveMarkdownPreview(args),
                sessionId);
    }

    private static Path resolveProjectRoot(String[] args) {
        String value = firstConfigValue(args, "--workspace=", "agentscope.workspace", "AGENTSCOPE_WORKSPACE");
        if (value != null) {
            return Paths.get(value).toAbsolutePath().normalize();
        }
        return Paths.get("").toAbsolutePath().normalize();
    }

    private static Path resolveConfigDir(String[] args) {
        String value = firstConfigValue(args, "--config-dir=", "orbit.configDir", "ORBIT_HOME");
        if (value != null) {
            return Paths.get(value).toAbsolutePath().normalize();
        }
        return Paths.get(System.getProperty("user.home"), ".orbit").toAbsolutePath().normalize();
    }

    private static String resolveModel(String[] args) {
        String value = firstConfigValue(args, "--model=", "agentscope.model", "AGENTSCOPE_MODEL");
        return value == null ? DEFAULT_MODEL : value;
    }

    private static Boolean resolveEnableThinking(String[] args) {
        for (String arg : args) {
            if ("--thinking".equals(arg)) {
                return true;
            }
            if ("--no-thinking".equals(arg)) {
                return false;
            }
        }

        String value = firstConfigValue(
                args, "--enable-thinking=", "agentscope.enableThinking", "AGENTSCOPE_ENABLE_THINKING");
        return value == null ? true : Boolean.parseBoolean(value);
    }

    private static EndpointType resolveDashScopeEndpoint(String[] args) {
        String value = firstConfigValue(
                args, "--dashscope-endpoint=", "agentscope.dashscopeEndpoint", "AGENTSCOPE_DASHSCOPE_ENDPOINT");
        return value == null ? EndpointType.AUTO : parseEndpointType(value);
    }

    private static EndpointType parseEndpointType(String value) {
        return switch (value.trim().toLowerCase()) {
            case "text" -> EndpointType.TEXT;
            case "multimodal" -> EndpointType.MULTIMODAL;
            case "auto" -> EndpointType.AUTO;
            default -> throw new IllegalArgumentException(
                    "Unsupported DashScope endpoint: " + value + " (expected auto, text, or multimodal)");
        };
    }

    private static boolean resolveDebugEvents(String[] args) {
        if (contains(args, "--debug-events")) {
            return true;
        }
        String value = firstConfigValue(args, null, "agentscope.debugEvents", "AGENTSCOPE_DEBUG_EVENTS");
        return value != null && Boolean.parseBoolean(value);
    }

    private static boolean resolveMarkdownPreview(String[] args) {
        if (contains(args, "--raw-markdown")) {
            return false;
        }
        if (contains(args, "--markdown-preview")) {
            return true;
        }

        String value = firstConfigValue(
                args, null, "agentscope.markdownPreview", "AGENTSCOPE_MARKDOWN_PREVIEW");
        return value == null || Boolean.parseBoolean(value);
    }

    private static String resolveSessionId(String[] args, Path workspace) {
        String value = firstConfigValue(args, "--session=", "agentscope.session", "AGENTSCOPE_SESSION");
        return value == null ? workspace + "-" + UUID.randomUUID() : value;
    }

    private static String resolveAgentName(String[] args, String sessionId) {
        String value = firstConfigValue(args, "--agent-name=", "agentscope.agentName", "AGENTSCOPE_AGENT_NAME");
        if (value != null) {
            return sanitizeName(value);
        }

        Matcher matcher = UUID_SUFFIX.matcher(sessionId);
        if (matcher.find()) {
            return "orbit-" + matcher.group(1);
        }

        return "orbit-" + shortHash(sessionId);
    }

    private static String sanitizeName(String value) {
        String sanitized = value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        if (sanitized.isBlank()) {
            throw new IllegalArgumentException("Agent name must not be blank");
        }
        return sanitized;
    }

    private static String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash session id", e);
        }
    }

    private static boolean contains(String[] args, String expected) {
        for (String arg : args) {
            if (expected.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static String firstConfigValue(
            String[] args, String argPrefix, String systemProperty, String environmentVariable) {
        if (argPrefix != null) {
            for (String arg : args) {
                if (arg.startsWith(argPrefix)) {
                    return arg.substring(argPrefix.length());
                }
            }
        }

        String property = System.getProperty(systemProperty);
        if (property != null && !property.isBlank()) {
            return property;
        }

        String environment = System.getenv(environmentVariable);
        if (environment != null && !environment.isBlank()) {
            return environment;
        }

        return null;
    }
}
