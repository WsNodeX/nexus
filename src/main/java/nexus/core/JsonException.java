package nexus.core;

/**
 * Thrown when JSON parsing or serialization fails.
 */
public class JsonException extends RuntimeException {
    public JsonException(String message) {
        super(message);
    }

    public JsonException(String message, Throwable cause) {
        super(message, cause);
    }
}
