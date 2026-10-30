package nexus.routing;

import nexus.core.errors.NexusException;
import nexus.llm.ChatRequest;
import nexus.llm.Provider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;

/**
 * Weighted round-robin: providers are visited proportionally to their weight.
 * A fixed schedule is precomputed from the weights (e.g. weights {a:3, b:1}
 * yield [a, a, a, b]) and cycled, which keeps selection deterministic and fair.
 */
public class WeightedRouter implements Router {
    private final List<Provider> schedule;
    private final AtomicInteger cursor = new AtomicInteger(0);

    public WeightedRouter(List<Provider> providers, Map<String, Integer> weights) {
        this.schedule = new ArrayList<>();
        for (Provider p : providers) {
            int w = weights.getOrDefault(p.name(), 1);
            for (int i = 0; i < Math.max(1, w); i++) schedule.add(p);
        }
        if (schedule.isEmpty()) throw new NexusException("weighted router: no providers");
    }

    @Override
    public Provider select(List<Provider> providers, ChatRequest req) {
        int idx = Math.floorMod(cursor.getAndIncrement(), schedule.size());
        return schedule.get(idx);
    }
}
