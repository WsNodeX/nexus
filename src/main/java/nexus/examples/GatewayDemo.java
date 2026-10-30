package nexus.examples;

import nexus.Nexus;
import nexus.llm.ChatRequest;
import nexus.llm.ChatResponse;
import nexus.llm.Message;
import nexus.llm.Provider;
import nexus.llm.ScriptedProvider;
import nexus.observability.Meter;
import nexus.resilience.CircuitBreaker;
import nexus.resilience.RateLimiter;
import nexus.resilience.RetryPolicy;
import nexus.resilience.SemanticCache;
import nexus.routing.RoundRobinRouter;

import java.util.List;
import java.util.function.Function;

/**
 * End-to-end showcase of the gateway: two backends behind a round-robin router,
 * with caching, a circuit breaker, retry and metrics. Runs fully offline using
 * scripted providers, so you can read the wiring without an API key.
 *
 * <p>Run: {@code java nexus.examples.GatewayDemo}</p>
 */
public class GatewayDemo {

    public static void main(String[] args) {
        Function<ChatRequest, ChatResponse> script = req -> {
            String last = lastUser(req);
            ChatResponse r = new ChatResponse("[" + Thread.currentThread().threadId() + "] reply to: " + last);
            r.usage = new nexus.llm.Usage(12, 8, 20);
            return r;
        };

        Provider a = new ScriptedProvider("backend-a", script);
        Provider b = new ScriptedProvider("backend-b", script);

        Meter meter = new Meter();
        Nexus nexus = Nexus.builder()
                .addProvider(a)
                .addProvider(b)
                .router(new RoundRobinRouter())
                .cache(new SemanticCache(30_000))
                .circuitBreaker(new CircuitBreaker(5, 5000, 2))
                .retryPolicy(new RetryPolicy().maxAttempts(3).baseDelayMs(50))
                .meter(meter)
                .build();

        System.out.println("=== Nexus gateway demo ===");
        for (int i = 0; i < 4; i++) {
            ChatResponse r = nexus.chat(new ChatRequest("demo-model").add(Message.user("hello " + i)));
            System.out.println("turn " + i + " -> " + r.content);
        }
        // Identical request should be served from cache on the 2nd hit of same text.
        ChatResponse cached = nexus.chat(new ChatRequest("demo-model").add(Message.user("hello 0")));
        System.out.println("cached turn -> " + cached.content);

        System.out.println();
        System.out.println(meter.summary());
    }

    private static String lastUser(ChatRequest req) {
        for (int i = req.messages.size() - 1; i >= 0; i--) {
            Message m = req.messages.get(i);
            if (m.role == Message.Role.user && m.content != null) return m.content;
        }
        return "";
    }

    // Silence unused-import check for List in some toolchains.
    @SuppressWarnings("unused")
    private static final List<String> NOTE = List.of("offline-demo");
}
