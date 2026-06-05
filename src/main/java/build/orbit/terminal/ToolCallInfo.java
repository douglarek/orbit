package build.orbit.terminal;

final class ToolCallInfo {
    private final String name;
    private final StringBuilder arguments = new StringBuilder();

    ToolCallInfo(String name) {
        this.name = name;
    }

    String name() {
        return name;
    }

    StringBuilder arguments() {
        return arguments;
    }
}
