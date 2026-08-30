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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.codelibs.core.misc.Pair;
import org.codelibs.fess.app.service.FailureUrlService;
import org.codelibs.fess.crawler.container.CrawlerContainer;
import org.codelibs.fess.crawler.entity.ExtractData;
import org.codelibs.fess.crawler.extractor.Extractor;
import org.codelibs.fess.crawler.extractor.ExtractorFactory;
import org.codelibs.fess.crawler.helper.MimeTypeHelper;
import org.codelibs.fess.crawler.helper.impl.MimeTypeHelperImpl;
import org.codelibs.fess.ds.sharepoint.SharePointCrawler.CrawlerConfig;
import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.client.backoff.SharePointBackoff;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.crawl.SharePointCrawl;
import org.codelibs.fess.ds.sharepoint.crawl.file.FileCrawl;
import org.codelibs.fess.ds.sharepoint.util.SharePointMockServer;
import org.codelibs.fess.exception.DataStoreCrawlingException;
import org.codelibs.fess.helper.CrawlerStatsHelper;
import org.codelibs.fess.helper.CrawlerStatsHelper.StatsKeyObject;
import org.codelibs.fess.helper.CrawlingInfoHelper;
import org.codelibs.fess.helper.FileTypeHelper;
import org.codelibs.fess.helper.SystemHelper;
import org.codelibs.fess.opensearch.config.exentity.CrawlingConfig;
import org.codelibs.fess.opensearch.config.exentity.DataConfig;
import org.codelibs.fess.opensearch.config.exentity.FailureUrl;
import org.codelibs.fess.util.ComponentUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;

import jakarta.validation.ValidationException;

public class SharePointCrawlerTest extends UnitDsTestCase {

    @Override
    protected String prepareConfigFile() {
        return "test_app.xml";
    }

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Override
    public void setUp(final TestInfo testInfo) throws Exception {
        super.setUp(testInfo);
        // doCrawl() reaches CrawlerStatsHelper unconditionally, and FailureUrlService on any path
        // that gives up on a target - registered the same way SharePointDataStoreFailureTest does,
        // so a test driving doCrawl() directly does not need OpenSearch either.
        ComponentUtil.register(new SystemHelper(), "systemHelper");
        ComponentUtil.register(new CrawlingInfoHelper(), "crawlingInfoHelper");
        final CrawlerStatsHelper crawlerStatsHelper = new CrawlerStatsHelper();
        crawlerStatsHelper.init();
        ComponentUtil.register(crawlerStatsHelper, "crawlerStatsHelper");
        ComponentUtil.register(new FailureUrlService() {
            @Override
            public FailureUrl store(final CrawlingConfig crawlingConfig, final String errorName, final String url, final Throwable e) {
                return null;
            }
        }, FailureUrlService.class.getCanonicalName());
    }

    private static CrawlerConfig baseConfig() {
        final CrawlerConfig config = new CrawlerConfig();
        config.setUrl("http://localhost/");
        config.setSiteName("test");
        return config;
    }

    @Test
    public void test_construction_rejectsAMalformedSiteListId() {
        final CrawlerConfig config = baseConfig();
        config.setInitialListId("11111111-1111-1111-1111-111111111111' or 1=1--");

        // This has to throw from the constructor itself - before any crawl target exists and
        // before SharePointCrawler#doCrawl's generic exception handling is even reachable - so
        // the failure escapes uncaught to SharePointDataStore#createCrawler's caller instead of
        // being caught once there, downgraded to a warning, and reported as a zero-document
        // "success". See SharePointDataStoreTest for the same guarantee verified through the
        // full storeData path.
        assertThrows(ValidationException.class, () -> new SharePointCrawler(config),
                "a malformed site.list_id must fail crawler construction, not just a later request");
    }

    @Test
    public void test_construction_acceptsAWellFormedSiteListId() {
        final CrawlerConfig config = baseConfig();
        config.setInitialListId("11111111-1111-1111-1111-111111111111");

        assertDoesNotThrow(() -> new SharePointCrawler(config), "a well-formed GUID list id must not be rejected");
    }

    @Test
    public void test_initialDocLibPathUsesTheDefaultManagedPath() {
        final CrawlerConfig config = new CrawlerConfig();
        config.setSiteName("mysite");
        config.setInitialDocLibPath("/Shared Documents");

        assertEquals("unchanged when site.path is unset", "/sites/mysite/Shared Documents", config.getInitialDocLibPath());
    }

    @Test
    public void test_initialDocLibPathFollowsTheConfiguredSitePath() {
        final CrawlerConfig config = new CrawlerConfig();
        config.setSitePath("/teams/eng");
        config.setInitialDocLibPath("/Shared Documents");

        assertEquals("site.path is honoured here too", "/teams/eng/Shared Documents", config.getInitialDocLibPath());
    }

    @Test
    public void test_sitePathAloneSatisfiesValidation() {
        final CrawlerConfig config = new CrawlerConfig();
        config.setUrl("https://example.com/");
        config.setSitePath("/teams/eng");

        // site.name is not set; constructing must not throw a ValidationException for it.
        assertDoesNotThrow(() -> new SharePointCrawler(config), "site.path alone must satisfy the site.name/site.path validation");
    }

    @Test
    public void test_setSupportedMimeTypes_blankValueFallsBackToTheDefault() {
        final CrawlerConfig config = new CrawlerConfig();

        config.setSupportedMimeTypes("");

        // A blank value split on "," used to become {""}, a pattern that matches no MIME type at
        // all - supported_mimetypes="" silently excluded every file rather than leaving the
        // parameter unset.
        assertEquals("a blank supported_mimetypes must fall back to the default pattern, not {\"\"}",
                Arrays.toString(FileCrawl.DEFAULT_SUPPORTED_MIMETYPES), Arrays.toString(config.getSupportedMimeTypes()));
    }

    @Test
    public void test_setSupportedMimeTypes_blankAfterTrimFallsBackToTheDefault() {
        final CrawlerConfig config = new CrawlerConfig();

        config.setSupportedMimeTypes("   ");

        assertEquals("whitespace-only supported_mimetypes must also fall back to the default",
                Arrays.toString(FileCrawl.DEFAULT_SUPPORTED_MIMETYPES), Arrays.toString(config.getSupportedMimeTypes()));
    }

    /** Where a doclib-path crawl targeting an empty folder makes its one request. */
    private static final String EMPTY_FOLDER_API = "/sites/test/_api/web/GetFolderByServerRelativePath(decodedUrl='/sites/test/docs')";

    @Test
    @Timeout(value = 30, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_doCrawl_backsOffOnA503BeforeRetrying() throws Exception {
        // Against the unfixed code this test still passes the retry itself - doCrawl already
        // retries a SharePointServerException - but recordedSleeps stays empty, because nothing
        // ever calls SharePointBackoff before the second attempt.
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathOnce(EMPTY_FOLDER_API, 503, "application/json", "{}");
            // ItemCount 0 means FolderCrawl#doCrawl returns without listing subfolders or files,
            // so this one request is the entire crawl - nothing else needs to be stubbed.
            server.onPathStatus(EMPTY_FOLDER_API, 200, "application/json", "{\"ItemCount\":0}");
            server.start();

            final CrawlerConfig config = new CrawlerConfig();
            config.setUrl(server.getBaseUrl());
            config.setSiteName("test");
            config.setInitialDocLibPath("/docs");
            config.setRetryLimit(1);

            final SharePointCrawler crawler = new SharePointCrawler(config);
            final List<Long> recordedSleeps = new ArrayList<>();
            crawler.setBackoff(new SharePointBackoff(2000L, 30000L, () -> 0.5d, recordedSleeps::add));
            try {
                assertNull("the retried target produces no document itself", crawler.doCrawl(new DataConfig()));

                assertEquals("a 503 must back off exactly once, before the single retry this target needed", 1, recordedSleeps.size());
                assertEquals("the backoff for the first retry (attempt 0) must be the initial delay, unjittered at the midpoint", 2000L,
                        recordedSleeps.get(0).longValue());
                assertEquals("the retry must have succeeded, so nothing was given up on", 0L, crawler.getFailureCount());
            } finally {
                crawler.close();
            }
        }
    }

    /**
     * Every other 503 test here stubs a JSON body, which is what hid this: an error body is not
     * necessarily JSON even when the request asked for JSON, and a 503 whose body is an HTML page -
     * IIS's own default, or a load balancer's - used to be reported as a SharePointClientException
     * carrying no status code at all, so this retry loop's {@code == 503} check was false and
     * recordedSleeps stayed empty.
     */
    @Test
    @Timeout(value = 30, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_doCrawl_backsOffOnA503WhoseBodyIsNotJson() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathOnce(EMPTY_FOLDER_API, 503, "text/html",
                    "<html><head><title>503 Service Unavailable</title></head><body><h1>Service Unavailable</h1></body></html>");
            server.onPathStatus(EMPTY_FOLDER_API, 200, "application/json", "{\"ItemCount\":0}");
            server.start();

            final CrawlerConfig config = new CrawlerConfig();
            config.setUrl(server.getBaseUrl());
            config.setSiteName("test");
            config.setInitialDocLibPath("/docs");
            config.setRetryLimit(1);

            final SharePointCrawler crawler = new SharePointCrawler(config);
            final List<Long> recordedSleeps = new ArrayList<>();
            crawler.setBackoff(new SharePointBackoff(2000L, 30000L, () -> 0.5d, recordedSleeps::add));
            try {
                assertNull("the retried target produces no document itself", crawler.doCrawl(new DataConfig()));

                assertEquals("a 503 must back off even when its body is not JSON", 1, recordedSleeps.size());
                assertEquals("the backoff for the first retry (attempt 0) must be the initial delay", 2000L,
                        recordedSleeps.get(0).longValue());
                assertEquals("the retry must have succeeded, so nothing was given up on", 0L, crawler.getFailureCount());
            } finally {
                crawler.close();
            }
        }
    }

    @Test
    @Timeout(value = 30, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_doCrawl_doesNotBackOffOnANon503Error() throws Exception {
        // A 404 (or any non-503) is retried the same as a 503 always was, but is not evidence the
        // server is overloaded, so it must not pay the backoff wait.
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathOnce(EMPTY_FOLDER_API, 404, "application/json", "{}");
            server.onPathStatus(EMPTY_FOLDER_API, 200, "application/json", "{\"ItemCount\":0}");
            server.start();

            final CrawlerConfig config = new CrawlerConfig();
            config.setUrl(server.getBaseUrl());
            config.setSiteName("test");
            config.setInitialDocLibPath("/docs");
            config.setRetryLimit(1);

            final SharePointCrawler crawler = new SharePointCrawler(config);
            final List<Long> recordedSleeps = new ArrayList<>();
            crawler.setBackoff(new SharePointBackoff(2000L, 30000L, () -> 0.5d, recordedSleeps::add));
            try {
                crawler.doCrawl(new DataConfig());

                assertTrue("a non-503 error must not trigger the throttling backoff", recordedSleeps.isEmpty());
            } finally {
                crawler.close();
            }
        }
    }

    /**
     * A crawl unit that fails a fixed number of times with a given exception, then succeeds by
     * returning a non-null result - so {@link SharePointCrawler#doCrawl} returns instead of
     * looping to a second, unrelated queue entry.
     */
    private static SharePointCrawl failingThenSucceeding(final int failureCount, final RuntimeException failure) {
        return new SharePointCrawl(null) {
            private int remaining = failureCount;

            {
                // The protected field inherited from SharePointCrawl - set here rather than left
                // null, since CrawlerStatsHelper.begin(statsKey) is called on it before doCrawl
                // ever runs.
                statsKey = new StatsKeyObject("stub-503");
            }

            @Override
            public Map<String, Object> doCrawl(final DataConfig dataConfig, final Queue<SharePointCrawl> crawlingQueue) {
                if (remaining > 0) {
                    remaining--;
                    throw failure;
                }
                return new HashMap<>(Map.of("title", "ok"));
            }
        };
    }

    /**
     * GetFile carries no status code the shared path's SharePointServerException does, but
     * SharePointClientException(String, int) - added so GetFile's 503 could still be recognized -
     * lets this same retry-loop branch back off for it too. Against the unfixed code (before that
     * status code was added and read here) this test's recordedSleeps stays empty.
     */
    @Test
    @Timeout(value = 30, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_doCrawl_backsOffOnA503CarriedByASharePointClientException() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(EMPTY_FOLDER_API, 200, "application/json", "{\"ItemCount\":0}");
            server.start();

            final CrawlerConfig config = new CrawlerConfig();
            config.setUrl(server.getBaseUrl());
            config.setSiteName("test");
            config.setInitialDocLibPath("/docs");
            config.setRetryLimit(1);

            final SharePointCrawler crawler = new SharePointCrawler(config);
            final List<Long> recordedSleeps = new ArrayList<>();
            crawler.setBackoff(new SharePointBackoff(2000L, 30000L, () -> 0.5d, recordedSleeps::add));
            crawler.offerCrawlTargetForTest(
                    failingThenSucceeding(1, new SharePointClientException("GetFile Request failure. status:503 body:", 503)));
            try {
                // Two targets in the queue: the empty-folder one built into config, then the
                // stub above. The first returns null and is not what this test is about; loop
                // until the stub's target is reached and either gives a result or the queue
                // empties.
                while (crawler.hasCrawlTarget() && crawler.doCrawl(new DataConfig()) == null) {
                    // draining the empty-folder target
                }

                assertEquals("a 503 carried by a SharePointClientException must back off exactly once", 1, recordedSleeps.size());
                assertEquals("attempt 0 (the first retry) must be the initial delay", 2000L, recordedSleeps.get(0).longValue());
            } finally {
                crawler.close();
            }
        }
    }

    /**
     * The final attempt of an abandoned target must not pay a backoff wait it will never benefit
     * from - a 503 that survives every retry is given up on, not delayed pointlessly first.
     */
    @Test
    @Timeout(value = 30, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_doCrawl_doesNotBackOffOnTheFinalGiveUpAttempt() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(EMPTY_FOLDER_API, 200, "application/json", "{\"ItemCount\":0}");
            server.start();

            final CrawlerConfig config = new CrawlerConfig();
            config.setUrl(server.getBaseUrl());
            config.setSiteName("test");
            config.setInitialDocLibPath("/docs");
            config.setRetryLimit(0);

            final SharePointCrawler crawler = new SharePointCrawler(config);
            final List<Long> recordedSleeps = new ArrayList<>();
            crawler.setBackoff(new SharePointBackoff(2000L, 30000L, () -> 0.5d, recordedSleeps::add));
            crawler.offerCrawlTargetForTest(failingThenSucceeding(Integer.MAX_VALUE,
                    new SharePointClientException("GetFile Request failure. status:503 body:", 503)));
            try {
                while (crawler.hasCrawlTarget() && crawler.doCrawl(new DataConfig()) == null) {
                    // draining the empty-folder target, then the always-failing one gives up
                }

                assertTrue("a target given up on immediately (retry_limit 0) must not pay a wasted backoff wait", recordedSleeps.isEmpty());
                assertEquals("the target must actually have been given up on, not silently skipped", 1, crawler.getFailureCount());
            } finally {
                crawler.close();
            }
        }
    }

    /** A crawl unit that records that it ran, runs {@code onCrawl}, and produces no document. */
    private static SharePointCrawl recordingCrawl(final String id, final Runnable onCrawl) {
        return new SharePointCrawl(null) {
            {
                statsKey = new StatsKeyObject(id);
            }

            @Override
            public Map<String, Object> doCrawl(final DataConfig dataConfig, final Queue<SharePointCrawl> crawlingQueue) {
                onCrawl.run();
                return null;
            }
        };
    }

    /**
     * Against the unfixed code the queue loop had no stop check at all, so it drained every
     * remaining target before returning - the crawl unit below would have run.
     */
    @Test
    @Timeout(value = 30, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_doCrawl_stopsPollingTheQueueWhenAStopIsRequested() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(EMPTY_FOLDER_API, 200, "application/json", "{\"ItemCount\":0}");
            server.start();

            final CrawlerConfig config = new CrawlerConfig();
            config.setUrl(server.getBaseUrl());
            config.setSiteName("test");
            config.setInitialDocLibPath("/docs");

            final SharePointCrawler crawler = new SharePointCrawler(config);
            final AtomicBoolean stopped = new AtomicBoolean();
            final AtomicInteger secondTargetRuns = new AtomicInteger();
            crawler.setStopRequested(stopped::get);
            crawler.offerCrawlTargetForTest(recordingCrawl("stub-stopper", () -> stopped.set(true)));
            crawler.offerCrawlTargetForTest(recordingCrawl("stub-after-stop", secondTargetRuns::incrementAndGet));
            try {
                assertNull("no target here produces a document", crawler.doCrawl(new DataConfig()));

                assertEquals("a target queued behind the stop must not be crawled", 0, secondTargetRuns.get());
                assertTrue("it must still be queued rather than silently discarded", crawler.hasCrawlTarget());
            } finally {
                crawler.close();
            }
        }
    }

    /**
     * Against the unfixed code the 503 branch called the backoff unconditionally, so a stop
     * requested during the crawl still waited out the full delay - up to the backoff's 30-second
     * cap - before the retry it was about to abandon anyway.
     */
    @Test
    @Timeout(value = 30, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_doCrawl_doesNotWaitOutTheBackoffWhenAStopIsRequested() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(EMPTY_FOLDER_API, 200, "application/json", "{\"ItemCount\":0}");
            server.start();

            final CrawlerConfig config = new CrawlerConfig();
            config.setUrl(server.getBaseUrl());
            config.setSiteName("test");
            config.setInitialDocLibPath("/docs");
            config.setRetryLimit(3);

            final SharePointCrawler crawler = new SharePointCrawler(config);
            final List<Long> recordedSleeps = new ArrayList<>();
            crawler.setBackoff(new SharePointBackoff(2000L, 30000L, () -> 0.5d, recordedSleeps::add));
            final AtomicBoolean stopped = new AtomicBoolean();
            final AtomicInteger attempts = new AtomicInteger();
            crawler.setStopRequested(stopped::get);
            crawler.offerCrawlTargetForTest(recordingCrawl("stub-503-then-stop", () -> {
                attempts.incrementAndGet();
                stopped.set(true);
                throw new SharePointClientException("GetFile Request failure. status:503 body:", 503);
            }));
            try {
                // The empty-folder target built into config comes first and produces no document.
                crawler.doCrawl(new DataConfig());

                assertEquals("the stopping target must have been attempted exactly once", 1, attempts.get());
                assertTrue("a stop must not sit out the 503 backoff before a retry it will not make", recordedSleeps.isEmpty());
            } finally {
                crawler.close();
            }
        }
    }

    @Test
    @Timeout(value = 30, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_doCrawl_doesNotRetryAContentTypeMismatch() throws Exception {
        // Against the unfixed code the mismatch is a SharePointClientException, one of the two
        // types this retry loop retries, so it would be requested retryLimit+1 = 2 times before
        // being given up on - not the single attempt a deterministic configuration problem
        // deserves.
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(EMPTY_FOLDER_API, 200, "application/atom+xml", "<feed><entry>not json</entry></feed>");
            server.start();

            final CrawlerConfig config = new CrawlerConfig();
            config.setUrl(server.getBaseUrl());
            config.setSiteName("test");
            config.setInitialDocLibPath("/docs");
            config.setRetryLimit(1);

            final SharePointCrawler crawler = new SharePointCrawler(config);
            try {
                assertThrows(DataStoreCrawlingException.class, () -> crawler.doCrawl(new DataConfig()),
                        "a Content-Type mismatch must not be swallowed by the retry loop's two retryable catches");

                assertEquals("a deterministic configuration mismatch must be requested exactly once, not retried", 1,
                        server.getRecordedRequests().size());
            } finally {
                crawler.close();
            }
        }
    }

    /**
     * Against the unfixed code, getConnectionRequestTimeout() is -1 (Apache HttpClient's "wait
     * forever" default) because nothing ever calls setConnectionRequestTimeout.
     */
    @Test
    public void test_buildRequestConfig_boundsTheConnectionRequestTimeout() {
        final CrawlerConfig config = baseConfig();
        config.setConnectionTimeout(12345);

        final RequestConfig requestConfig = SharePointCrawler.buildRequestConfig(config);

        assertEquals("the connection request timeout must be bounded, not left at Apache HttpClient's unbounded default", 12345,
                requestConfig.getConnectionRequestTimeout());
        assertEquals("the connect timeout must be unaffected", 12345, requestConfig.getConnectTimeout());
        assertEquals("the socket timeout must be unaffected", config.getSocketTimeout(), requestConfig.getSocketTimeout());
    }

    /**
     * An end-to-end demonstration that the RequestConfig SharePointCrawler actually builds makes a
     * real Apache HttpClient time out waiting for a pooled connection, rather than blocking for the
     * life of the crawl the way the unfixed code's unbounded default would.
     *
     * <p>Apache HttpClient's own client-side connection pool defaults to 2 connections per route
     * (nothing here raises it - see {@code SharePointClientBuilder#buildHttpClient}, which never
     * calls {@code setMaxConnPerRoute}); the mock server itself has no connection limit of its
     * own. Two background requests held open by {@code withDelay} occupy both of the client's
     * pooled connections, so a third request on this thread has none left to wait for.
     */
    @Test
    @Timeout(value = 30, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_buildRequestConfig_connectionRequestTimeoutIsHonouredUnderPoolExhaustion() throws Exception {
        final String slowFileUrl = "/sites/test/_api/web/GetFileByServerRelativePath(decodedUrl='/slow.txt')/$value";
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(slowFileUrl, 200, "text/plain", "done");
            server.withDelay(slowFileUrl, 5000L);
            server.start();

            final CrawlerConfig config = baseConfig();
            config.setUrl(server.getBaseUrl());
            config.setConnectionTimeout(200);
            final RequestConfig requestConfig = SharePointCrawler.buildRequestConfig(config);

            try (SharePointClient client =
                    SharePointClient.builder().setUrl(server.getBaseUrl()).setSite("test").setRequestConfig(requestConfig).build()) {
                final ExecutorService occupiers = Executors.newFixedThreadPool(2);
                final CountDownLatch bothStarted = new CountDownLatch(2);
                try {
                    for (int i = 0; i < 2; i++) {
                        occupiers.submit(() -> {
                            bothStarted.countDown();
                            try {
                                client.api().file().getFile().setServerRelativeUrl("/slow.txt").execute().close();
                            } catch (final Exception e) {
                                // Not under test here - only that the connection stayed leased.
                            }
                        });
                    }
                    assertTrue("both occupying requests must have started", bothStarted.await(10, TimeUnit.SECONDS));
                    // Give the two occupiers a moment to actually lease their connections before
                    // this thread asks for a third.
                    Thread.sleep(300L);

                    final SharePointClientException e = assertThrows(SharePointClientException.class,
                            () -> client.api().file().getFile().setServerRelativeUrl("/slow.txt").execute(),
                            "a third request must fail waiting for a connection, not block for the life of the crawl");
                    assertNotNull("the timeout must be the underlying cause, not swallowed", e.getCause());
                    assertTrue("the cause must be a connection pool timeout", e.getCause() instanceof ConnectionPoolTimeoutException);
                } finally {
                    occupiers.shutdownNow();
                }
            }
        }
    }

    /**
     * Registers a working, disconnected content extractor under "extractorFactory" (plus the
     * MimeTypeHelper/FileTypeHelper components {@code FileCrawl} also reaches for), so a file
     * crawled below actually reaches a produced document instead of failing at extraction - the
     * same shape {@code FileCrawlTest#registerCapturingExtractorFactory} uses, but returning real
     * content rather than recording the component name requested.
     *
     * @param content the content every extraction request returns
     */
    private static void registerContentExtractor(final String content) {
        ComponentUtil.register(new MimeTypeHelperImpl(), MimeTypeHelper.class.getCanonicalName());
        final FileTypeHelper fileTypeHelper = new FileTypeHelper();
        fileTypeHelper.init();
        ComponentUtil.register(fileTypeHelper, "fileTypeHelper");
        final ExtractorFactory extractorFactory = new ExtractorFactory() {
            {
                this.crawlerContainer = new CrawlerContainer() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public <T> T getComponent(final String name) {
                        if ("mimeTypeHelper".equals(name)) {
                            return (T) new MimeTypeHelperImpl();
                        }
                        if ("extractorFactory".equals(name)) {
                            return (T) new ExtractorFactory();
                        }
                        return (T) (Extractor) (in, params) -> new ExtractData(content);
                    }

                    @Override
                    public boolean available() {
                        return true;
                    }

                    @Override
                    public void destroy() {
                        // nothing to release
                    }
                };
            }
        };
        ComponentUtil.register(extractorFactory, "extractorFactory");
    }

    /**
     * End-to-end proof of the invariant Task 2's 403 handling exists to protect: a 403 listing a
     * site's own subsites must not be counted as a failed crawl target, because
     * {@code SharePointDataStore#store} suppresses the whole data config's stale-document cleanup
     * whenever {@code getFailureCount()} is non-zero. A unit-level "doCrawl doesn't throw" check
     * cannot show this - only driving a real {@link SharePointCrawler} end to end and reading
     * {@link SharePointCrawler#getFailureCount()} afterwards can. This also confirms the root
     * site's own documents are still produced - the 403 must not abandon the rest of the crawl.
     */
    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_doCrawl_a403OnWebinfosDoesNotFailTheCrawlOrStopTheRootSitesDocuments() throws Exception {
        registerContentExtractor("hello world");

        final String docLibUrl = "/sites/test/Shared Documents";
        final String encodedDocLibUrl = "/sites/test/Shared%20Documents";
        final String fileUrl = docLibUrl + "/a.txt";
        final String encodedFileUrl = encodedDocLibUrl + "/a.txt";
        final String sharedDocsFolderApi = "/sites/test/_api/web/GetFolderByServerRelativePath(decodedUrl='" + encodedDocLibUrl + "')";
        final String listId = "33333333-3333-3333-3333-333333333333";
        try (SharePointMockServer server = new SharePointMockServer()) {
            // No top-level folders and no lists, so the "Shared Documents" fallback fires.
            server.onPathStatus("/sites/test/_api/web/GetFolderByServerRelativePath(decodedUrl='/sites/test/')/Folders", 200,
                    "application/json", "{\"value\": []}");
            server.onPathStatus("/sites/test/_api/lists", 200, "application/json", "{\"value\":[]}");
            // The subsite listing this crawl account cannot read - the case Task 2 exists for.
            server.onPathStatus("/sites/test/_api/web/webinfos", 403, "application/json",
                    "{\"error\":{\"message\":{\"value\":\"Access denied\"}}}");

            server.onPathStatus(sharedDocsFolderApi, 200, "application/json", "{\"ItemCount\":1}");
            server.onPathStatus(sharedDocsFolderApi + "/Folders", 200, "application/json", "{\"value\": []}");
            server.onPathStatus(sharedDocsFolderApi + "/Files", 200, "application/json",
                    "{\"value\": [{\"Name\": \"a.txt\", \"ServerRelativeUrl\": \"" + fileUrl + "\"}]}");
            server.onPathStatus("/sites/test/_api/Web/GetFolderByServerRelativePath(decodedurl='" + encodedFileUrl + "')/ListItemAllFields",
                    200, "application/json", "{\"Id\":\"1\",\"odata.editLink\":\"Web/Lists(guid'" + listId + "')/Items(1)\"}");
            server.onPathStatus("/sites/test/_api/Web/Lists(guid'" + listId + "')/Items(1)/FieldValuesAsText", 200, "application/json",
                    "{}");
            server.onPathStatus("/sites/test/_api/Web/Lists(guid'" + listId + "')/Forms", 200, "application/json",
                    "{\"value\":[{\"Id\":\"f1\",\"ServerRelativeUrl\":\"/sites/test/Lists/Docs/DispForm.aspx\",\"FormType\":4}]}");
            server.onPathStatus("/sites/test/_api/web/GetFileByServerRelativePath(decodedUrl='" + encodedFileUrl + "')/$value", 200,
                    "text/plain", "hello world");
            server.start();

            final CrawlerConfig config = new CrawlerConfig();
            config.setUrl(server.getBaseUrl());
            config.setSiteName("test");
            config.setCrawlSubsites(true);
            // Avoids needing a RoleAssignments stub - not what this test is about.
            config.setSkipRole(true);

            final SharePointCrawler crawler = new SharePointCrawler(config);
            try {
                Pair<Map<String, Object>, StatsKeyObject> result = null;
                while (crawler.hasCrawlTarget() && result == null) {
                    result = crawler.doCrawl(new DataConfig());
                }

                assertNotNull("the root site's own file must still be produced despite the 403 on webinfos", result);
                assertEquals("a 403 listing a site's own subsites must not be counted as a failed crawl target - doing so would"
                        + " suppress stale-document cleanup for the entire data config", 0L, crawler.getFailureCount());
            } finally {
                crawler.close();
            }
        }
    }

    /**
     * The common shape of the permission boundary {@code webinfos} exposes, end to end: the
     * listing succeeds - it is not security-trimmed - and names a child site the crawl account
     * cannot read, so the 403 arrives when that child's own {@code SiteCrawl} runs, not on the
     * listing. Before {@code SiteCrawl#doCrawl} caught it, that 403 propagated, exhausted
     * {@code retry_limit} and left {@code getFailureCount() == 1}, which makes
     * {@code SharePointDataStore#store} suppress the stale-document cleanup for the entire data
     * config on every run - permanently, on any farm with one permission-partitioned subsite.
     *
     * <p>Only a real {@link SharePointCrawler} can show this: the retry loop and the failure
     * counter both live there, and a unit-level check on {@code SiteCrawl} would pass either way.
     * The document assertion is half the point - skipping the subsite must not cost the root
     * site's own documents.
     */
    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_doCrawl_aListedButUnreadableSubsiteIsNotAFailure() throws Exception {
        registerContentExtractor("hello world");

        final String docLibUrl = "/sites/test/Shared Documents";
        final String encodedDocLibUrl = "/sites/test/Shared%20Documents";
        final String fileUrl = docLibUrl + "/a.txt";
        final String encodedFileUrl = encodedDocLibUrl + "/a.txt";
        final String sharedDocsFolderApi = "/sites/test/_api/web/GetFolderByServerRelativePath(decodedUrl='" + encodedDocLibUrl + "')";
        final String subsiteFoldersApi = "/sites/test/sub/_api/web/GetFolderByServerRelativePath(decodedUrl='/sites/test/sub/')/Folders";
        final String listId = "33333333-3333-3333-3333-333333333333";
        try (SharePointMockServer server = new SharePointMockServer()) {
            // No top-level folders and no lists, so the "Shared Documents" fallback fires.
            server.onPathStatus("/sites/test/_api/web/GetFolderByServerRelativePath(decodedUrl='/sites/test/')/Folders", 200,
                    "application/json", "{\"value\": []}");
            server.onPathStatus("/sites/test/_api/lists", 200, "application/json", "{\"value\":[]}");
            // The listing succeeds and names the child: webinfos is not security-trimmed.
            server.onPathStatus("/sites/test/_api/web/webinfos", 200, "application/json",
                    "{\"value\": [{\"Id\":\"1\",\"Title\":\"HR\",\"ServerRelativeUrl\":\"/sites/test/sub\"}]}");
            // ... and the child's own first request is where the account's lack of access shows.
            server.onPathStatus(subsiteFoldersApi, 403, "application/json", "{\"error\":{\"message\":{\"value\":\"Access denied\"}}}");

            server.onPathStatus(sharedDocsFolderApi, 200, "application/json", "{\"ItemCount\":1}");
            server.onPathStatus(sharedDocsFolderApi + "/Folders", 200, "application/json", "{\"value\": []}");
            server.onPathStatus(sharedDocsFolderApi + "/Files", 200, "application/json",
                    "{\"value\": [{\"Name\": \"a.txt\", \"ServerRelativeUrl\": \"" + fileUrl + "\"}]}");
            server.onPathStatus("/sites/test/_api/Web/GetFolderByServerRelativePath(decodedurl='" + encodedFileUrl + "')/ListItemAllFields",
                    200, "application/json", "{\"Id\":\"1\",\"odata.editLink\":\"Web/Lists(guid'" + listId + "')/Items(1)\"}");
            server.onPathStatus("/sites/test/_api/Web/Lists(guid'" + listId + "')/Items(1)/FieldValuesAsText", 200, "application/json",
                    "{}");
            server.onPathStatus("/sites/test/_api/Web/Lists(guid'" + listId + "')/Forms", 200, "application/json",
                    "{\"value\":[{\"Id\":\"f1\",\"ServerRelativeUrl\":\"/sites/test/Lists/Docs/DispForm.aspx\",\"FormType\":4}]}");
            server.onPathStatus("/sites/test/_api/web/GetFileByServerRelativePath(decodedUrl='" + encodedFileUrl + "')/$value", 200,
                    "text/plain", "hello world");
            server.start();

            final CrawlerConfig config = new CrawlerConfig();
            config.setUrl(server.getBaseUrl());
            config.setSiteName("test");
            config.setCrawlSubsites(true);
            // Avoids needing a RoleAssignments stub - not what this test is about.
            config.setSkipRole(true);

            final SharePointCrawler crawler = new SharePointCrawler(config);
            try {
                // Drains the whole queue, so the unreadable subsite is actually reached: stopping
                // at the first document would return before its SiteCrawl ever ran.
                final List<Map<String, Object>> documents = new ArrayList<>();
                while (crawler.hasCrawlTarget()) {
                    final Pair<Map<String, Object>, StatsKeyObject> result = crawler.doCrawl(new DataConfig());
                    if (result != null) {
                        documents.add(result.getFirst());
                    }
                }

                assertEquals(
                        "a subsite the crawl account cannot read must be skipped, not counted as a failed crawl target -"
                                + " counting it suppresses stale-document cleanup for the entire data config on every run",
                        0L, crawler.getFailureCount());
                assertEquals("the root site's own file must still be produced", 1, documents.size());
                assertEquals("the document produced must be the root site's own file", fileUrl,
                        documents.get(0).get(ComponentUtil.getFessConfig().getIndexFieldSite()));
                assertEquals("the subsite must be skipped on its first 403, without spending a single retry", 1,
                        server.getRecordedRequests().stream().filter(request -> subsiteFoldersApi.equals(request.getPath())).count());
            } finally {
                crawler.close();
            }
        }
    }
}
