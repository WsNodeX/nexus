package nexus.test;

import nexus.Nexus;
import nexus.llm.ChatRequest;
import nexus.llm.ChatResponse;
import nexus.llm.Message;
import nexus.llm.StreamChunk;
import nexus.observability.Meter;
import nexus.resilience.SemanticCache;
import nexus.routing.RoundRobinRouter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class GatewayIntegrationTest {

    @Test
    void fullGatewayServesAndCaches() {
        Meter meter = new Meter();
        Nexus nexus = Nexus.builder()
                .addProvider(Stubs.named("a", new ChatResponse("A")))
                .addProvider(Stubs.named("b", new ChatResponse("B")))
                .router(new RoundRobinRouter())
                .cache(new SemanticCache(60_000))
                .meter(meter)
                .build();

        ChatRequest req = new ChatRequest("m").add(Message.user("q"));
        ChatResponse r1 = nexus.chat(req);
        ChatResponse r2 = nexus.chat(req); // identical -> cache hit
        assertNotNull(r1.content);
        assertNotNull(r2.content);
        assertEquals(1, meter.stat("(cache)").cacheHits);
    }

    @Test
    void gatewayStreamsThroughSelectedProvider() {
        Nexus nexus = Nexus.builder()
                .addProvider(new nexus.llm.ScriptedProvider("a", r -> new ChatResponse("hello there")))
                .router(new RoundRobinRouter())
                .build();
        List<StreamChunk> chunks = nexus.chatStream(new ChatRequest("m").add(Message.user("hello there")))
                .collect(Collectors.toList());
        assertTrue(chunks.stream().anyMatch(c -> c.deltaContent != null));
    }

    @Test
    void resilienceLayersComposeWithoutNetwork() {
        // A flaky provider that fails twice then succeeds, wrapped in retry. Round-robin
        // keeps returning it; the retry recovers, the breaker never trips.
        nexus.core.errors.NexusException[] last = {null};
        nexus.llm.Provider flaky = new nexus.llm.Provider() {
            int calls = 0;
            @Override public String name() { return "flaky"; }
            @Override public ChatResponse chat(ChatRequest req) {
                if (++calls <= 2) throw new nexus.core.errors.NexusException("fail " + calls);
                return new ChatResponse("recovered");
            }
        };
        Nexus nexus = Nexus.builder()
                .addProvider(flaky)
                .retryPolicy(new nexus.resilience.RetryPolicy().maxAttempts(5).baseDelayMs(1))
                .circuitBreaker(new nexus.resilience.CircuitBreaker(10, 60_000, 1))
                .build();
        assertEquals("recovered", nexus.chat(new ChatRequest("m").add(Message.user("x"))).content);
    }
}
