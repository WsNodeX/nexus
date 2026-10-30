package nexus.test;

import nexus.core.errors.CircuitOpenException;
import nexus.core.errors.NexusException;
import nexus.resilience.CircuitBreaker;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

public class CircuitBreakerTest {

    @Test
    void tripsOpenAfterThresholdAndRecovers() {
        long[] clock = {0L};
        CircuitBreaker cb = new CircuitBreaker(2, 1000, 1, (Supplier<Long>) () -> clock[0]);
        assertThrows(NexusException.class, () -> cb.run(() -> { throw new NexusException("x"); }));
        assertThrows(NexusException.class, () -> cb.run(() -> { throw new NexusException("x"); }));
        assertEquals(CircuitBreaker.State.OPEN, cb.state());

        // While open, calls are rejected without executing the action.
        assertThrows(CircuitOpenException.class, () -> cb.run(() -> "ok"));

        // After the cooldown, it half-opens and recovers on success.
        clock[0] += 2000;
        assertEquals("ok", cb.run(() -> "ok"));
        assertEquals(CircuitBreaker.State.CLOSED, cb.state());
    }

    @Test
    void resetsFailureCountOnSuccess() {
        long[] clock = {0L};
        CircuitBreaker cb = new CircuitBreaker(3, 1000, 1, (Supplier<Long>) () -> clock[0]);
        cb.run(() -> "ok");
        assertThrows(NexusException.class, () -> cb.run(() -> { throw new NexusException("1"); }));
        assertThrows(NexusException.class, () -> cb.run(() -> { throw new NexusException("2"); }));
        assertEquals(CircuitBreaker.State.CLOSED, cb.state());
    }
}
