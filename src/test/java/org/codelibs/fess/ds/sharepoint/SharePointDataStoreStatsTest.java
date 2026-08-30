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
package org.codelibs.fess.ds.sharepoint;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.codelibs.core.misc.Pair;
import org.codelibs.fess.Constants;
import org.codelibs.fess.entity.DataStoreParams;
import org.codelibs.fess.helper.CrawlerStatsHelper;
import org.codelibs.fess.helper.CrawlerStatsHelper.StatsKeyObject;
import org.codelibs.fess.helper.CrawlingInfoHelper;
import org.codelibs.fess.helper.SystemHelper;
import org.codelibs.fess.opensearch.config.exentity.DataConfig;
import org.codelibs.fess.util.ComponentUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;

/**
 * Covers three loose ends around how a crawl reports itself: whether it can be stopped mid-flight,
 * whether its stats key carries the document's real url, and whether the parameter map handed to
 * the index callback carries the stats key other data stores in this family also put there.
 */
public class SharePointDataStoreStatsTest extends UnitDsTestCase {

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Override
    public void setUp(final TestInfo testInfo) throws Exception {
        super.setUp(testInfo);
        ComponentUtil.register(new SystemHelper(), "systemHelper");
        ComponentUtil.register(new CrawlingInfoHelper(), "crawlingInfoHelper");
        final CrawlerStatsHelper crawlerStatsHelper = new CrawlerStatsHelper();
        crawlerStatsHelper.init();
        ComponentUtil.register(crawlerStatsHelper, "crawlerStatsHelper");
    }

    /** Exposes {@link StatsKeyObject#getUrl()}, which is protected in core, to this test. */
    private static final class ObservableStatsKey extends StatsKeyObject {
        ObservableStatsKey(final String id) {
            super(id);
        }

        String observedUrl() {
            return getUrl();
        }
    }

    /** A crawler that hands back one result under a {@link StatsKeyObject} the test can inspect afterward. */
    private static final class SingleResultCrawler extends StubSharePointCrawler {
        final ObservableStatsKey statsKey;

        SingleResultCrawler(final Map<String, Object> result, final String id) {
            super(List.of(result));
            this.statsKey = new ObservableStatsKey(id);
        }

        @Override
        public Pair<Map<String, Object>, StatsKeyObject> doCrawl(final DataConfig dataConfig) {
            final Pair<Map<String, Object>, StatsKeyObject> result = super.doCrawl(dataConfig);
            if (result == null) {
                return null;
            }
            return new Pair<>(result.getFirst(), statsKey);
        }
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_stopEndsACrawlThatHasNoNaturalEnd() throws Exception {
        final CountDownLatch atLeastOneDocumentStored = new CountDownLatch(1);
        final CapturingIndexUpdateCallback callback = new CapturingIndexUpdateCallback() {
            @Override
            public void store(final DataStoreParams paramMap, final Map<String, Object> dataMap) {
                super.store(paramMap, dataMap);
                atLeastOneDocumentStored.countDown();
            }
        };

        final Map<String, Object> result = new HashMap<>();
        result.put("title", "ok");

        final SharePointDataStore dataStore = new SharePointDataStore() {
            @Override
            protected SharePointCrawler createCrawler(final DataStoreParams paramMap) {
                return StubSharePointCrawler.infinite(result);
            }
        };

        final DataStoreParams initParamMap = new DataStoreParams();
        initParamMap.put("sessionId", "test-session");
        final Thread crawl = new Thread(() -> dataStore.store(new DataConfig(), callback, initParamMap), "infinite-crawl");
        // A crawl with no natural end must not be able to block the JVM from exiting if this test
        // fails and stop() turns out not to end it.
        crawl.setDaemon(true);
        crawl.start();

        assertTrue("the crawl must reach the callback at least once before it is stopped",
                atLeastOneDocumentStored.await(30, TimeUnit.SECONDS));
        dataStore.stop();
        crawl.join(30_000);

        assertFalse("stop() must end a crawl that has no natural end", crawl.isAlive());
    }

    @Test
    public void test_theStatsKeyCarriesTheDocumentsRealUrlOnceItIsBuilt() {
        final Map<String, Object> crawlResult = new HashMap<>();
        crawlResult.put("title", "a page");
        crawlResult.put("url", "https://example.sharepoint.com/sites/test/page.aspx");

        final SingleResultCrawler crawler = new SingleResultCrawler(crawlResult, "site#test");
        final SharePointDataStore dataStore = new SharePointDataStore() {
            @Override
            protected SharePointCrawler createCrawler(final DataStoreParams paramMap) {
                return crawler;
            }
        };

        final Map<String, String> scriptMap = new HashMap<>();
        scriptMap.put("url", "url");

        final CapturingIndexUpdateCallback callback = new CapturingIndexUpdateCallback();
        dataStore.storeData(null, callback, new DataStoreParams(), scriptMap, new HashMap<>());

        assertEquals("exactly one document must reach the index", 1, callback.documents.size());
        assertEquals("the stats key must carry the document's real url, not its internal id",
                "https://example.sharepoint.com/sites/test/page.aspx", crawler.statsKey.observedUrl());
    }

    @Test
    public void test_theCrawlerStatsKeyReachesTheParamMapHandedToTheCallback() {
        final Map<String, Object> crawlResult = new HashMap<>();
        crawlResult.put("title", "a page");

        final SharePointDataStore dataStore = new SharePointDataStore() {
            @Override
            protected SharePointCrawler createCrawler(final DataStoreParams paramMap) {
                return new StubSharePointCrawler(List.of(crawlResult));
            }
        };

        final CapturingIndexUpdateCallback callback = new CapturingIndexUpdateCallback();
        dataStore.storeData(null, callback, new DataStoreParams(), new HashMap<>(), new HashMap<>());

        assertEquals("exactly one document must reach the index", 1, callback.documents.size());
        assertEquals("exactly one parameter map must reach the index", 1, callback.paramMaps.size());
        // This only asserts presence: nothing in the current callback wiring reads this key, so no
        // stronger claim about behavior would be honest.
        assertTrue("the crawler stats key must be present in the parameter map handed to callback.store",
                callback.paramMaps.get(0).containsKey(Constants.CRAWLER_STATS_KEY));
    }
}
