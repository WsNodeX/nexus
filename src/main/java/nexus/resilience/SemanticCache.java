package nexus.resilience;

import nexus.core.Json;
import nexus.llm.ChatRequest;
import nexus.llm.ChatResponse;
import nexus.llm.Message;
import nexus.llm.Tool;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * A deterministic request/response cache. The key is a stable hash of the model,
 * message contents, tool names and sampling parameters (the {@code stream} flag is
 * ignored so a cached non-streaming result can serve a streaming request). Entries
 * expire after a time-to-live, which bounds memory and lets freshness be tuned.
 *
 * <p>The "semantic" qualifier reflects the intent: identical meaning =&gt; identical
 * key. The current implementation is exact-match on the canonicalized request,
 * which is the safe default for LLM calls where semantically-equal prompts are
 * rarely byte-identical in practice.</p>
 */
public class SemanticCache {
    private final long ttlMs;
    private final Supplier<Long> clock;
    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    private static final class Entry {
        ChatResponse response;
        long expireAt;
    }

    public SemanticCache() {
        this(60_000);
    }

    public SemanticCache(long ttlMs) {
        this(ttlMs, System::currentTimeMillis);
    }

    public SemanticCache(long ttlMs, Supplier<Long> clock) {
        this.ttlMs = ttlMs;
        this.clock = clock;
    }

    public ChatResponse get(ChatRequest req) {
        String key = keyOf(req);
        Entry e = store.get(key);
        if (e == null) return null;
        if (clock.get() > e.expireAt) {
            store.remove(key);
            return null;
        }
        return e.response;
    }

    public void put(ChatRequest req, ChatResponse resp) {
        Entry e = new Entry();
        e.response = resp;
        e.expireAt = clock.get() + ttlMs;
        store.put(keyOf(req), e);
    }

    public int size() {
        return store.size();
    }

    public void clear() {
        store.clear();
    }

    public String keyOf(ChatRequest req) {
        Json c = Json.obj()
                .put("model", req.model == null ? "" : req.model)
                .put("temperature", req.temperature == null ? Json.nil() : Json.num(req.temperature))
                .put("max_tokens", req.maxTokens == null ? Json.nil() : Json.num(req.maxTokens));
        Json msgs = Json.arr();
        if (req.messages != null) {
            for (Message m : req.messages) {
                msgs.add(Json.obj()
                        .put("role", m.role == null ? "" : m.role.name())
                        .put("content", m.content == null ? "" : m.content));
            }
        }
        c.put("messages", msgs);
        Json tools = Json.arr();
        if (req.tools != null) {
            for (Tool t : req.tools) tools.add(Json.str(t.name));
        }
        c.put("tools", tools);
        return hash(c.toString());
    }

    /** FNV-1a 64-bit hash rendered as hex. Stable across runs. */
    public static String hash(String s) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        return String.format("%016x", h);
    }
}
