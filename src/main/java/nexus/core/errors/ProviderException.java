package nexus.core.errors;

/**
 * Raised when an upstream model provider returns an error response or cannot be
 * reached. Carries the HTTP status code when available so resilience policies
 * can decide whether retrying makes sense.
 */
public class ProviderException extends NexusException {
    private final int statusCode;

    public ProviderException(String message) {
        this(message, -1, null);
    }

    public ProviderException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public ProviderException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
