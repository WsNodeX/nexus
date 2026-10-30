package nexus.llm;

import java.util.ArrayList;
import java.util.List;

/**
 * A single chat message in the OpenAI-compatible conversation schema. Assistant
 * turns may carry {@link ToolCall}s instead of (or in addition to) text; tool
 * turns reference the call they answer via {@code toolCallId}.
 */
public class Message {
    public enum Role { system, user, assistant, tool }

    public Role role;
    public String content;
    public String name;
    public String toolCallId;
    public List<ToolCall> toolCalls;

    public Message() {}

    public Message(Role role, String content) {
        this.role = role;
        this.content = content;
    }

    public static Message system(String content) { return new Message(Role.system, content); }

    public static Message user(String content) { return new Message(Role.user, content); }

    public static Message assistant(String content) {
        Message m = new Message(Role.assistant, content);
        return m;
    }

    public static Message assistant(List<ToolCall> calls) {
        Message m = new Message(Role.assistant, null);
        m.toolCalls = calls;
        return m;
    }

    public static Message tool(String toolCallId, String content) {
        Message m = new Message(Role.tool, content);
        m.toolCallId = toolCallId;
        return m;
    }

    public List<ToolCall> toolCalls() {
        if (toolCalls == null) toolCalls = new ArrayList<>();
        return toolCalls;
    }

    @Override
    public String toString() {
        return "Message{" + role + ", content=" + (content == null ? "null" : '"' + content + '"')
                + ", toolCalls=" + (toolCalls == null ? 0 : toolCalls.size()) + '}';
    }
}
