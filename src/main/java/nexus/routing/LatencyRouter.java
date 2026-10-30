package nexus.routing;

import nexus.core.errors.NexusException;
import nexus.llm.ChatRequest;
import nexus.llm.Provider;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends each request to the provider with the lowest rolling average latency,
 * steering traffic toward the backend that is currently responding fastest.
 * Latency is reported back through {@link #record(String, long)} (the gateway
 * does this automatically after every call).
 */
public class LatencyRouter implements Router {
    private final Map<String, Double> avg = new ConcurrentHashMap<>();
    private final double alpha;

    public LatencyRouter() { this(0.3); }

    public LatencyRouter(double smoothing) {
        this.alpha = smoothing;
    }

    public void record(String providerName, long millis) {
        avg.compute(providerName, (k, v) -> v == null ? (double) millis : v + alpha * (millis - v));
    }

    @Override
    public Provider select(List<Provider> providers, ChatRequest req) {
        if (providers.isEmpty()) throw new NexusException("latency router: empty provider pool");
        Provider best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (Provider p : providers) {
            Double score = avg.get(p.name());
            if (score == null) {
                // Untested backends are preferred first so they get sampled.
                return p;
            }
            if (score < bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best == null ? providers.get(0) : best;
    }
}
