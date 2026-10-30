package nexus.resilience;

/**
 * Exponential backoff with optional full jitter. Pure math, no I/O, so it is
 * trivial to unit-test the delay schedule.
 */
public final class Backoff {

    private Backoff() {}

    /**
     * @param attempt    zero-based attempt index (0 = first retry)
     * @param baseMs     base delay in milliseconds
     * @param maxMs      cap on the delay
     * @param jitter     0 = no jitter, 1 = full jitter (delay scaled to [0, capped])
     */
    public static long compute(long attempt, long baseMs, long maxMs, double jitter) {
        long exp = (long) (baseMs * Math.pow(2, attempt));
        long capped = Math.min(exp, maxMs);
        if (jitter > 0) {
            double factor = 1 - jitter + (Math.random() * 2 * jitter);
            capped = (long) (capped * factor);
        }
        return Math.max(0, capped);
    }
}
