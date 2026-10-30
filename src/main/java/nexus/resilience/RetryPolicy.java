package nexus.resilience;

import nexus.core.errors.NexusException;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Retries a failing action with exponential backoff and jitter. By default every
 * exception is retried up to {@code maxAttempts} times; the {@code retryOn}
 * predicate can narrow that to transient failures only (e.g. 5xx, timeouts).
 */
public class RetryPolicy {
    private int maxAttempts = 3;
    private long baseDelayMs = 200;
    private long maxDelayMs = 5000;
    private double jitter = 0.2;
    private Predicate<Throwable> retryOn = e -> true;

    public RetryPolicy() {}

    public RetryPolicy maxAttempts(int v) { this.maxAttempts = v; return this; }
    public RetryPolicy baseDelayMs(long v) { this.baseDelayMs = v; return this; }
    public RetryPolicy maxDelayMs(long v) { this.maxDelayMs = v; return this; }
    public RetryPolicy jitter(double v) { this.jitter = v; return this; }
    public RetryPolicy retryOn(Predicate<Throwable> p) { this.retryOn = p; return this; }

    public <T> T run(Supplier<T> action) {
        long attempt = 0;
        while (true) {
            try {
                return action.get();
            } catch (Throwable e) {
                boolean retryable = attempt + 1 < maxAttempts && retryOn.test(e);
                if (!retryable) {
                    if (e instanceof RuntimeException) throw (RuntimeException) e;
                    throw new NexusException(e.getMessage(), e);
                }
                long delay = Backoff.compute(attempt, baseDelayMs, maxDelayMs, jitter);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new NexusException("retry interrupted", ie);
                }
                attempt++;
            }
        }
    }

    public void run(Runnable action) {
        run(() -> { action.run(); return null; });
    }
}
