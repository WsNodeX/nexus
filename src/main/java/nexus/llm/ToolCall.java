package nexus.llm;

/**
 * A tool invocation requested by the model. {@code arguments} is the raw JSON
 * payload exactly as the provider emitted it (callers parse it with
 * {@link nexus.core.Json#parse}).
 */
public class ToolCall {
    public String id;
    public String name;
    public String arguments;

    public ToolCall() {}

    public ToolCall(String id, String name, String arguments) {
        this.id = id;
        this.name = name;
        this.arguments = arguments;
    }

    @Override
    public String toString() {
        return "ToolCall{" + name + "(" + id + "): " + arguments + '}';
    }
}
