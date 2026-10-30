package nexus.core.errors;

/**
 * Root exception for the Nexus framework. Every recoverable error raised by the
 * gateway extends this type so callers can catch failures uniformly.
 */
public class NexusException extends RuntimeException {
    public NexusException(String message) {
        super(message);
    }

    public NexusException(String message, Throwable cause) {
        super(message, cause);
    }
}
