package nexus.llm;

import nexus.core.errors.NexusException;

import java.util.List;
import java.util.stream.Stream;

/**
 * Abstraction over a single model backend (OpenAI, a self-hosted endpoint, a
 * scripted stub for tests, ...). Implementations translate a {@link
 * ChatRequest} into their own wire format and back.
 */
public interface Provider {

    /** Stable identifier used by routers, meters and caches. */
    String name();

    /** Capability tags used by {@code CapabilityRouter} (e.g. "streaming"). */
    default java.util.Set<String> capabilities() {
        return java.util.Collections.emptySet();
    }

    /** Synchronous completion. */
    ChatResponse chat(ChatRequest req) throws NexusException;

    /** Streaming completion. The returned stream must be consumed fully. */
    default Stream<StreamChunk> chatStream(ChatRequest req) throws NexusException {
        throw new UnsupportedOperationException(name() + " does not support streaming");
    }
}
