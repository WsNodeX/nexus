package nexus.resilience;

import nexus.core.errors.CircuitOpenException;
import nexus.core.errors.NexusException;

import java.util.function.Supplier;

/**
 * A classic three-state circuit breaker. While healthy (CLOSED) it counts
 * consecutive failures and trips to OPEN after {@code failureThreshold},
 * blocking all calls for {@code cooldownMs}. After the cooldown it moves to
 * HALF_OPEN and, once {@code successThreshold} calls succeed, returns to CLOSED.
 *
 * <p>Thread-safe; the clock is injectable so tests can drive state transitions
 * deterministically without sleeping.</p>
 */
public class CircuitBreaker {
    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final long cooldownMs;
    private final int successThreshold;
    private final java.util.function.Supplier<Long> clock;

    private State state = State.CLOSED;
    private int failures = 0;
    private int successes = 0;
    private long openUntil = 0;

    public CircuitBreaker(int failureThreshold, long cooldownMs, int successThreshold) {
        this(failureThreshold, cooldownMs, successThreshold, System::currentTimeMillis);
    }

    public CircuitBreaker(int failureThreshold, long cooldownMs, int successThreshold,
                          java.util.function.Supplier<Long> clock) {
        this.failureThreshold = failureThreshold;
        this.cooldownMs = cooldownMs;
        this.successThreshold = successThreshold;
        this.clock = clock;
    }

    public synchronized State state() { return state; }

    public synchronized int failures() { return failures; }

    public <T> T run(Supplier<T> action) {
        synchronized (this) {
            if (state == State.OPEN) {
                if (clock.get() >= openUntil) {
                    state = State.HALF_OPEN;
                    successes = 0;
                } else {
                    throw new CircuitOpenException("circuit breaker open");
                }
            }
        }
        try {
            T result = action.get();
            onSuccess();
            return result;
        } catch (Throwable e) {
            onFailure();
            if (e instanceof RuntimeException) throw (RuntimeException) e;
            throw new NexusException(e.getMessage(), e);
        }
    }

    private synchronized void onSuccess() {
        if (state == State.HALF_OPEN) {
            successes++;
            if (successes >= successThreshold) {
                state = State.CLOSED;
                failures = 0;
                successes = 0;
            }
        } else {
            failures = 0;
        }
    }

    private synchronized void onFailure() {
        if (state == State.HALF_OPEN) {
            state = State.OPEN;
            openUntil = clock.get() + cooldownMs;
            successes = 0;
        } else {
            failures++;
            if (failures >= failureThreshold) {
                state = State.OPEN;
                openUntil = clock.get() + cooldownMs;
            }
        }
    }
}
