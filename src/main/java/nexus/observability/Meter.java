package nexus.observability;

import nexus.llm.ChatRequest;
import nexus.llm.ChatResponse;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight, lock-free metrics recorder. Tracks per-provider call counts,
 * token usage, latency and cache hits, and derives a rough cost estimate from a
 * small built-in price table. Useful for dashboards, tests and cost guardrails.
 */
public class Meter {
    public static final class Stat {
        public int calls;
        public int cacheHits;
        public long promptTokens;
        public long completionTokens;
        public long totalLatencyMs;

        public double avgLatencyMs() {
            return calls == 0 ? 0 : (double) totalLatencyMs / calls;
        }
    }

    private final Map<String, Stat> stats = new ConcurrentHashMap<>();
    private final Map<String, Double[]> pricing = new ConcurrentHashMap<>();

    public Meter() {
        // [prompt $ per 1M tokens, completion $ per 1M tokens]
        pricing.put("gpt-4o", new Double[]{5.0, 15.0});
        pricing.put("gpt-4o-mini", new Double[]{0.15, 0.60});
        pricing.put("gpt-3.5-turbo", new Double[]{0.50, 1.50});
        pricing.put("text-embedding-3-small", new Double[]{0.02, 0.02});
        pricing.put("default", new Double[]{1.0, 2.0});
    }

    public void record(String provider, String model, ChatResponse resp, long latencyNanos) {
        Stat s = stats.computeIfAbsent(provider, k -> new Stat());
        s.calls++;
        s.promptTokens += resp.usage.promptTokens;
        s.completionTokens += resp.usage.completionTokens;
        s.totalLatencyMs += Math.max(0, latencyNanos / 1_000_000);
    }

    public void recordCacheHit(ChatRequest req, ChatResponse resp) {
        Stat s = stats.computeIfAbsent("(cache)", k -> new Stat());
        s.calls++;
        s.cacheHits++;
        s.promptTokens += resp.usage.promptTokens;
        s.completionTokens += resp.usage.completionTokens;
    }

    public Stat stat(String provider) {
        return stats.get(provider);
    }

    public Map<String, Stat> all() {
        return new LinkedHashMap<>(stats);
    }

    public double estimatedCostUsd(String provider) {
        Stat s = stats.get(provider);
        if (s == null) return 0;
        Double[] p = priceFor(provider);
        return (s.promptTokens / 1_000_000.0) * p[0] + (s.completionTokens / 1_000_000.0) * p[1];
    }

    private Double[] priceFor(String model) {
        for (String key : pricing.keySet()) {
            if (model != null && model.contains(key)) return pricing.get(key);
        }
        return pricing.get("default");
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Meter summary\n");
        for (Map.Entry<String, Stat> e : all().entrySet()) {
            Stat s = e.getValue();
            sb.append(String.format("  %-12s calls=%d cacheHits=%d prompt=%d completion=%d avgLatency=%.1fms cost=$%.4f%n",
                    e.getKey(), s.calls, s.cacheHits, s.promptTokens, s.completionTokens, s.avgLatencyMs(), estimatedCostUsd(e.getKey())));
        }
        return sb.toString();
    }
}
