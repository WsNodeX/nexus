package nexus.llm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A chat completion request. Mirrors the OpenAI chat schema so it can be
 * serialized directly, while staying provider-agnostic: the {@link Provider}
 * implementations translate it to their own wire format.
 */
public class ChatRequest {
    public String model;
    public List<Message> messages = new ArrayList<>();
    public List<Tool> tools;
    /** "auto" | "none" | "required" | a function-name string. */
    public String toolChoice;
    public Double temperature;
    public Integer maxTokens;
    public Boolean stream;
    /** Provider-specific extra fields merged into the request body. */
    public Map<String, Object> extra = new HashMap<>();

    public ChatRequest() {}

    public ChatRequest(String model) {
        this.model = model;
    }

    public ChatRequest add(Message m) {
        messages.add(m);
        return this;
    }

    public ChatRequest add(Tool t) {
        if (tools == null) tools = new ArrayList<>();
        tools.add(t);
        return this;
    }

    public ChatRequest temperature(double v) { this.temperature = v; return this; }

    public ChatRequest maxTokens(int v) { this.maxTokens = v; return this; }

    public ChatRequest stream(boolean v) { this.stream = v; return this; }

    public ChatRequest toolChoice(String v) { this.toolChoice = v; return this; }

    public ChatRequest extra(String k, Object v) { this.extra.put(k, v); return this; }
}
