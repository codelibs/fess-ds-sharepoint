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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

    /**
     * Two authentication methods at once must fail crawler construction.
     *
     * <p>Only one credential can be registered - {@code SharePointClientBuilder} holds a single
     * one - and the scope it is registered under, {@code AuthScope.ANY} for everything except
     * Kerberos, matches a {@code Negotiate} challenge as readily as an {@code NTLM} one. So the
     * combination does not produce an error anywhere; it produces 401s that nothing explains. This
     * is the loud failure that replaces that. See
     * {@code KerberosAuthenticationTest#test_aNegotiateChallengeIsNotAnsweredWithNtlmCredentials}
     * for the silent version demonstrated end to end.
     */
    @Test
    public void test_construction_rejectsMoreThanOneAuthenticationMethod() {
        final CrawlerConfig kerberosAndNtlm = baseConfig();
        kerberosAndNtlm.setKerberosPrincipal("fess@EXAMPLE.COM");
        kerberosAndNtlm.setNtlmUser("fess");
        assertThrows(ValidationException.class, () -> new SharePointCrawler(kerberosAndNtlm),
                "auth.kerberos.principal and auth.ntlm.user together must be rejected");

        final CrawlerConfig kerberosAndOauth = baseConfig();
        kerberosAndOauth.setKerberosPrincipal("fess@EXAMPLE.COM");
        kerberosAndOauth.setOauthClientId("client-id");
        assertThrows(ValidationException.class, () -> new SharePointCrawler(kerberosAndOauth),
                "auth.kerberos.principal and auth.oauth.client_id together must be rejected");

        final CrawlerConfig ntlmAndOauth = baseConfig();
        ntlmAndOauth.setNtlmUser("fess");
        ntlmAndOauth.setOauthClientId("client-id");
        assertThrows(ValidationException.class, () -> new SharePointCrawler(ntlmAndOauth),
                "auth.ntlm.user and auth.oauth.client_id together must be rejected");

        final CrawlerConfig allThree = baseConfig();
        allThree.setKerberosPrincipal("fess@EXAMPLE.COM");
        allThree.setNtlmUser("fess");
        allThree.setOauthClientId("client-id");
        final ValidationException e =
                assertThrows(ValidationException.class, () -> new SharePointCrawler(allThree), "all three together must be rejected");
        assertTrue("the message must name every method that is set, not just the first two found",
                e.getMessage().contains("auth.kerberos.principal") && e.getMessage().contains("auth.ntlm.user")
                        && e.getMessage().contains("auth.oauth.client_id"));

        // The two OAuth-carrying configs above are safe to construct here only because validate()
        // runs before createClient(). SharePointClientBuilder#build calls OAuth#updateAccessToken,
        // which posts to accounts.accesscontrol.windows.net - a live HTTPS request from a unit
        // test. If the constructor is ever reordered so the client is built first, or if this
        // validation is moved later, these become network-dependent and will fail (or hang) on a
        // machine with no route out. Keep validate() first.
    }

    /**
     * {@code auth.kerberos.keytab} and {@code auth.kerberos.password} are documented as mutually
     * exclusive, and only the keytab is ever used when both are set - so the combination has to be
     * rejected rather than silently resolved. Both parameters are new, so nothing can depend on the
     * old behaviour.
     */
    @Test
    public void test_construction_rejectsAKeytabAndAPasswordTogether() {
        final CrawlerConfig both = baseConfig();
        both.setKerberosPrincipal("fess@EXAMPLE.COM");
        both.setKerberosKeytab("/etc/security/keytabs/fess.keytab");
        both.setKerberosPassword("password");

        final ValidationException e = assertThrows(ValidationException.class, () -> new SharePointCrawler(both),
                "a keytab and a password together must be rejected rather than the keytab silently winning");
        assertTrue("the message must name both parameters",
                e.getMessage().contains("auth.kerberos.keytab") && e.getMessage().contains("auth.kerberos.password"));
    }

    /**
     * The validation must not reject anything that works today: NTLM alone and no authentication
     * at all are both still accepted, and a blank value is not "set" - the admin UI produces one
     * from a field left empty rather than removed, and {@code createClient} already ignores it.
     *
     * <p>OAuth alone is deliberately not exercised here: constructing a crawler with an
     * {@code auth.oauth.client_id} set makes {@code SharePointClientBuilder#build} call
     * {@code OAuth#updateAccessToken}, which posts to {@code accounts.accesscontrol.windows.net}.
     * A unit test must not reach the network. The rejection side above covers OAuth, because
     * {@code validate} throws before any client is built.
     */
    @Test
    public void test_construction_acceptsExactlyOneAuthenticationMethod() {
        final CrawlerConfig ntlmOnly = baseConfig();
        ntlmOnly.setNtlmUser("fess");
        assertDoesNotThrow(() -> new SharePointCrawler(ntlmOnly), "NTLM alone must keep working");

        final CrawlerConfig noneAtAll = baseConfig();
        assertDoesNotThrow(() -> new SharePointCrawler(noneAtAll), "an unauthenticated crawl must keep working");

        final CrawlerConfig blankKerberosAlongsideNtlm = baseConfig();
        blankKerberosAlongsideNtlm.setKerberosPrincipal("");
        blankKerberosAlongsideNtlm.setNtlmUser("fess");
        assertDoesNotThrow(() -> new SharePointCrawler(blankKerberosAlongsideNtlm),
                "a blank auth.kerberos.principal is not a configured authentication method");
    }

    /**
     * {@code auth.ntlm.domain} and {@code auth.ntlm.workstation} were never exposed, so the
     * credential was built with both hardcoded to null. Unset they must stay null, and set they
     * must land in the right positions of {@link org.apache.http.auth.NTCredentials} - the two are
     * easy to swap, and swapping them fails only against a real domain controller.
     */
    @Test
    public void test_ntlmDomainAndWorkstationDefaultToNull() {
        final CrawlerConfig config = baseConfig();
        assertNull("auth.ntlm.domain must default to null", config.getNtlmDomain());
        assertNull("auth.ntlm.workstation must default to null", config.getNtlmWorkstation());
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

    // === number_of_threads ===

    /**
     * A crawl unit that produces exactly one document, identified by {@code id}, after running
     * {@code onCrawl}.
     */
    private static SharePointCrawl documentCrawl(final String id, final Runnable onCrawl) {
        return new SharePointCrawl(null) {
            {
                statsKey = new StatsKeyObject(id);
            }

            @Override
            public Map<String, Object> doCrawl(final DataConfig dataConfig, final Queue<SharePointCrawl> crawlingQueue) {
                onCrawl.run();
                // A HashMap, not Map.of: storeData mutates the map a crawl unit returns
                // (resultMap.remove) and swallows the UnsupportedOperationException an immutable
                // one would throw, so a test that hands production code an immutable map here can
                // pass having run nothing.
                final Map<String, Object> dataMap = new HashMap<>();
                dataMap.put("id", id);
                return dataMap;
            }
        };
    }

    /** Builds a crawler whose only real crawl target is an empty document library. */
    private static CrawlerConfig emptyDocLibConfig(final SharePointMockServer server) {
        final CrawlerConfig config = new CrawlerConfig();
        config.setUrl(server.getBaseUrl());
        config.setSiteName("test");
        config.setInitialDocLibPath("/docs");
        return config;
    }

    /** Drains a crawler to exhaustion, collecting the id of every document it produced. */
    private static List<String> drain(final SharePointCrawler crawler) {
        final List<String> ids = new ArrayList<>();
        while (crawler.hasCrawlTarget()) {
            final Pair<Map<String, Object>, StatsKeyObject> result = crawler.doCrawl(new DataConfig());
            if (result != null) {
                ids.add((String) result.getFirst().get("id"));
            }
        }
        return ids;
    }

    /**
     * The invariant every other test in this section is allowed to assume: a data config that does
     * not set {@code number_of_threads} gets the crawl it always got. Same requests, same
     * documents, same order - and no thread pool, so the default configuration cannot inherit any
     * of the failure modes a pool brings with it.
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_theDefaultIsStillASingleThreadedCrawl() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(EMPTY_FOLDER_API, 200, "application/json", "{\"ItemCount\":0}");
            server.start();

            // number_of_threads deliberately not set.
            final SharePointCrawler crawler = new SharePointCrawler(emptyDocLibConfig(server));
            final String callingThread = Thread.currentThread().getName();
            final List<String> crawledOn = Collections.synchronizedList(new ArrayList<>());
            crawler.offerCrawlTargetForTest(documentCrawl("a", () -> crawledOn.add(Thread.currentThread().getName())));
            crawler.offerCrawlTargetForTest(documentCrawl("b", () -> crawledOn.add(Thread.currentThread().getName())));
            crawler.offerCrawlTargetForTest(documentCrawl("c", () -> crawledOn.add(Thread.currentThread().getName())));
            try {
                assertNull("no worker pool may be created when number_of_threads is unset", crawler.getExecutorService());
                assertEquals("the default must resolve to a single-threaded crawl", 1, crawler.getNumberOfThreads());

                final List<String> ids = drain(crawler);

                assertEquals("every document must be returned, in the order its target was queued", List.of("a", "b", "c"), ids);
                assertEquals("the empty document library listing must still be the only request made", 1,
                        server.getRecordedRequests().size());
                assertEquals("every crawl target must run on the calling thread - there is no other thread to run on",
                        List.of(callingThread, callingThread, callingThread), crawledOn);
            } finally {
                crawler.close();
            }
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_everyDocumentIsReturnedExactlyOnceWithFourThreads() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(EMPTY_FOLDER_API, 200, "application/json", "{\"ItemCount\":0}");
            server.start();

            final CrawlerConfig config = emptyDocLibConfig(server);
            config.setNumberOfThreads(4);

            final SharePointCrawler crawler = new SharePointCrawler(config);
            final Set<String> expected = new LinkedHashSet<>();
            for (int i = 0; i < 50; i++) {
                final String id = "doc-" + i;
                expected.add(id);
                crawler.offerCrawlTargetForTest(documentCrawl(id, () -> {
                    // Long enough that the units genuinely overlap rather than being finished by
                    // whichever worker happens to get there first.
                    try {
                        Thread.sleep(5L);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }
            try {
                final List<String> ids = drain(crawler);

                assertEquals("no document may be produced twice", ids.size(), new HashSet<>(ids).size());
                assertEquals("every queued target's document must come back", expected, new HashSet<>(ids));
            } finally {
                crawler.close();
            }
        }
    }

    /**
     * The regression this whole change is at risk of: a worker that has taken a discovery unit but
     * has not yet enqueued what it found leaves the queue legitimately empty with the crawl far
     * from over. A termination test that only asks whether the queue is empty ends the crawl here -
     * and a crawl that ends early having recorded no failure lets core delete every document it
     * never reached.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_theCrawlEndsWhenDiscoveryIsStillInFlightButTheQueueIsMomentarilyEmpty() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(EMPTY_FOLDER_API, 200, "application/json", "{\"ItemCount\":0}");
            server.start();

            final CrawlerConfig config = emptyDocLibConfig(server);
            config.setNumberOfThreads(4);

            final SharePointCrawler crawler = new SharePointCrawler(config);
            // Produces no document of its own, and takes its time before enqueuing the ones that
            // do - so for most of a second the queue is empty and the results are empty while
            // three documents are still to come.
            crawler.offerCrawlTargetForTest(new SharePointCrawl(null) {
                {
                    statsKey = new StatsKeyObject("slow-discovery");
                }

                @Override
                public Map<String, Object> doCrawl(final DataConfig dataConfig, final Queue<SharePointCrawl> crawlingQueue) {
                    try {
                        Thread.sleep(500L);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    crawlingQueue.offer(documentCrawl("child-1", () -> {}));
                    crawlingQueue.offer(documentCrawl("child-2", () -> {}));
                    crawlingQueue.offer(documentCrawl("child-3", () -> {}));
                    return null;
                }
            });
            try {
                final List<String> ids = drain(crawler);

                assertEquals("the crawl must not finish while a discovery unit is still working", Set.of("child-1", "child-2", "child-3"),
                        new HashSet<>(ids));
            } finally {
                crawler.close();
            }
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_aStopRequestEndsTheCrawlAndDrainsTheWorkers() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(EMPTY_FOLDER_API, 200, "application/json", "{\"ItemCount\":0}");
            server.start();

            final CrawlerConfig config = emptyDocLibConfig(server);
            config.setNumberOfThreads(4);

            final SharePointCrawler crawler = new SharePointCrawler(config);
            final AtomicBoolean stopped = new AtomicBoolean();
            final AtomicInteger crawled = new AtomicInteger();
            crawler.setStopRequested(stopped::get);
            for (int i = 0; i < 40; i++) {
                crawler.offerCrawlTargetForTest(documentCrawl("doc-" + i, () -> {
                    crawled.incrementAndGet();
                    try {
                        Thread.sleep(100L);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }
            try {
                Pair<Map<String, Object>, StatsKeyObject> first = null;
                while (first == null && crawler.hasCrawlTarget()) {
                    first = crawler.doCrawl(new DataConfig());
                }
                assertNotNull("the crawl must produce documents before it is stopped", first);

                stopped.set(true);

                assertNull("a stop must end the crawl rather than waiting for the queue to drain", crawler.doCrawl(new DataConfig()));
                assertTrue("a stop must leave the targets behind it uncrawled", crawled.get() < 40);
            } finally {
                crawler.close();
            }
            assertTrue("close() must have stopped every worker", crawler.getExecutorService().awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    /**
     * A close is the other way every worker leaves, and the crawling thread has to notice it for
     * the same reason it notices a stop: work that is outstanding is only worth waiting for while
     * there is a worker left to do it. Without the {@code closing} check in the poll loop this test
     * does not fail - it never finishes, because the crawling thread waits for documents from
     * threads that have already gone. The timeout is what turns that into a failing test rather
     * than a wedged build.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_aCloseFromAnotherThreadEndsACrawlInProgress() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(EMPTY_FOLDER_API, 200, "application/json", "{\"ItemCount\":0}");
            server.start();

            final CrawlerConfig config = emptyDocLibConfig(server);
            config.setNumberOfThreads(4);

            final SharePointCrawler crawler = new SharePointCrawler(config);
            // None of these produce a document, so the crawling thread below stays in its poll
            // loop rather than returning early with one; there is far more queued than the close
            // will let the workers get through.
            for (int i = 0; i < 200; i++) {
                crawler.offerCrawlTargetForTest(recordingCrawl("slow-" + i, () -> {
                    try {
                        Thread.sleep(50L);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }
            final AtomicReference<Throwable> closeFailure = new AtomicReference<>();
            final Thread closer = new Thread(() -> {
                try {
                    Thread.sleep(300L);
                    crawler.close();
                } catch (final Throwable t) {
                    closeFailure.set(t);
                }
            });
            closer.start();
            try {
                assertNull("a close from another thread must end the crawl, not leave the crawling thread waiting forever",
                        crawler.doCrawl(new DataConfig()));
                assertFalse("and the crawl must not claim it still has targets afterwards", crawler.hasCrawlTarget());
            } finally {
                closer.join(30_000L);
            }
            assertNull("the close itself must not have failed", closeFailure.get());
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_theThreadCountIsCappedAtTwiceTheProcessorCount() throws Exception {
        final CrawlerConfig config = baseConfig();
        config.setNumberOfThreads(1000);

        try (SharePointCrawler crawler = new SharePointCrawler(config)) {
            assertEquals("number_of_threads must be capped so a data config cannot exhaust the host",
                    Runtime.getRuntime().availableProcessors() * 2, crawler.getNumberOfThreads());
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_aThreadCountBelowOneFallsBackToTheSingleThreadedCrawl() throws Exception {
        final CrawlerConfig config = baseConfig();
        config.setNumberOfThreads(0);

        try (SharePointCrawler crawler = new SharePointCrawler(config)) {
            assertEquals("a thread count below 1 must fall back to 1 rather than failing the job", 1, crawler.getNumberOfThreads());
            assertNull("and it must take the no-pool path, not build a pool of one", crawler.getExecutorService());
        }
    }

    /**
     * A retry is the retry loop inside one unit's crawl, not a re-queue, so every attempt is made
     * by the worker that owns the unit. This test is what says so if anyone ever "fixes" a failed
     * target by putting it back on the queue: a re-queued target would be crawled a second time by
     * another worker, repeating its requests and counting its own failure.
     *
     * <p>This says nothing about stats keys - two different units can share one id, and that is
     * accepted; see {@code SharePointCrawler#crawlUnit}.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_aRetryStaysOnTheWorkerThatOwnsTheUnit() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(EMPTY_FOLDER_API, 200, "application/json", "{\"ItemCount\":0}");
            server.start();

            final CrawlerConfig config = emptyDocLibConfig(server);
            config.setNumberOfThreads(4);
            config.setRetryLimit(1);

            final SharePointCrawler crawler = new SharePointCrawler(config);
            final List<Long> recordedSleeps = Collections.synchronizedList(new ArrayList<>());
            crawler.setBackoff(new SharePointBackoff(1L, 2L, () -> 0.5d, recordedSleeps::add));
            final List<String> attemptThreads = Collections.synchronizedList(new ArrayList<>());
            final AtomicInteger attempts = new AtomicInteger();
            crawler.offerCrawlTargetForTest(new SharePointCrawl(null) {
                {
                    statsKey = new StatsKeyObject("retried");
                }

                @Override
                public Map<String, Object> doCrawl(final DataConfig dataConfig, final Queue<SharePointCrawl> crawlingQueue) {
                    attemptThreads.add(Thread.currentThread().getName());
                    if (attempts.incrementAndGet() == 1) {
                        throw new SharePointClientException("GetFile Request failure. status:503 body:", 503);
                    }
                    final Map<String, Object> dataMap = new HashMap<>();
                    dataMap.put("id", "retried");
                    return dataMap;
                }
            });
            try {
                final List<String> ids = drain(crawler);

                assertEquals("the retried target must still produce its document exactly once", List.of("retried"), ids);
                assertEquals("it must have been attempted twice", 2, attemptThreads.size());
                assertEquals("both attempts must be made by the one worker that owns the unit", attemptThreads.get(0),
                        attemptThreads.get(1));
                assertEquals("the 503 must still have been backed off before the retry", 1, recordedSleeps.size());
                assertEquals("nothing may be counted as given up on", 0L, crawler.getFailureCount());
            } finally {
                crawler.close();
            }
        }
    }

    /**
     * A worker that swallowed the exception its crawl target threw would leave the crawl reporting
     * success with a target silently lost - and core deletes every document a successful crawl did
     * not refresh. The exception has to arrive on the crawling thread, where
     * {@code SharePointDataStore#storeData} already counts it.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_aWorkerFailureIsRaisedOnTheCrawlingThread() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(EMPTY_FOLDER_API, 200, "application/json", "{\"ItemCount\":0}");
            server.start();

            final CrawlerConfig config = emptyDocLibConfig(server);
            config.setNumberOfThreads(4);

            final SharePointCrawler crawler = new SharePointCrawler(config);
            crawler.offerCrawlTargetForTest(recordingCrawl("exploding", () -> {
                throw new IllegalStateException("boom");
            }));
            try {
                assertThrows(DataStoreCrawlingException.class, () -> drain(crawler),
                        "a crawl target that fails on a worker must fail the caller's doCrawl, not disappear");
            } finally {
                crawler.close();
            }
        }
    }

    /**
     * The same guarantee for the other way a target is lost: retries exhausted. The count is what
     * {@code SharePointDataStore#store} reads to decide whether stale documents may be deleted, so
     * a worker that failed to increment it would cost the index every document this crawl missed.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_aTargetGivenUpOnByAWorkerIsStillCounted() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(EMPTY_FOLDER_API, 200, "application/json", "{\"ItemCount\":0}");
            server.start();

            final CrawlerConfig config = emptyDocLibConfig(server);
            config.setNumberOfThreads(4);
            config.setRetryLimit(0);

            final SharePointCrawler crawler = new SharePointCrawler(config);
            crawler.offerCrawlTargetForTest(recordingCrawl("always-503", () -> {
                throw new SharePointClientException("GetFile Request failure. status:503 body:", 503);
            }));
            try {
                drain(crawler);

                assertEquals("a target a worker gave up on must be counted, or the crawl reports a clean run it did not have", 1L,
                        crawler.getFailureCount());
            } finally {
                crawler.close();
            }
        }
    }
}
