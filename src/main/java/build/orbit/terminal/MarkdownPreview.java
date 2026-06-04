package build.orbit.terminal;

import java.util.ArrayList;
import java.util.List;

final class MarkdownPreview {
    private static final String DIM = "\u001B[2m";
    private static final String GRAY = "\u001B[90m";
    private static final String BOLD = "\u001B[1m";
    private static final String CYAN = "\u001B[36m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RESET = "\u001B[0m";

    private final boolean enabled;
    private final StringBuilder pendingLine = new StringBuilder();
    private final List<List<String>> pendingTable = new ArrayList<>();
    private boolean inCodeBlock;

    MarkdownPreview(boolean enabled) {
        this.enabled = enabled;
    }

    void print(String text) {
        if (!enabled) {
            System.out.print(text);
            return;
        }

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            pendingLine.append(ch);
            if (ch == '\n') {
                flushPendingLine();
            }
        }
    }

    void flush() {
        if (!enabled) {
            return;
        }
        flushTable();
        if (pendingLine.isEmpty()) {
            return;
        }
        flushPendingLine();
    }

    private void flushPendingLine() {
        String line = pendingLine.toString();
        pendingLine.setLength(0);
        String rendered = renderLine(line);
        if (!rendered.isEmpty()) {
            System.out.print(rendered);
        }
    }

    private String renderLine(String line) {
        String lineEnding = "";
        if (line.endsWith("\n")) {
            lineEnding = "\n";
            line = line.substring(0, line.length() - 1);
        }

        String trimmed = line.trim();
        if (trimmed.startsWith("```")) {
            flushTable();
            inCodeBlock = !inCodeBlock;
            return DIM + GRAY + trimmed + RESET + lineEnding;
        }
        if (inCodeBlock) {
            flushTable();
            return DIM + line + RESET + lineEnding;
        }
        if (trimmed.isEmpty()) {
            flushTable();
            return lineEnding;
        }
        if (isTableRow(trimmed)) {
            List<String> cells = parseTableRow(trimmed);
            if (!isSeparatorRow(cells)) {
                pendingTable.add(cells);
            }
            return "";
        }

        flushTable();

        if (trimmed.startsWith("#")) {
            int level = countLeading(trimmed, '#');
            if (level <= 6 && trimmed.length() > level && trimmed.charAt(level) == ' ') {
                return CYAN + BOLD + renderInline(trimmed.substring(level + 1)) + RESET + lineEnding;
            }
        }
        if (trimmed.startsWith(">")) {
            return GRAY + "│ " + renderInline(trimmed.substring(1).trim()) + RESET + lineEnding;
        }
        if (trimmed.matches("^[-*+]\\s+.*")) {
            return "• " + renderInline(trimmed.substring(2)) + lineEnding;
        }
        if (trimmed.matches("^\\d+\\.\\s+.*")) {
            int dot = trimmed.indexOf('.');
            return YELLOW + trimmed.substring(0, dot + 1) + RESET + " "
                    + renderInline(trimmed.substring(dot + 2)) + lineEnding;
        }

        return renderInline(line) + lineEnding;
    }

    private void flushTable() {
        if (pendingTable.isEmpty()) {
            return;
        }

        int columnCount = 0;
        for (List<String> row : pendingTable) {
            columnCount = Math.max(columnCount, row.size());
        }

        int[] widths = new int[columnCount];
        for (List<String> row : pendingTable) {
            for (int i = 0; i < row.size(); i++) {
                widths[i] = Math.max(widths[i], displayWidth(stripInlineMarkup(row.get(i))));
            }
        }

        for (int rowIndex = 0; rowIndex < pendingTable.size(); rowIndex++) {
            List<String> row = pendingTable.get(rowIndex);
            StringBuilder line = new StringBuilder();
            for (int column = 0; column < columnCount; column++) {
                String cell = column < row.size() ? row.get(column) : "";
                String rendered = renderInline(cell);
                line.append(column == 0 ? "│ " : " │ ");
                if (rowIndex == 0) {
                    line.append(BOLD).append(rendered).append(RESET);
                } else {
                    line.append(rendered);
                }
                line.append(" ".repeat(Math.max(0, widths[column] - displayWidth(stripInlineMarkup(cell)))));
            }
            line.append(" │");
            System.out.println(line);
        }
        pendingTable.clear();
    }

    private boolean isTableRow(String line) {
        return line.startsWith("|") && line.endsWith("|") && line.indexOf('|', 1) > 0;
    }

    private List<String> parseTableRow(String line) {
        String body = line.substring(1, line.length() - 1);
        String[] parts = body.split("\\|", -1);
        List<String> cells = new ArrayList<>();
        for (String part : parts) {
            cells.add(part.trim());
        }
        return cells;
    }

    private boolean isSeparatorRow(List<String> cells) {
        if (cells.isEmpty()) {
            return false;
        }
        for (String cell : cells) {
            if (!cell.matches(":?-{3,}:?")) {
                return false;
            }
        }
        return true;
    }

    private String renderInline(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '`') {
                int end = text.indexOf('`', i + 1);
                if (end > i) {
                    out.append(YELLOW).append(text, i + 1, end).append(RESET);
                    i = end;
                    continue;
                }
            }
            if (text.startsWith("**", i)) {
                int end = text.indexOf("**", i + 2);
                if (end > i) {
                    out.append(BOLD).append(text, i + 2, end).append(RESET);
                    i = end + 1;
                    continue;
                }
            }
            out.append(text.charAt(i));
        }
        return out.toString();
    }

    private int countLeading(String text, char ch) {
        int count = 0;
        while (count < text.length() && text.charAt(count) == ch) {
            count++;
        }
        return count;
    }

    private String stripInlineMarkup(String text) {
        return text.replace("**", "").replace("`", "");
    }

    private int displayWidth(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += text.charAt(i) <= 0x7F ? 1 : 2;
        }
        return width;
    }
}
