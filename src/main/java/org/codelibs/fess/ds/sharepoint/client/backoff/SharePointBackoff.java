/*
 * Copyright 2012-2025 CodeLibs Project and the Others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.codelibs.fess.ds.sharepoint.client.backoff;

import java.util.function.DoubleSupplier;
import java.util.function.LongConsumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A wait computed for a throttled or busy on-premises SharePoint server.
 *
 * <p>Shaped after {@code fess-ds-atlassian}'s {@code RetryPolicy} - the only sibling data store
 * with an HTTP backoff - minus its {@code Retry-After} branch: an on-premises SharePoint farm
 * does not send that header, so there is nothing to honour there. What is kept is the same
 * exponential-with-jitter shape: an initial delay that doubles on each successive step, capped so
 * a long run of steps does not park a crawl thread indefinitely, and a jitter factor so a crawl
 * that retries many targets at once does not have every one of them retry in lockstep.
 *
 * <p>The actual sleep is a {@link LongConsumer} the caller supplies, defaulting to a real
 * {@link Thread#sleep(long)}. A test replaces it with one that only records the delay, so
 * verifying the computed backoff never has to actually wait for it.
 */
public class SharePointBackoff {

    private static final Logger logger = LogManager.getLogger(SharePointBackoff.class);

    /** Default initial delay, in milliseconds: the same first step {@code fess-ds-atlassian} uses. */
    public static final long DEFAULT_INITIAL_DELAY_MILLIS = 2000L;

    /** Default cap, in milliseconds, matching {@code fess-ds-atlassian}'s ceiling. */
    public static final long DEFAULT_MAX_DELAY_MILLIS = 30000L;

    private static final long JITTER_FLOOR_PERCENT = 70L;

    private static final long JITTER_SPAN_PERCENT = 60L;

    private final long initialDelayMillis;

    private final long maxDelayMillis;

    private final DoubleSupplier jitterSupplier;

    private final LongConsumer sleeper;

    /**
     * Constructs a backoff.
     *
     * @param initialDelayMillis the delay used for the first step (attempt 0)
     * @param maxDelayMillis the ceiling the computed delay is capped at
     * @param jitterSupplier supplies a value in [0, 1] used to spread the delay across
     *            {@link #JITTER_FLOOR_PERCENT}-{@link #JITTER_FLOOR_PERCENT}+{@link #JITTER_SPAN_PERCENT} percent of it
     * @param sleeper performs the actual wait; a test supplies one that does not really sleep
     */
    public SharePointBackoff(final long initialDelayMillis, final long maxDelayMillis, final DoubleSupplier jitterSupplier,
            final LongConsumer sleeper) {
        this.initialDelayMillis = initialDelayMillis;
        this.maxDelayMillis = maxDelayMillis;
        this.jitterSupplier = jitterSupplier;
        this.sleeper = sleeper;
    }

    /**
     * Returns the backoff used in production: a two-second initial delay doubling up to a
     * thirty-second cap, jittered to 70-130% of the computed value, sleeping for real.
     *
     * @return the default backoff
     */
    public static SharePointBackoff defaults() {
        return new SharePointBackoff(DEFAULT_INITIAL_DELAY_MILLIS, DEFAULT_MAX_DELAY_MILLIS, Math::random,
                SharePointBackoff::sleepUninterruptibly);
    }

    /**
     * Computes the delay for the given step without waiting for it.
     *
     * @param attempt the zero-based step; 0 is the initial delay, each step after doubles it
     * @return the jittered delay in milliseconds
     */
    public long delayMillis(final int attempt) {
        long delay = initialDelayMillis;
        for (int i = 0; i < attempt && delay < maxDelayMillis; i++) {
            delay *= 2L;
        }
        if (delay > maxDelayMillis) {
            delay = maxDelayMillis;
        }
        final long jitterPercent = JITTER_FLOOR_PERCENT + (long) (jitterSupplier.getAsDouble() * JITTER_SPAN_PERCENT);
        return delay * jitterPercent / 100L;
    }

    /**
     * Computes the delay for the given step and waits for it.
     *
     * <p>Every caller today only calls this once it has already decided a wait is warranted -
     * {@link org.codelibs.fess.ds.sharepoint.client.api.SharePointApi#awaitIfServerIsBusy} returns
     * before reaching this method when the health score is not above its busy threshold, and the
     * other callers pass either a retry count (always non-negative) or a literal {@code 0} - so
     * {@code attempt} is always zero or greater in practice.
     *
     * @param attempt the zero-based step
     */
    public void await(final int attempt) {
        final long delay = delayMillis(attempt);
        if (delay > 0L) {
            sleeper.accept(delay);
        }
    }

    private static void sleepUninterruptibly(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.debug("Interrupted while backing off.", e);
        }
    }
}
