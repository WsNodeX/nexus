package nexus.test;

import nexus.llm.ChatRequest;
import nexus.llm.ChatResponse;
import nexus.llm.Message;
import nexus.resilience.SemanticCache;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

public class CacheTest {

    @Test
    void storesAndRetrieves() {
        long[] clock = {0L};
        SemanticCache cache = new SemanticCache(1000, (Supplier<Long>) () -> clock[0]);
        ChatRequest req = new ChatRequest("m").add(Message.user("hi"));
        ChatResponse resp = new ChatResponse("hello");
        cache.put(req, resp);
        assertSame(resp, cache.get(req));
    }

    @Test
    void missesOnDifferentModel() {
        long[] clock = {0L};
        SemanticCache cache = new SemanticCache(1000, (Supplier<Long>) () -> clock[0]);
        ChatRequest r1 = new ChatRequest("m1").add(Message.user("hi"));
        cache.put(r1, new ChatResponse("a"));
        ChatRequest r2 = new ChatRequest("m2").add(Message.user("hi"));
        assertNull(cache.get(r2));
    }

    @Test
    void expiresAfterTtl() {
        long[] clock = {0L};
        SemanticCache cache = new SemanticCache(1000, (Supplier<Long>) () -> clock[0]);
        ChatRequest req = new ChatRequest("m").add(Message.user("hi"));
        cache.put(req, new ChatResponse("a"));
        clock[0] += 2000;
        assertNull(cache.get(req));
    }

    @Test
    void ignoresStreamFlagInKey() {
        long[] clock = {0L};
        SemanticCache cache = new SemanticCache(1000, (Supplier<Long>) () -> clock[0]);
        ChatRequest r1 = new ChatRequest("m").add(Message.user("hi"));
        ChatRequest r2 = new ChatRequest("m").add(Message.user("hi"));
        r2.stream = true;
        ChatResponse resp = new ChatResponse("a");
        cache.put(r1, resp);
        assertSame(resp, cache.get(r2));
    }
}
