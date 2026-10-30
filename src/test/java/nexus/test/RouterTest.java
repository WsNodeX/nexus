package nexus.test;

import nexus.core.errors.NexusException;
import nexus.llm.ChatRequest;
import nexus.llm.Provider;
import nexus.routing.CapabilityRouter;
import nexus.routing.LatencyRouter;
import nexus.routing.RoundRobinRouter;
import nexus.routing.Router;
import nexus.routing.WeightedRouter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class RouterTest {

    private Provider p(String name) { return Stubs.named(name, null); }

    @Test
    void roundRobinCyclesThroughPool() {
        Router r = new RoundRobinRouter();
        List<Provider> pool = List.of(p("a"), p("b"), p("c"));
        ChatRequest req = new ChatRequest("m");
        StringBuilder order = new StringBuilder();
        for (int i = 0; i < 6; i++) order.append(r.select(pool, req).name());
        assertEquals("abcabc", order.toString());
    }

    @Test
    void weightedRespectsProportions() {
        List<Provider> pool = List.of(p("a"), p("b"));
        Router r = new WeightedRouter(pool, Map.of("a", 3, "b", 1));
        ChatRequest req = new ChatRequest("m");
        StringBuilder order = new StringBuilder();
        for (int i = 0; i < 4; i++) order.append(r.select(pool, req).name());
        assertEquals("aaab", order.toString());
    }

    @Test
    void latencyPicksFastest() {
        LatencyRouter r = new LatencyRouter(1.0); // alpha=1 -> pure last-value, deterministic
        Provider a = p("a");
        Provider b = p("b");
        r.record("a", 100);
        r.record("b", 10);
        assertEquals("b", r.select(List.of(a, b), new ChatRequest("m")).name());
        r.record("a", 5);
        assertEquals("a", r.select(List.of(a, b), new ChatRequest("m")).name());
    }

    @Test
    void capabilityFiltersPool() {
        Provider streaming = Stubs.namedCaps("s", Set.of("streaming"), null);
        Provider plain = Stubs.namedCaps("p", Set.of(), null);
        Router r = new CapabilityRouter(Set.of("streaming"));
        ChatRequest req = new ChatRequest("m");
        assertEquals("s", r.select(List.of(streaming, plain), req).name());
        assertEquals("s", r.select(List.of(plain, streaming), req).name());
    }

    @Test
    void capabilityThrowsWhenNoneMatch() {
        Provider plain = Stubs.namedCaps("p", Set.of(), null);
        Router r = new CapabilityRouter(Set.of("vision"));
        assertThrows(NexusException.class, () -> r.select(List.of(plain), new ChatRequest("m")));
    }
}
