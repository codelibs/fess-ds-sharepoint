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

import java.util.ArrayList;
import java.util.List;

import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link SharePointBackoff}'s delay math directly, with a fixed jitter and a sleeper
 * that only records what it was asked to wait for - so none of this ever actually sleeps, even
 * though the values it computes (up to a 30-second cap) would make the suite noticeably slower if
 * it did.
 */
public class SharePointBackoffTest extends UnitDsTestCase {

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    /** Jitter fixed at the midpoint, so the factor is exactly 1.0. */
    private static SharePointBackoff fixed(final List<Long> recordedSleeps) {
        return new SharePointBackoff(2000L, 30000L, () -> 0.5d, recordedSleeps::add);
    }

    @Test
    public void test_delayMillis_doublesFromTheInitialDelay() {
        final SharePointBackoff backoff = fixed(new ArrayList<>());
        assertEquals("attempt 0 must be the initial delay", 2000L, backoff.delayMillis(0));
        assertEquals("attempt 1 must double it", 4000L, backoff.delayMillis(1));
        assertEquals("attempt 2 must double again", 8000L, backoff.delayMillis(2));
        assertEquals("attempt 3 must double again", 16000L, backoff.delayMillis(3));
    }

    @Test
    public void test_delayMillis_isCapped() {
        final SharePointBackoff backoff = fixed(new ArrayList<>());
        assertEquals("attempt 4 would double past the cap, so it must be capped", 30000L, backoff.delayMillis(4));
        assertEquals("a much later attempt must still be capped, not overflow", 30000L, backoff.delayMillis(20));
    }

    @Test
    public void test_delayMillis_jitterSpansSeventyToOneHundredThirtyPercent() {
        assertEquals("a jitter supplier of 0.0 must yield 70% of the computed delay", 1400L,
                new SharePointBackoff(2000L, 30000L, () -> 0.0d, ms -> {}).delayMillis(0));
        assertEquals("a jitter supplier of 1.0 must yield 130% of the computed delay", 2600L,
                new SharePointBackoff(2000L, 30000L, () -> 1.0d, ms -> {}).delayMillis(0));
    }

    @Test
    public void test_await_sleepsForTheComputedDelay() {
        final List<Long> recordedSleeps = new ArrayList<>();
        final SharePointBackoff backoff = fixed(recordedSleeps);

        backoff.await(1);

        assertEquals("await must record exactly one sleep", 1, recordedSleeps.size());
        assertEquals("await must sleep for the same value delayMillis computes", 4000L, recordedSleeps.get(0).longValue());
    }

    @Test
    public void test_defaults_matchesTheDocumentedInitialDelayAndJitterRange() {
        // defaults() sleeps for real, so this only checks the computed delay's shape - the
        // documented 2-second initial step, jittered to 70-130% of it - rather than waiting out
        // an actual sleep. Math::random is the production jitter supplier, so the exact value is
        // not reproducible; the 1400-2600 range is what 70-130% of 2000ms bounds to.
        final long delay = SharePointBackoff.defaults().delayMillis(0);
        assertTrue("attempt 0 must fall within 70-130% of the documented 2-second initial delay: was " + delay,
                delay >= 1400L && delay <= 2600L);
    }
}
