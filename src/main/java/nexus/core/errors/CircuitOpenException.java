package nexus.core.errors;

/**
 * Raised when a {@link nexus.resilience.CircuitBreaker} is open and rejects a
 * call without even attempting it. Callers can fall back to another provider.
 */
public class CircuitOpenException extends NexusException {
    public CircuitOpenException(String message) {
        super(message);
    }
}
