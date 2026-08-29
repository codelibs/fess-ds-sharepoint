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

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.codelibs.fess.entity.DataStoreParams;
import org.codelibs.fess.exception.DataStoreCrawlingException;
import org.codelibs.fess.helper.CrawlerStatsHelper;
import org.codelibs.fess.helper.CrawlingInfoHelper;
import org.codelibs.fess.helper.SystemHelper;
import org.codelibs.fess.opensearch.config.exentity.DataConfig;
import org.codelibs.fess.util.ComponentUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;

import jakarta.validation.ValidationException;

/**
 * Verifies that a crawl which gave up on part of its targets tells core to keep the documents it
 * could not refresh.
 *
 * <p>These drive {@link SharePointDataStore#store} rather than {@code storeData}, because the
 * parameter map {@code storeData} receives is a copy and core reads the original one after the
 * crawl returns.
 */
public class SharePointDataStoreFailureTest extends UnitDsTestCase {

    /** The parameter core reads to decide whether to delete the documents this crawl missed. */
    private static final String DELETE_OLD_DOCS = "delete_old_docs";

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

    /** A data config with the little core reads out of it before handing over to the data store. */
    private static DataConfig dataConfig() {
        final DataConfig dataConfig = new DataConfig();
        dataConfig.setBoost(1.0f);
        return dataConfig;
    }

    /**
     * One crawl result. It has to be mutable: {@code storeData} strips the role field out of the
     * result before handing it to the scripts, and an immutable map makes that throw - which the
     * crawl loop then counts as a lost target, so a fixture built with {@code Map.of} would not
     * be a clean crawl at all.
     */
    private static Map<String, Object> cleanResult() {
        final Map<String, Object> result = new HashMap<>();
        result.put("title", "ok");
        return result;
    }

    private static DataStoreParams paramMapWithSession() {
        final DataStoreParams initParamMap = new DataStoreParams();
        initParamMap.put("sessionId", "test-session");
        return initParamMap;
    }

    /** A crawler that yields nothing and reports one crawl target it gave up on. */
    private static class FailingStubCrawler extends StubSharePointCrawler {
        FailingStubCrawler() {
            super(List.of());
        }

        @Override
        public long getFailureCount() {
            return 1L;
        }
    }

    @Test
    public void test_deleteOldDocsIsSuppressedWhenACrawlUnitFailed() {
        final DataStoreParams initParamMap = paramMapWithSession();

        final SharePointDataStore dataStore = new SharePointDataStore() {
            @Override
            protected SharePointCrawler createCrawler(final DataStoreParams paramMap) {
                return new FailingStubCrawler();
            }
        };
        dataStore.store(dataConfig(), new CapturingIndexUpdateCallback(), initParamMap);

        assertEquals("stale documents must not be deleted after a partial crawl", "false", initParamMap.getAsString(DELETE_OLD_DOCS));
    }

    @Test
    public void test_deleteOldDocsIsLeftAloneWhenEverythingSucceeded() {
        final DataStoreParams initParamMap = paramMapWithSession();

        final SharePointDataStore dataStore = new SharePointDataStore() {
            @Override
            protected SharePointCrawler createCrawler(final DataStoreParams paramMap) {
                return new StubSharePointCrawler(List.of(cleanResult()));
            }
        };
        dataStore.store(dataConfig(), new CapturingIndexUpdateCallback(), initParamMap);

        assertNull("a clean crawl must leave the stale-document cleanup enabled", initParamMap.getAsString(DELETE_OLD_DOCS));
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_deleteOldDocsIsSuppressedWhenACrawlTargetThrew() {
        final DataStoreParams initParamMap = paramMapWithSession();

        final SharePointDataStore dataStore = new SharePointDataStore() {
            @Override
            protected SharePointCrawler createCrawler(final DataStoreParams paramMap) {
                // The shape SharePointCrawler#doCrawl produces for an exception its retry loop
                // does not retry: the three-argument constructor leaves abort false, so storeData
                // logs a warning and carries on to the next target. That target is still lost.
                return new StubSharePointCrawler(List.of()).failingOnceWith(
                        new DataStoreCrawlingException("stub", "Failed to crawl stub", new IOException("the farm hung up")));
            }
        };
        dataStore.store(dataConfig(), new CapturingIndexUpdateCallback(), initParamMap);

        assertEquals("a target lost to an exception must not delete documents either", "false", initParamMap.getAsString(DELETE_OLD_DOCS));
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_deleteOldDocsIsSuppressedWhenACrawlTargetThrewSomethingUnexpected() {
        final DataStoreParams initParamMap = paramMapWithSession();

        final SharePointDataStore dataStore = new SharePointDataStore() {
            @Override
            protected SharePointCrawler createCrawler(final DataStoreParams paramMap) {
                // Not a CrawlingAccessException, so it lands in the other handler - which loses
                // the target just the same.
                return new StubSharePointCrawler(List.of()).failingOnceWith(new IllegalStateException("unexpected"));
            }
        };
        dataStore.store(dataConfig(), new CapturingIndexUpdateCallback(), initParamMap);

        assertEquals("an unexpected exception must not delete documents either", "false", initParamMap.getAsString(DELETE_OLD_DOCS));
    }

    @Test
    public void test_theSuppressionSurvivesACrawlerThatThrows() {
        final DataStoreParams initParamMap = paramMapWithSession();

        final SharePointDataStore dataStore = new SharePointDataStore() {
            @Override
            protected SharePointCrawler createCrawler(final DataStoreParams paramMap) {
                return new FailingStubCrawler() {
                    @Override
                    public boolean hasCrawlTarget() {
                        throw new IllegalStateException("crawl blew up after a target had already failed");
                    }
                };
            }
        };
        // The exception escapes to core, which logs it and then runs the deletion anyway, so the
        // parameter has to have been set on the way out.
        assertThrows(IllegalStateException.class, () -> dataStore.store(dataConfig(), new CapturingIndexUpdateCallback(), initParamMap),
                "the exception must reach core rather than being swallowed here");

        assertEquals("a crawl that failed outright must not delete documents either", "false", initParamMap.getAsString(DELETE_OLD_DOCS));
    }

    /**
     * A configuration typo must not delete the index.
     *
     * <p>{@code site.list_id} is validated in the {@link SharePointCrawler} constructor, and
     * {@code storeData} builds the crawler before its own {@code try} block, so the exception
     * reaches core with nothing counted anywhere: no target was ever attempted and the crawler
     * that would have reported its own failures does not exist. Core then deletes every document
     * the previous runs indexed for this data config unless {@code delete_old_docs} says
     * otherwise.
     */
    @Test
    public void test_theSuppressionSurvivesACrawlerThatCouldNotBeBuilt() {
        final DataStoreParams initParamMap = paramMapWithSession();
        initParamMap.put("url", "http://localhost/");
        initParamMap.put("site.name", "test");
        initParamMap.put("site.list_id", "not-a-guid");

        final SharePointDataStore dataStore = new SharePointDataStore();

        assertThrows(ValidationException.class, () -> dataStore.store(dataConfig(), new CapturingIndexUpdateCallback(), initParamMap),
                "a malformed site.list_id must fail the job rather than crawl nothing quietly");

        assertEquals("a crawl that never started must not delete documents either", "false", initParamMap.getAsString(DELETE_OLD_DOCS));
    }

    /**
     * One data store instance serves every data config, and core runs one thread per data config
     * against it, so a failure in one crawl must not suppress the cleanup of another.
     *
     * <p>The failing crawl is held open inside {@code storeData}, after its failure has been
     * recorded and before {@code store} reads it, while the clean crawl runs from start to finish
     * on this thread. A count shared between the two crawls shows up either as a "false" on the
     * clean crawl's parameters or as the failing crawl losing its own count.
     */
    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_aFailureInOneDataConfigDoesNotReachAnother() throws Exception {
        final CountDownLatch failureRecorded = new CountDownLatch(1);
        final CountDownLatch cleanCrawlFinished = new CountDownLatch(1);

        final SharePointDataStore dataStore = new SharePointDataStore() {
            @Override
            protected SharePointCrawler createCrawler(final DataStoreParams paramMap) {
                if (paramMap.containsKey("failing")) {
                    return new FailingStubCrawler() {
                        @Override
                        public void close() throws IOException {
                            // Reached after storeData recorded the failure and before store()
                            // reads it.
                            failureRecorded.countDown();
                            try {
                                // Timed: this runs on a thread JUnit cannot interrupt, so an
                                // untimed await would outlive a failed or timed-out test and park
                                // for the rest of the JVM.
                                cleanCrawlFinished.await(60, TimeUnit.SECONDS);
                            } catch (final InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            super.close();
                        }
                    };
                }
                return new StubSharePointCrawler(List.of(cleanResult()));
            }
        };

        final DataStoreParams failingParams = paramMapWithSession();
        failingParams.put("failing", "true");
        final Thread failingCrawl =
                new Thread(() -> dataStore.store(dataConfig(), new CapturingIndexUpdateCallback(), failingParams), "failing-crawl");
        failingCrawl.start();
        failureRecorded.await();

        final DataStoreParams cleanParams = paramMapWithSession();
        try {
            dataStore.store(dataConfig(), new CapturingIndexUpdateCallback(), cleanParams);
        } finally {
            // Releases the other thread even when the clean crawl above fails, so a broken
            // assertion does not leave a parked non-daemon thread behind.
            cleanCrawlFinished.countDown();
        }
        failingCrawl.join();

        assertNull("the clean crawl must keep its cleanup enabled", cleanParams.getAsString(DELETE_OLD_DOCS));
        assertEquals("the failing crawl must still suppress its own cleanup", "false", failingParams.getAsString(DELETE_OLD_DOCS));
    }
}
