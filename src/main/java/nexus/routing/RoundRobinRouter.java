package nexus.routing;

import nexus.core.errors.NexusException;
import nexus.llm.ChatRequest;
import nexus.llm.Provider;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Distributes requests evenly across the pool in a round-robin fashion. Safe for
 * concurrent use; a single shared counter advances on every selection.
 */
public class RoundRobinRouter implements Router {
    private final AtomicInteger cursor = new AtomicInteger(0);

    @Override
    public Provider select(List<Provider> providers, ChatRequest req) {
        if (providers.isEmpty()) throw new NexusException("router: empty provider pool");
        int idx = Math.floorMod(cursor.getAndIncrement(), providers.size());
        return providers.get(idx);
    }
}
