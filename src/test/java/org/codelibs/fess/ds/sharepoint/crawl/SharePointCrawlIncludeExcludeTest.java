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
package org.codelibs.fess.ds.sharepoint.crawl;

import java.util.Map;
import java.util.Queue;

import org.codelibs.fess.crawler.filter.UrlFilter;
import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.codelibs.fess.opensearch.config.exentity.DataConfig;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link SharePointCrawl#isUrlAllowed}'s own logic - delegating to a pre-built
 * {@link UrlFilter}, or allowing everything when there is none - independent of which crawl path
 * calls it and independent of {@link UrlFilter}'s own pattern-validation/matching behavior, which
 * is exercised in fess-crawler's own test suite, not here.
 */
public class SharePointCrawlIncludeExcludeTest extends UnitDsTestCase {

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    private static SharePointCrawl crawlWith() {
        return new SharePointCrawl(null) {
            @Override
            public Map<String, Object> doCrawl(final DataConfig dataConfig, final Queue<SharePointCrawl> crawlingQueue) {
                return null;
            }
        };
    }

    @Test
    public void test_noFilterConfiguredAllowsEverything() {
        assertTrue("a null UrlFilter (neither pattern configured, or the component unavailable) must not filter anything",
                crawlWith().isUrlAllowed(null, "/sites/test/docs/a.txt"));
    }

    @Test
    public void test_aBlankValueIsAlwaysAllowed() {
        final UrlFilter filter = new SimpleUrlFilter(null, ".*");
        assertTrue("a blank URL-ish value has nothing to check against a filter", crawlWith().isUrlAllowed(filter, ""));
    }

    @Test
    public void test_aMatchedFilterIsHonored() {
        final UrlFilter filter = new SimpleUrlFilter(null, ".*\\.txt");
        assertFalse("a configured filter's match() result must be honored", crawlWith().isUrlAllowed(filter, "/sites/test/docs/a.txt"));
    }

    @Test
    public void test_anUnmatchedFilterIsHonored() {
        final UrlFilter filter = new SimpleUrlFilter(null, ".*\\.pdf");
        assertTrue("a value the filter does not exclude must be allowed", crawlWith().isUrlAllowed(filter, "/sites/test/docs/a.txt"));
    }
}
