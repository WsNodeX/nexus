package nexus.llm;

import nexus.core.Json;

import java.util.List;

/**
 * A chat completion result. Either {@code content} (free text) or {@code
 * toolCalls} (model-requested tool invocations) is populated, mirroring the
 * OpenAI response shape.
 */
public class ChatResponse {
    public String id;
    public String model;
    public String content;
    public List<ToolCall> toolCalls;
    public Usage usage = Usage.empty();
    public Json raw;

    public ChatResponse() {}

    public ChatResponse(String content) {
        this.content = content;
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
