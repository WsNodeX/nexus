package nexus.core.errors;

/**
 * Raised when the local {@link nexus.resilience.RateLimiter} cannot grant a
 * permit within the configured wait window. Distinguished from a provider-side
 * 429 so callers can react to local backpressure differently.
 */
public class RateLimitException extends NexusException {
    public RateLimitException(String message) {
        super(message);
    }
}
