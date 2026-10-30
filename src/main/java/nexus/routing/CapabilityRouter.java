package nexus.routing;

import nexus.core.errors.NexusException;
import nexus.llm.ChatRequest;
import nexus.llm.Provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Restricts the pool to providers advertising the required capabilities (e.g.
 * "streaming", "function-calling") and round-robins among the survivors. This
 * is how a gateway routes a streaming request away from a backend that cannot
 * serve it.
 */
public class CapabilityRouter implements Router {
    private final Set<String> required;
    private final AtomicInteger cursor = new AtomicInteger(0);

    public CapabilityRouter(Set<String> requiredCapabilities) {
        this.required = requiredCapabilities;
    }

    @Override
    public Provider select(List<Provider> providers, ChatRequest req) {
        List<Provider> eligible = new ArrayList<>();
        for (Provider p : providers) {
            if (p.capabilities().containsAll(required)) eligible.add(p);
        }
        if (eligible.isEmpty()) {
            throw new NexusException("capability router: no provider supports " + required);
        }
        int idx = Math.floorMod(cursor.getAndIncrement(), eligible.size());
        return eligible.get(idx);
    }
}
