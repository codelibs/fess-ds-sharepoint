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

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.TestInfo;

import org.codelibs.fess.entity.DataStoreParams;
import org.codelibs.fess.helper.CrawlerStatsHelper;
import org.codelibs.fess.helper.SystemHelper;
import org.codelibs.fess.util.ComponentUtil;
import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;

import jakarta.validation.ValidationException;

public class SharePointDataStoreTest extends UnitDsTestCase {
    public SharePointDataStore dataStore;

    @Override
    protected String prepareConfigFile() {
        return "test_app.xml";
    }

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Override
    public void setUp(TestInfo testInfo) throws Exception {
        super.setUp(testInfo);
        dataStore = new SharePointDataStore();
        ComponentUtil.register(new SystemHelper(), "systemHelper");
        final CrawlerStatsHelper crawlerStatsHelper = new CrawlerStatsHelper();
        crawlerStatsHelper.init();
        ComponentUtil.register(crawlerStatsHelper, "crawlerStatsHelper");
    }

    @Override
    public void tearDown(TestInfo testInfo) throws Exception {
        ComponentUtil.setFessConfig(null);
        super.tearDown(testInfo);
    }

    @Test
    public void testCrawl() throws Exception {
        // Intentionally empty. Exercising storeData() end to end against a live SharePoint
        // farm needs a DataConfig, an IndexUpdateCallback and a DataStoreParams, which is more
        // setup than this class currently carries. Kept so the class still reports a test
        // rather than being silently empty; the crawl paths get their coverage alongside the
        // fixes that touch them. See test_storeData_failsLoudlyForAMalformedSiteListId below for
        // a storeData() case that needs no live server at all - it fails before any request is
        // ever made.
    }

    @Test
    public void test_storeData_failsLoudlyForAMalformedSiteListId() {
        final DataStoreParams paramMap = new DataStoreParams();
        paramMap.put("url", "http://localhost/");
        paramMap.put("site.name", "test");
        paramMap.put("site.list_id", "11111111-1111-1111-1111-111111111111' or 1=1--");
        final CapturingIndexUpdateCallback callback = new CapturingIndexUpdateCallback();

        // Before this fix, GetList/GetList2013 were the only place a malformed site.list_id was
        // ever checked, and that check ran inside a crawl attempt: doCrawl's generic
        // catch (Exception e) caught it once (not a retry - only SharePointServerException and
        // SharePointClientException retry there), discarded the stats key, and wrapped it in an
        // abort=false DataStoreCrawlingException, which storeData's own catch block downgraded to
        // a warning before returning normally after committing zero documents - a "successful"
        // crawl that crawled nothing. createCrawler (and so SharePointCrawler's constructor, where
        // the check now also lives) runs outside storeData's try block, so the exception below
        // must propagate out of storeData itself instead of being swallowed there.
        assertThrows(ValidationException.class, () -> dataStore.storeData(null, callback, paramMap, new HashMap<>(), new HashMap<>()),
                "a malformed site.list_id must fail the job, not be downgraded to a warning and a zero-document success");
        assertEquals("no document can reach the index when the crawl never starts", 0, callback.documents.size());
    }

    @Test
    public void test_createCrawler_blankMaxContentLengthFallsBackInsteadOfFailingTheJob() throws Exception {
        final DataStoreParams paramMap = new DataStoreParams();
        paramMap.put("url", "http://localhost/");
        paramMap.put("site.name", "test");
        // Trivially produced by the admin UI: the field left empty rather than removed.
        // Integer.parseInt/Long.parseLong called directly on this throws NumberFormatException,
        // and createCrawler runs outside storeData's try block, so an uncaught one here fails the
        // whole data-config job rather than one crawl target.
        paramMap.put("max_content_length", "");

        try (SharePointCrawler crawler = dataStore.createCrawler(paramMap)) {
            assertNotNull("a blank max_content_length must fall back to the default instead of throwing", crawler);
        }
    }

    @Test
    public void test_createCrawler_malformedProxyPortFallsBackInsteadOfFailingTheJob() throws Exception {
        final DataStoreParams paramMap = new DataStoreParams();
        paramMap.put("url", "http://localhost/");
        paramMap.put("site.name", "test");
        paramMap.put("proxy_host", "proxy.example.com");
        paramMap.put("proxy_port", "not-a-number");

        try (SharePointCrawler crawler = dataStore.createCrawler(paramMap)) {
            assertNotNull("a malformed proxy_port must fall back to the default instead of throwing", crawler);
        }
    }

    /**
     * The suppression in {@code FileCrawl#getContent} fires when either ignore_error or the global
     * crawler.ignore.content.exception setting says to ignore, so a default of true here would make
     * it unconditional and override an installation that had set that global setting to false to
     * get hard failures. At false the condition reduces to the global setting alone, which is what
     * this plugin did before the parameter existed.
     */
    @Test
    public void test_createCrawler_ignoreErrorDefaultsToFalse() throws Exception {
        final DataStoreParams paramMap = new DataStoreParams();
        paramMap.put("url", "http://localhost/");
        paramMap.put("site.name", "test");

        try (SharePointCrawler crawler = dataStore.createCrawler(paramMap)) {
            assertFalse("an unset ignore_error must leave the global setting in charge, not force suppression",
                    crawler.getCrawlerConfig().isIgnoreError());
        }
    }

    @Test
    public void test_createCrawler_ignoreErrorIsHonoredWhenSet() throws Exception {
        final DataStoreParams paramMap = new DataStoreParams();
        paramMap.put("url", "http://localhost/");
        paramMap.put("site.name", "test");
        paramMap.put("ignore_error", "true");

        try (SharePointCrawler crawler = dataStore.createCrawler(paramMap)) {
            assertTrue("ignore_error=true must still suppress extraction failures regardless of the global setting",
                    crawler.getCrawlerConfig().isIgnoreError());
        }
    }

    @Test
    public void test_createCrawler_blankRetryLimitFallsBackInsteadOfFailingTheJob() throws Exception {
        final DataStoreParams paramMap = new DataStoreParams();
        paramMap.put("url", "http://localhost/");
        paramMap.put("site.name", "test");
        // Trivially produced by the admin UI: the field left empty rather than removed.
        // containsKey("retry_limit") is still true for an empty value, so
        // Integer.parseInt(paramMap.getAsString("retry_limit")) threw NumberFormatException here
        // before this fix, and createCrawler runs outside storeData's try block, so an uncaught
        // one failed the whole data-config job rather than one crawl target.
        paramMap.put("retry_limit", "");

        try (SharePointCrawler crawler = dataStore.createCrawler(paramMap)) {
            assertNotNull("a blank retry_limit must fall back to the default instead of throwing", crawler);
        }
    }

    @Test
    public void test_createCrawler_crawlSubsitesDefaultsToFalse() throws Exception {
        final DataStoreParams paramMap = new DataStoreParams();
        paramMap.put("url", "http://localhost/");
        paramMap.put("site.name", "test");

        try (SharePointCrawler crawler = dataStore.createCrawler(paramMap)) {
            assertFalse("an unset site.crawl_subsites must default to false, issuing no webinfos request at all",
                    crawler.getCrawlerConfig().isCrawlSubsites());
        }
    }

    @Test
    public void test_createCrawler_crawlSubsitesReachesConfig() throws Exception {
        final DataStoreParams paramMap = new DataStoreParams();
        paramMap.put("url", "http://localhost/");
        paramMap.put("site.name", "test");
        paramMap.put("site.crawl_subsites", "true");

        try (SharePointCrawler crawler = dataStore.createCrawler(paramMap)) {
            assertTrue("site.crawl_subsites=true must reach the crawler config", crawler.getCrawlerConfig().isCrawlSubsites());
        }
    }

    @Test
    public void test_createCrawler_maxDepthReachesConfig() throws Exception {
        final DataStoreParams paramMap = new DataStoreParams();
        paramMap.put("url", "http://localhost/");
        paramMap.put("site.name", "test");
        paramMap.put("site.max_depth", "3");

        try (SharePointCrawler crawler = dataStore.createCrawler(paramMap)) {
            assertEquals("site.max_depth must reach the crawler config", 3, crawler.getCrawlerConfig().getMaxDepth());
        }
    }

    @Test
    public void test_createCrawler_blankMaxDepthFallsBackInsteadOfFailingTheJob() throws Exception {
        final DataStoreParams paramMap = new DataStoreParams();
        paramMap.put("url", "http://localhost/");
        paramMap.put("site.name", "test");
        // Trivially produced by the admin UI: the field left empty rather than removed.
        // Integer.parseInt called directly on this throws NumberFormatException, and createCrawler
        // runs outside storeData's try block, so an uncaught one here fails the whole data-config
        // job rather than one crawl target. Asserted against the actual default (not just
        // non-null) so a refactor of the guarded parseInt helper that stops falling back would be
        // caught here.
        paramMap.put("site.max_depth", "");

        try (SharePointCrawler crawler = dataStore.createCrawler(paramMap)) {
            assertEquals("a blank site.max_depth must fall back to the default instead of throwing", 10,
                    crawler.getCrawlerConfig().getMaxDepth());
        }
    }

    @Test
    public void test_createCrawler_malformedMaxDepthFallsBackInsteadOfFailingTheJob() throws Exception {
        final DataStoreParams paramMap = new DataStoreParams();
        paramMap.put("url", "http://localhost/");
        paramMap.put("site.name", "test");
        paramMap.put("site.max_depth", "not-a-number");

        try (SharePointCrawler crawler = dataStore.createCrawler(paramMap)) {
            assertEquals("a malformed site.max_depth must fall back to the default instead of throwing", 10,
                    crawler.getCrawlerConfig().getMaxDepth());
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_createCrawler_numberOfThreadsDefaultsToOne() throws Exception {
        final DataStoreParams paramMap = new DataStoreParams();
        paramMap.put("url", "http://localhost/");
        paramMap.put("site.name", "test");

        try (SharePointCrawler crawler = dataStore.createCrawler(paramMap)) {
            assertEquals("an unset number_of_threads must leave the crawl single-threaded", 1,
                    crawler.getCrawlerConfig().getNumberOfThreads());
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_createCrawler_numberOfThreadsReachesConfig() throws Exception {
        final DataStoreParams paramMap = new DataStoreParams();
        paramMap.put("url", "http://localhost/");
        paramMap.put("site.name", "test");
        paramMap.put("number_of_threads", "4");

        try (SharePointCrawler crawler = dataStore.createCrawler(paramMap)) {
            assertEquals("number_of_threads must reach the crawler config", 4, crawler.getCrawlerConfig().getNumberOfThreads());
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_createCrawler_blankNumberOfThreadsFallsBackInsteadOfFailingTheJob() throws Exception {
        final DataStoreParams paramMap = new DataStoreParams();
        paramMap.put("url", "http://localhost/");
        paramMap.put("site.name", "test");
        // The admin UI produces this by leaving the field empty rather than removing it.
        // Integer.parseInt called directly on it throws, and createCrawler runs outside
        // storeData's try block, so that would fail the whole data-config job.
        paramMap.put("number_of_threads", "");

        try (SharePointCrawler crawler = dataStore.createCrawler(paramMap)) {
            assertEquals("a blank number_of_threads must fall back to the default instead of throwing", 1,
                    crawler.getCrawlerConfig().getNumberOfThreads());
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_createCrawler_malformedNumberOfThreadsFallsBackInsteadOfFailingTheJob() throws Exception {
        final DataStoreParams paramMap = new DataStoreParams();
        paramMap.put("url", "http://localhost/");
        paramMap.put("site.name", "test");
        paramMap.put("number_of_threads", "four");

        try (SharePointCrawler crawler = dataStore.createCrawler(paramMap)) {
            assertEquals("a malformed number_of_threads must fall back to the default instead of throwing", 1,
                    crawler.getCrawlerConfig().getNumberOfThreads());
        }
    }
}
