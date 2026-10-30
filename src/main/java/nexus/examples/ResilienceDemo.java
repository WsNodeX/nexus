package nexus.examples;

import nexus.Nexus;
import nexus.core.errors.NexusException;
import nexus.llm.ChatRequest;
import nexus.llm.ChatResponse;
import nexus.llm.Message;
import nexus.llm.Provider;
import nexus.resilience.CircuitBreaker;
import nexus.resilience.RetryPolicy;

/**
 * Demonstrates the resilience layers failing over and recovering. A flaky backend
 * fails its first three calls then succeeds; a broken backend always fails and
 * trips the circuit breaker so the gateway stops calling it.
 *
 * <p>Run: {@code java nexus.examples.ResilienceDemo}</p>
 */
public class ResilienceDemo {

    /** Fails the first {@code failFirst} calls, then succeeds. */
    static class FlakyProvider implements Provider {
        private final String name;
        private final int failFirst;
        private int calls;

        FlakyProvider(String name, int failFirst) {
            this.name = name;
            this.failFirst = failFirst;
        }

        @Override
        public String name() { return name; }

        @Override
        public ChatResponse chat(ChatRequest req) {
            int n = ++calls;
            if (n <= failFirst) {
                throw new NexusException(name + " transient failure #" + n);
            }
            return new ChatResponse(name + " recovered after " + failFirst + " failures");
        }
    }

    public static void main(String[] args) {
        Provider flaky = new FlakyProvider("flaky", 3);
        Provider broken = new FlakyProvider("broken", Integer.MAX_VALUE);

        Nexus nexus = Nexus.builder()
                .addProvider(flaky)
                .addProvider(broken)
                .router((providers, req) -> providers.get(0)) // always try flaky first
                .retryPolicy(new RetryPolicy().maxAttempts(4).baseDelayMs(20))
                .circuitBreaker(new CircuitBreaker(2, 60_000, 1))
                .build();

        System.out.println("=== Resilience demo ===");
        ChatResponse r = nexus.chat(new ChatRequest("demo").add(Message.user("ping")));
        System.out.println("result: " + r.content);

        // Now hammer the broken backend directly to trip its breaker.
        CircuitBreaker cb = new CircuitBreaker(2, 60_000, 1);
        for (int i = 0; i < 3; i++) {
            try {
                cb.run(() -> { throw new NexusException("boom"); });
            } catch (NexusException ignored) {}
        }
        System.out.println("breaker state after 3 failures: " + cb.state());
        try {
            cb.run(() -> new ChatResponse("should not happen"));
        } catch (NexusException e) {
            System.out.println("blocked as expected: " + e.getMessage());
        }
    }
}
