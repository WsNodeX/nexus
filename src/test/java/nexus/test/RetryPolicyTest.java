package nexus.test;

import nexus.core.errors.NexusException;
import nexus.resilience.Backoff;
import nexus.resilience.RetryPolicy;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class RetryPolicyTest {

    @Test
    void succeedsAfterTransientFailures() {
        AtomicInteger attempts = new AtomicInteger();
        RetryPolicy rp = new RetryPolicy().maxAttempts(5).baseDelayMs(1).maxDelayMs(5).jitter(0);
        String r = rp.run(() -> {
            int n = attempts.incrementAndGet();
            if (n < 3) throw new NexusException("fail #" + n);
            return "ok";
        });
        assertEquals("ok", r);
        assertEquals(3, attempts.get());
    }

    @Test
    void respectsMaxAttempts() {
        AtomicInteger attempts = new AtomicInteger();
        RetryPolicy rp = new RetryPolicy().maxAttempts(5).baseDelayMs(1).maxDelayMs(5);
        assertThrows(NexusException.class, () -> rp.run(() -> {
            attempts.incrementAndGet();
            throw new NexusException("always");
        }));
        assertEquals(5, attempts.get());
    }

    @Test
    void retryOnPredicateGatesRetries() {
        AtomicInteger attempts = new AtomicInteger();
        RetryPolicy rp = new RetryPolicy().maxAttempts(5).retryOn(e -> false);
        assertThrows(NexusException.class, () -> rp.run(() -> {
            attempts.incrementAndGet();
            throw new NexusException("no-retry");
        }));
        assertEquals(1, attempts.get());
    }

    @Test
    void backoffGrowsExponentiallyWithoutJitter() {
        assertEquals(100, Backoff.compute(0, 100, 1000, 0));
        assertEquals(200, Backoff.compute(1, 100, 1000, 0));
        assertEquals(400, Backoff.compute(2, 100, 1000, 0));
        assertEquals(1000, Backoff.compute(10, 100, 1000, 0));
    }
}
