package nexus.test;

import nexus.core.errors.RateLimitException;
import nexus.resilience.RateLimiter;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

public class RateLimiterTest {

    @Test
    void tokenBucketExhaustsAndRefills() {
        long[] clock = {0L};
        RateLimiter rl = new RateLimiter(100, 2, (Supplier<Long>) () -> clock[0]);
        assertTrue(rl.tryAcquire());
        assertTrue(rl.tryAcquire());
        assertFalse(rl.tryAcquire());
        clock[0] += 200; // ~20 tokens accrue, capped at capacity 2
        assertTrue(rl.tryAcquire());
    }

    @Test
    void acquireFailsFastWhenEmpty() {
        long[] clock = {0L};
        RateLimiter rl = new RateLimiter(1, 1, (Supplier<Long>) () -> clock[0]);
        assertTrue(rl.tryAcquire());
        assertThrows(RateLimitException.class, rl::acquire);
    }

    @Test
    void availableReflectsRefill() {
        long[] clock = {0L};
        RateLimiter rl = new RateLimiter(10, 10, (Supplier<Long>) () -> clock[0]);
        for (int i = 0; i < 10; i++) rl.tryAcquire();
        assertEquals(0.0, rl.available(), 1e-6);
        clock[0] += 1000; // 10 tokens accrue
        assertEquals(10.0, rl.available(), 1e-6);
    }
}
