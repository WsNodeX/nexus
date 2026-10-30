package nexus;

import nexus.core.errors.NexusException;
import nexus.core.errors.RateLimitException;
import nexus.llm.ChatRequest;
import nexus.llm.ChatResponse;
import nexus.llm.Message;
import nexus.llm.Provider;
import nexus.llm.StreamChunk;
import nexus.observability.Meter;
import nexus.resilience.CircuitBreaker;
import nexus.resilience.RateLimiter;
import nexus.resilience.RetryPolicy;
import nexus.resilience.SemanticCache;
import nexus.routing.LatencyRouter;
import nexus.routing.Router;
import nexus.routing.RoundRobinRouter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The gateway facade. A {@code Nexus} bundles a pool of {@link Provider}s with a
 * {@link Router}, optional resilience layers (rate limiter, circuit breaker,
 * retry policy), a response {@link SemanticCache} and a {@link Meter}, exposing a
 * single {@link #chat(ChatRequest)} entry point that applies them in order:
 *
 * <pre>
 * rate-limit -&gt; cache lookup -&gt; [circuit-breaker [retry [router.select -&gt; provider]]] -&gt; cache store -&gt; meter
 * </pre>
 *
 * All layers are optional; omitting them yields a thin, direct client.
 */
public class Nexus {

    private final List<Provider> providers;
    private final Router router;
    private final RateLimiter rateLimiter;
    private final CircuitBreaker breaker;
    private final RetryPolicy retry;
    private final SemanticCache cache;
    private final boolean caching;
    private final long rateLimitWaitMs;
    private final Meter meter;

    private Nexus(Builder b) {
        this.providers = new ArrayList<>(b.providers);
        this.router = b.router != null ? b.router : new RoundRobinRouter();
        this.rateLimiter = b.rateLimiter;
        this.breaker = b.breaker;
        this.retry = b.retry;
        this.cache = b.cache;
        this.caching = b.caching && b.cache != null;
        this.rateLimitWaitMs = b.rateLimitWaitMs;
        this.meter = b.meter;
        if (providers.isEmpty()) throw new NexusException("Nexus requires at least one provider");
    }

    public static Builder builder() { return new Builder(); }

    public ChatResponse chat(ChatRequest req) {
        if (rateLimiter != null) {
            if (rateLimitWaitMs > 0) rateLimiter.acquire(rateLimitWaitMs);
            else rateLimiter.tryAcquire();
        }
        if (caching) {
            ChatResponse hit = cache.get(req);
            if (hit != null) {
                if (meter != null) meter.recordCacheHit(req, hit);
                return hit;
            }
        }
        final Provider[] picked = {null};
        Supplier<ChatResponse> action = () -> {
            picked[0] = router.select(providers, req);
            return picked[0].chat(req);
        };
        long start = System.nanoTime();
        ChatResponse resp;
        if (breaker != null) {
            resp = breaker.run(() -> retry != null ? retry.run(action) : action.get());
        } else {
            resp = retry != null ? retry.run(action) : action.get();
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        if (picked[0] != null) {
            if (meter != null) meter.record(picked[0].name(), req.model, resp, System.nanoTime() - start);
            if (router instanceof LatencyRouter) ((LatencyRouter) router).record(picked[0].name(), elapsedMs);
        }
        if (caching) cache.put(req, resp);
        return resp;
    }

    public Stream<StreamChunk> chatStream(ChatRequest req) {
        if (rateLimiter != null) {
            if (rateLimitWaitMs > 0) rateLimiter.acquire(rateLimitWaitMs);
            else rateLimiter.tryAcquire();
        }
        Provider p = router.select(providers, req);
        return p.chatStream(req);
    }

    /** Convenience one-shot: ask a question and get the text reply. */
    public String ask(String model, String userMessage) {
        ChatResponse r = chat(new ChatRequest(model).add(Message.user(userMessage)));
        return r.content;
    }

    public Meter meter() { return meter; }

    public static final class Builder {
        private final List<Provider> providers = new ArrayList<>();
        private Router router;
        private RateLimiter rateLimiter;
        private CircuitBreaker breaker;
        private RetryPolicy retry;
        private SemanticCache cache;
        private boolean caching;
        private long rateLimitWaitMs;
        private Meter meter;

        public Builder providers(List<Provider> p) { providers.addAll(p); return this; }
        public Builder addProvider(Provider p) { providers.add(p); return this; }
        public Builder router(Router r) { this.router = r; return this; }
        public Builder rateLimiter(RateLimiter rl) { this.rateLimiter = rl; return this; }
        public Builder rateLimitWaitMs(long ms) { this.rateLimitWaitMs = ms; return this; }
        public Builder circuitBreaker(CircuitBreaker cb) { this.breaker = cb; return this; }
        public Builder retryPolicy(RetryPolicy rp) { this.retry = rp; return this; }
        public Builder cache(SemanticCache c) { this.cache = c; this.caching = true; return this; }
        public Builder meter(Meter m) { this.meter = m; return this; }

        public Nexus build() { return new Nexus(this); }
    }
}
