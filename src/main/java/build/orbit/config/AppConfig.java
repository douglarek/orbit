package build.orbit.config;

import io.agentscope.core.model.EndpointType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

public record AppConfig(
        Path projectRoot,
        Path configDir,
        String agentName,
        String model,
        Boolean enableThinking,
        EndpointType dashScopeEndpoint,
        String dashScopeApiKey,
        String openAiApiKey,
        String openAiBaseUrl,
        String openAiEndpointPath,
        boolean debugEvents,
        boolean markdownPreview,
        String sessionId) {
    private static final String DEFAULT_MODEL = "dashscope:qwen3.6-plus";
    private static final String CONFIG_FILE_NAME = "config.toml";
    private static final String DASHSCOPE_MODEL_PREFIX = "dashscope:";
    private static final String OPENAI_MODEL_PREFIX = "openai:";
    private static final String OPENAI_COMPATIBLE_MODEL_PREFIX = "openai-compatible:";
    private static final Pattern UUID_SUFFIX = Pattern.compile(
            "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$");

    public static AppConfig from(String[] args) {
        Path configDir = resolveConfigDir(args);
        TomlParseResult toml = loadConfig(configDir);
        Path projectRoot = resolveProjectRoot(args, toml);
        String model = resolveModel(args, toml);
        Boolean enableThinking = resolveEnableThinking(args, toml, model);
        String sessionId = resolveSessionId(args, toml, projectRoot);
        AppConfig config = new AppConfig(
                projectRoot,
                configDir,
                resolveAgentName(args, toml, sessionId),
                model,
                enableThinking,
                resolveDashScopeEndpoint(args, toml),
                firstConfigValue(args, "--dashscope-api-key=", "dashscope.apiKey", toml, "dashscope.apiKey"),
                firstConfigValue(args, "--openai-api-key=", "openai.apiKey", toml, "openai.apiKey"),
                firstConfigValue(args, "--openai-base-url=", "openai.baseUrl", toml, "openai.baseUrl"),
                firstConfigValue(
                        args, "--openai-endpoint-path=", "openai.endpointPath", toml, "openai.endpointPath"),
                resolveDebugEvents(args, toml),
                resolveMarkdownPreview(args, toml),
                sessionId);
        validateProviderConfig(config);
        return config;
    }

    public Path configFile() {
        return configDir.resolve(CONFIG_FILE_NAME);
    }

    private static TomlParseResult loadConfig(Path configDir) {
        Path configFile = configDir.resolve(CONFIG_FILE_NAME);
        if (!Files.exists(configFile)) {
            return null;
        }

        try {
            TomlParseResult result = Toml.parse(configFile);
            if (result.hasErrors()) {
                throw new IllegalArgumentException("Invalid config file " + configFile + ": "
                        + result.errors());
            }
            return result;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read config file " + configFile, e);
        }
    }

    private static Path resolveProjectRoot(String[] args, TomlParseResult toml) {
        String value = firstConfigValue(args, "--workspace=", "agentscope.workspace", toml, "workspace");
        if (value != null) {
            return Paths.get(value).toAbsolutePath().normalize();
        }
        return Paths.get("").toAbsolutePath().normalize();
    }

    private static Path resolveConfigDir(String[] args) {
        String value = firstConfigValue(args, "--config-dir=", "orbit.configDir", null);
        if (value != null) {
            return Paths.get(value).toAbsolutePath().normalize();
        }
        return Paths.get(System.getProperty("user.home"), ".orbit").toAbsolutePath().normalize();
    }

    private static String resolveModel(String[] args, TomlParseResult toml) {
        String value = firstConfigValue(args, "--model=", "agentscope.model", toml, "model");
        return value == null ? DEFAULT_MODEL : value;
    }

    private static Boolean resolveEnableThinking(String[] args, TomlParseResult toml, String model) {
        for (String arg : args) {
            if ("--thinking".equals(arg)) {
                return true;
            }
            if ("--no-thinking".equals(arg)) {
                return false;
            }
        }

        String value = firstConfigValue(
                args,
                "--enable-thinking=",
                "agentscope.enableThinking",
                toml,
                "enableThinking",
                "dashscope.enableThinking");
        if (value != null) {
            return Boolean.parseBoolean(value);
        }
        return model.startsWith(DASHSCOPE_MODEL_PREFIX) ? true : null;
    }

    private static EndpointType resolveDashScopeEndpoint(String[] args, TomlParseResult toml) {
        String value = firstConfigValue(
                args,
                "--dashscope-endpoint=",
                "agentscope.dashscopeEndpoint",
                toml,
                "dashscope.endpoint");
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

    private static boolean resolveDebugEvents(String[] args, TomlParseResult toml) {
        if (contains(args, "--debug-events")) {
            return true;
        }
        String value = firstConfigValue(args, null, "agentscope.debugEvents", toml, "debugEvents");
        return value != null && Boolean.parseBoolean(value);
    }

    private static boolean resolveMarkdownPreview(String[] args, TomlParseResult toml) {
        if (contains(args, "--raw-markdown")) {
            return false;
        }
        if (contains(args, "--markdown-preview")) {
            return true;
        }

        String value = firstConfigValue(
                args, null, "agentscope.markdownPreview", toml, "markdownPreview");
        return value == null || Boolean.parseBoolean(value);
    }

    private static String resolveSessionId(String[] args, TomlParseResult toml, Path workspace) {
        String value = firstConfigValue(args, "--session=", "agentscope.session", toml, "session");
        return value == null ? workspace + "-" + UUID.randomUUID() : value;
    }

    private static String resolveAgentName(String[] args, TomlParseResult toml, String sessionId) {
        String value = firstConfigValue(args, "--agent-name=", "agentscope.agentName", toml, "agentName");
        if (value != null) {
            return sanitizeName(value);
        }

        Matcher matcher = UUID_SUFFIX.matcher(sessionId);
        if (matcher.find()) {
            return "orbit-" + matcher.group(1);
        }

        return "orbit-" + shortHash(sessionId);
    }

    private static void validateProviderConfig(AppConfig config) {
        if (config.model().startsWith(DASHSCOPE_MODEL_PREFIX)
                && (config.dashScopeApiKey() == null || config.dashScopeApiKey().isBlank())) {
            throw new IllegalArgumentException(
                    "Missing DashScope config in " + config.configFile() + System.lineSeparator()
                            + "Add:" + System.lineSeparator()
                            + "model = \"dashscope:qwen3.6-plus\"" + System.lineSeparator()
                            + System.lineSeparator()
                            + "[dashscope]" + System.lineSeparator()
                            + "apiKey = \"...\"");
        }

        if ((config.model().startsWith(OPENAI_MODEL_PREFIX)
                        || config.model().startsWith(OPENAI_COMPATIBLE_MODEL_PREFIX))
                && (config.openAiApiKey() == null || config.openAiApiKey().isBlank())) {
            throw new IllegalArgumentException(
                    "Missing OpenAI-compatible config in " + config.configFile() + System.lineSeparator()
                            + "Add:" + System.lineSeparator()
                            + "model = \"openai-compatible:your-model\"" + System.lineSeparator()
                            + System.lineSeparator()
                            + "[openai]" + System.lineSeparator()
                            + "apiKey = \"...\"" + System.lineSeparator()
                            + "baseUrl = \"https://example.com/v1\"");
        }

        if (config.model().startsWith(OPENAI_COMPATIBLE_MODEL_PREFIX)
                && (config.openAiBaseUrl() == null || config.openAiBaseUrl().isBlank())) {
            throw new IllegalArgumentException(
                    "Missing openai.baseUrl in " + config.configFile()
                            + " for " + config.model());
        }
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
            String[] args, String argPrefix, String systemProperty, TomlParseResult toml, String... tomlKeys) {
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

        return firstTomlValue(toml, tomlKeys);
    }

    private static String firstTomlValue(TomlParseResult toml, String... keys) {
        if (toml == null) {
            return null;
        }

        for (String key : keys) {
            Object value = toml.get(key);
            if (value instanceof String string && !string.isBlank()) {
                return string;
            }
            if (value instanceof Boolean bool) {
                return Boolean.toString(bool);
            }
        }

        return null;
    }
}
