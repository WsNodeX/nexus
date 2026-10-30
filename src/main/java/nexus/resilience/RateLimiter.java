package nexus.resilience;

import nexus.core.errors.RateLimitException;

import java.util.function.Supplier;

/**
 * A token-bucket rate limiter. Tokens accumulate at {@code permitsPerSecond}
 * up to a {@code capacity} burst, and each call consumes one. {@link #tryAcquire()}
 * never blocks; {@link #acquire(long)} waits up to a timeout. The clock is
 * injectable for deterministic tests.
 */
public class RateLimiter {
    private final double capacity;
    private final double refillPerMs;
    private final Supplier<Long> clock;
    private double tokens;
    private long lastRefill;

    private final Object lock = new Object();

    public RateLimiter(double permitsPerSecond, double capacity) {
        this(permitsPerSecond, capacity, System::currentTimeMillis);
    }

    public RateLimiter(double permitsPerSecond, double capacity, Supplier<Long> clock) {
        this.capacity = capacity;
        this.refillPerMs = permitsPerSecond / 1000.0;
        this.clock = clock;
        this.tokens = capacity;
        this.lastRefill = clock.get();
    }

    private void refill() {
        long now = clock.get();
        double elapsed = (now - lastRefill);
        tokens = Math.min(capacity, tokens + elapsed * refillPerMs);
        lastRefill = now;
    }

    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    public boolean tryAcquire(int n) {
        synchronized (lock) {
            refill();
            if (tokens >= n) {
                tokens -= n;
                return true;
            }
            return false;
        }
    }

    /** Acquire one permit, waiting up to {@code timeoutMs} (0 = fail fast). */
    public void acquire(long timeoutMs) throws RateLimitException {
        long deadline = clock.get() + timeoutMs;
        synchronized (lock) {
            while (true) {
                refill();
                if (tokens >= 1) {
                    tokens -= 1;
                    return;
                }
                if (timeoutMs <= 0 || clock.get() >= deadline) {
                    throw new RateLimitException("rate limit exceeded");
                }
                long wait = (long) Math.ceil((1 - tokens) / Math.max(refillPerMs, 1e-9));
                try {
                    Thread.sleep(Math.min(10, Math.max(1, wait)));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RateLimitException("interrupted while waiting for permit");
                }
            }
        }
    }

    public void acquire() throws RateLimitException {
        acquire(0);
    }

    public synchronized double available() {
        refill();
        return tokens;
    }
}
