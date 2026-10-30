package nexus.routing;

import nexus.llm.ChatRequest;
import nexus.llm.Provider;

import java.util.List;

/**
 * Selects one provider from a pool for a given request. Implementations encode
 * different load-balancing and fail-over strategies while sharing this contract.
 */
public interface Router {
    Provider select(List<Provider> providers, ChatRequest req);
}
