package nexus.llm;

import nexus.core.Json;
import nexus.core.errors.ProviderException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Provider for any OpenAI-compatible chat endpoint (OpenAI, Together, Groq,
 * local llama.cpp / vLLM servers, ...). Uses only the JDK HTTP client and the
 * built-in JSON engine, so it carries zero third-party dependencies.
 */
public class OpenAiProvider implements Provider {

    private final String name;
    private final String baseUrl;
    private final String apiKey;
    private final HttpClient http;
    private final Set<String> capabilities;

    public OpenAiProvider(String name, String baseUrl, String apiKey) {
        this(name, baseUrl, apiKey, Duration.ofSeconds(60), Set.of("streaming", "function-calling"));
    }

    public OpenAiProvider(String name, String baseUrl, String apiKey, Duration timeout, Set<String> capabilities) {
        this.name = name;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.capabilities = capabilities;
    }

    @Override
    public String name() { return name; }

    @Override
    public Set<String> capabilities() { return capabilities; }

    @Override
    public ChatResponse chat(ChatRequest req) {
        Json body = buildBody(req, false);
        HttpRequest httpReq = post("/chat/completions", body);
        HttpResponse<String> resp;
        try {
            resp = http.send(httpReq, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ProviderException(name() + ": network error - " + e.getMessage(), -1, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderException(name() + ": interrupted", -1, e);
        }
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new ProviderException(name() + ": HTTP " + resp.statusCode() + " - " + resp.body(), resp.statusCode());
        }
        return parseResponse(Json.parse(resp.body()));
    }

    @Override
    public Stream<StreamChunk> chatStream(ChatRequest req) {
        if (!capabilities.contains("streaming")) {
            throw new UnsupportedOperationException(name() + " does not advertise streaming");
        }
        Json body = buildBody(req, true);
        HttpRequest httpReq = post("/chat/completions", body);
        HttpResponse<InputStream> resp;
        try {
            resp = http.send(httpReq, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw new ProviderException(name() + ": network error - " + e.getMessage(), -1, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderException(name() + ": interrupted", -1, e);
        }
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new ProviderException(name() + ": HTTP " + resp.statusCode(), resp.statusCode());
        }
        return SseParser.stream(resp.body());
    }

    private HttpRequest post(String path, Json body) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (apiKey != null && !apiKey.isEmpty()) {
            b.header("Authorization", "Bearer " + apiKey);
        }
        return b.build();
    }

    /** Build the OpenAI chat/completions request body (exposed for testing). */
    public Json buildBody(ChatRequest req, boolean stream) {
        Json b = Json.obj().put("model", req.model);
        Json msgs = Json.arr();
        for (Message m : req.messages) {
            msgs.add(messageToJson(m));
        }
        b.put("messages", msgs);
        if (req.tools != null && !req.tools.isEmpty()) {
            Json tools = Json.arr();
            for (Tool t : req.tools) tools.add(t.toJson());
            b.put("tools", tools);
            if (req.toolChoice != null) {
                if ("auto".equals(req.toolChoice) || "none".equals(req.toolChoice) || "required".equals(req.toolChoice)) {
                    b.put("tool_choice", req.toolChoice);
                } else {
                    b.put("tool_choice", Json.obj()
                            .put("type", "function")
                            .put("function", Json.obj().put("name", req.toolChoice)));
                }
            }
        }
        if (req.temperature != null) b.put("temperature", req.temperature);
        if (req.maxTokens != null) b.put("max_tokens", req.maxTokens);
        if (stream) b.put("stream", true);
        for (java.util.Map.Entry<String, Object> e : req.extra.entrySet()) {
            b.put(e.getKey(), String.valueOf(e.getValue()));
        }
        return b;
    }

    private Json messageToJson(Message m) {
        Json j = Json.obj().put("role", m.role.name());
        if (m.name != null) j.put("name", m.name);
        if (m.content != null) j.put("content", m.content);
        if (m.toolCallId != null) j.put("tool_call_id", m.toolCallId);
        if (m.toolCalls != null && !m.toolCalls.isEmpty()) {
            Json calls = Json.arr();
            for (ToolCall tc : m.toolCalls) {
                calls.add(Json.obj()
                        .put("id", tc.id)
                        .put("type", "function")
                        .put("function", Json.obj()
                                .put("name", tc.name)
                                .put("arguments", tc.arguments == null ? "{}" : tc.arguments)));
            }
            j.put("tool_calls", calls);
        }
        return j;
    }

    ChatResponse parseResponse(Json json) {
        ChatResponse r = new ChatResponse();
        r.raw = json;
        r.id = json.has("id") ? json.get("id").asString() : null;
        r.model = json.has("model") ? json.get("model").asString() : null;
        Json choices = json.get("choices");
        if (choices != null && choices.size() > 0) {
            Json first = choices.get(0);
            Json msg = first.get("message");
            if (msg != null) {
                Json content = msg.get("content");
                if (content != null && !content.isNull()) r.content = content.asString();
                Json calls = msg.get("tool_calls");
                if (calls != null && calls.size() > 0) {
                    r.toolCalls = new java.util.ArrayList<>();
                    for (Json c : calls.asArray()) {
                        Json fn = c.get("function");
                        ToolCall tc = new ToolCall();
                        tc.id = c.has("id") ? c.get("id").asString() : null;
                        tc.name = fn.get("name").asString();
                        tc.arguments = fn.has("arguments") ? fn.get("arguments").asString() : "{}";
                        r.toolCalls.add(tc);
                    }
                }
            }
        }
        Json usage = json.get("usage");
        if (usage != null) {
            r.usage = new Usage(
                    usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : 0,
                    usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : 0,
                    usage.has("total_tokens") ? usage.get("total_tokens").asInt() : 0);
        }
        return r;
    }
}
