package build.orbit.terminal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentStartEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import java.util.HashMap;
import java.util.Map;

public final class StreamPrinter {
    private static final String DIM = "\u001B[2m";
    private static final String GRAY = "\u001B[90m";
    private static final String RESET = "\u001B[0m";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final boolean debugEvents;
    private final MarkdownPreview markdownPreview;
    private final Map<String, ToolCallInfo> toolCalls = new HashMap<>();
    private boolean inThinking;
    private boolean wroteContent;
    private boolean lineOpen;
    private int pendingToolResults;

    public StreamPrinter(boolean debugEvents, boolean markdownPreview) {
        this.debugEvents = debugEvents;
        this.markdownPreview = new MarkdownPreview(markdownPreview);
    }

    public void print(Object event) {
        if (event instanceof AgentStartEvent start) {
            if (debugEvents) {
                System.out.println("[start replyId=" + start.getReplyId() + "]");
            }
        } else if (event instanceof ThinkingBlockDeltaEvent delta) {
            startThinkingIfNeeded();
            printDelta(delta.getDelta());
            wroteContent = true;
        } else if (event instanceof TextBlockDeltaEvent delta) {
            stopThinkingIfNeeded();
            printDelta(delta.getDelta());
            wroteContent = true;
        } else if (event instanceof ToolCallStartEvent toolCall) {
            toolCalls.put(toolCall.getToolCallId(), new ToolCallInfo(toolCall.getToolCallName()));
        } else if (event instanceof ToolCallDeltaEvent delta) {
            toolCalls.computeIfAbsent(delta.getToolCallId(), ignored -> new ToolCallInfo("tool"))
                    .arguments()
                    .append(delta.getDelta());
        } else if (event instanceof ToolCallEndEvent toolCall) {
            stopThinkingIfNeeded();
            markdownPreview.flush();
            printLineBeforeStatus();
            ToolCallInfo info = toolCalls.get(toolCall.getToolCallId());
            System.out.println("[Tool call: " + formatToolCall(info) + "]");
            pendingToolResults++;
        } else if (event instanceof ToolResultEndEvent toolResult) {
            stopThinkingIfNeeded();
            markdownPreview.flush();
            if (pendingToolResults > 0) {
                pendingToolResults--;
            }
            if (!"SUCCESS".equals(String.valueOf(toolResult.getState()))) {
                System.out.println("[Tool result: " + toolResult.getState() + "]");
            }
        } else if (event instanceof AgentEndEvent) {
            stopThinkingIfNeeded();
            markdownPreview.flush();
            if (lineOpen) {
                System.out.println();
                lineOpen = false;
            }
            if (debugEvents) {
                if (wroteContent) {
                    System.out.println();
                }
                System.out.println("[完成]");
            }
        }
    }

    public void finishLine() {
        stopThinkingIfNeeded();
        markdownPreview.flush();
        if (lineOpen) {
            System.out.println();
            lineOpen = false;
        }
    }

    private void startThinkingIfNeeded() {
        if (!inThinking) {
            printLineBeforeStatus();
            System.out.print(DIM + GRAY);
            inThinking = true;
        }
    }

    private void stopThinkingIfNeeded() {
        if (inThinking) {
            System.out.print(RESET);
            System.out.println();
            lineOpen = false;
            inThinking = false;
        }
    }

    private void printLineBeforeStatus() {
        if (wroteContent) {
            System.out.println();
        }
    }

    private void printDelta(String delta) {
        if (inThinking) {
            System.out.print(delta);
        } else {
            markdownPreview.print(delta);
        }
        if (!delta.isEmpty()) {
            lineOpen = delta.charAt(delta.length() - 1) != '\n';
        }
    }

    private String formatToolCall(ToolCallInfo info) {
        if (info == null) {
            return "tool...";
        }

        String detail = extractUsefulArgument(info.name(), info.arguments().toString());
        if (detail.isBlank()) {
            return info.name() + "...";
        }
        return info.name() + ": " + detail;
    }

    private String extractUsefulArgument(String toolName, String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return "";
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(arguments);
            if ("execute".equals(toolName)) {
                String command = firstText(root, "cmd", "command", "script");
                if (!command.isBlank()) {
                    return compact(command);
                }
            }
            return compact(root.toString());
        } catch (Exception ignored) {
            return compact(arguments);
        }
    }

    private String firstText(JsonNode root, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = root.get(fieldName);
            if (value != null && value.isTextual()) {
                return value.asText();
            }
        }
        return "";
    }

    private String compact(String value) {
        String compacted = value.replaceAll("\\s+", " ").trim();
        if (compacted.length() <= 160) {
            return compacted;
        }
        return compacted.substring(0, 157) + "...";
    }
}
