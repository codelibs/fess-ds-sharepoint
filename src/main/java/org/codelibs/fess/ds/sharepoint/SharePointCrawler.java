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

import java.io.Closeable;
import java.io.IOException;
import java.util.AbstractQueue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.core.misc.Pair;
import org.codelibs.fess.app.service.FailureUrlService;
import org.codelibs.fess.crawler.filter.UrlFilter;
import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.client.SharePointClientBuilder;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.backoff.SharePointBackoff;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemRoleResponse;
import org.codelibs.fess.ds.sharepoint.client.credential.KerberosCredential;
import org.codelibs.fess.ds.sharepoint.client.credential.NtlmCredential;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointServerException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;
import org.codelibs.fess.ds.sharepoint.crawl.SharePointCrawl;
import org.codelibs.fess.ds.sharepoint.crawl.SiteCrawl;
import org.codelibs.fess.ds.sharepoint.crawl.doclib.FolderCrawl;
import org.codelibs.fess.ds.sharepoint.crawl.file.FileCrawl;
import org.codelibs.fess.ds.sharepoint.crawl.list.ListCrawl;
import org.codelibs.fess.exception.DataStoreCrawlingException;
import org.codelibs.fess.helper.CrawlerStatsHelper;
import org.codelibs.fess.helper.CrawlerStatsHelper.StatsAction;
import org.codelibs.fess.helper.CrawlerStatsHelper.StatsKeyObject;
import org.codelibs.fess.opensearch.config.exentity.DataConfig;
import org.codelibs.fess.util.ComponentUtil;
import org.lastaflute.di.core.exception.ComponentNotFoundException;

import jakarta.validation.ValidationException;

/**
 * Crawler for crawling SharePoint sites.
 */
public class SharePointCrawler implements Closeable {
    private static final Logger logger = LogManager.getLogger(SharePointCrawler.class);

    /**
     * How long {@link #doCrawl} waits for a worker to hand over a document before re-testing
     * whether the crawl has finished, and how long a worker waits for a crawl unit before
     * re-testing whether it should exit. Short enough that a stop or a close is noticed promptly,
     * long enough that neither loop is a spin.
     */
    private static final long POLL_TIMEOUT_MILLIS = 100L;

    /**
     * How long {@link #close()} waits for the workers to finish what they are doing before
     * interrupting them. A worker can be in the middle of an HTTP request, which is bounded by the
     * socket timeout, not by anything here.
     */
    private static final long EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 60L;

    private final SharePointClient client;

    private final CrawlQueue crawlingQueue = new CrawlQueue();

    private final CrawlerConfig config;

    private final AtomicLong failureCount = new AtomicLong();

    private final UrlFilter urlFilter;

    /**
     * How many crawl units are worked on at once, already capped to twice the processor count.
     * {@code 1} - the default - means every unit runs on the caller's thread and no pool exists.
     */
    private final int numberOfThreads;

    /**
     * The worker pool, or null when {@link #numberOfThreads} is 1. Null is what keeps the default
     * configuration on exactly the code path it had before this parameter existed: no pool is
     * created, no thread is started, and {@link #doCrawl} drains the queue on the caller's thread.
     */
    private final ExecutorService executorService;

    /**
     * Documents the workers have finished and {@link #doCrawl} has not handed to its caller yet,
     * or null when single-threaded.
     *
     * <p>Bounded, at one document per worker. A worker blocks handing over its document once it is
     * full, which is what keeps {@code SharePointDataStore#storeData}'s {@code read_interval} sleep
     * meaningful - the crawl runs no faster than documents are consumed - and what stops a fast
     * farm from filling the heap with file contents faster than they are indexed.
     */
    private final BlockingQueue<CrawlResult> results;

    /** Whether the workers have been started; they are started by the first {@link #doCrawl} call. */
    private final AtomicBoolean workersStarted = new AtomicBoolean();

    /**
     * Set by {@link #close()} before the pool is shut down, so a worker waiting on the queue or on
     * a full results queue leaves instead of being interrupted out of an HTTP request. Also set
     * when the crawling thread is interrupted, so the crawl ends rather than spinning.
     */
    private volatile boolean closing;

    /**
     * The wait applied before retrying a request SharePoint answered with 503, growing with each
     * successive retry of the same crawl unit. Package-private so a test can replace it with one
     * that records the delay instead of actually sleeping for it. Volatile because worker threads
     * read it.
     */
    private volatile SharePointBackoff backoff = SharePointBackoff.defaults();

    /**
     * Answers whether the job this crawl belongs to has been asked to stop.
     *
     * <p>Core's stop is a flag, not an interrupt: {@code AbstractDataStore#stop} clears
     * {@code alive} and nothing interrupts the crawling thread. {@code SharePointDataStore}
     * supplies a supplier reading that flag, so the queue loop below can notice it too - see
     * {@link #doCrawl} for the window that remains. Defaults to "never stop" so a crawler built
     * outside {@code SharePointDataStore} behaves exactly as it did before.
     *
     * <p>Volatile because the workers read it too. The flag it reads is itself volatile
     * ({@code AbstractDataStore#alive}), so a stop reaches every worker without any further
     * synchronization.
     */
    private volatile BooleanSupplier stopRequested = () -> false;

    /**
     * Creates a new SharePointCrawler with the specified configuration.
     *
     * @param config the crawler configuration
     */
    public SharePointCrawler(final CrawlerConfig config) {
        validate(config);
        this.numberOfThreads = resolveNumberOfThreads(config.getNumberOfThreads());
        if (numberOfThreads > 1) {
            this.results = new ArrayBlockingQueue<>(numberOfThreads);
            this.executorService = newFixedThreadPool(numberOfThreads);
        } else {
            this.results = null;
            this.executorService = null;
        }
        this.client = createClient(config);
        this.config = config;
        this.urlFilter = buildUrlFilter(config);
        setFirstCrawl(config);
        if (crawlingQueue.isEmpty()) {
            logger.error("Failed to start crawl.");
        }
    }

    /**
     * Decides how many crawl units this crawl works on at once.
     *
     * <p>Capped at twice the processor count, the same bound {@code fess-ds-microsoft365} applies -
     * capped here, rather than where the pool is built, because the HTTP connection pool is sized
     * to the result (see {@link #createClient}) and has to match what actually runs. A value below
     * 1 falls back to 1 rather than failing the job, for the same reason
     * {@code SharePointDataStore#parseInt} falls back on a malformed number.
     *
     * @param requested the configured {@code number_of_threads}
     * @return the number of threads that will actually be used, never below 1
     */
    private static int resolveNumberOfThreads(final int requested) {
        final int maxThreads = Runtime.getRuntime().availableProcessors() * 2;
        if (requested < 1) {
            logger.warn("number_of_threads is {}, which is not a usable thread count. Using 1.", requested);
            return 1;
        }
        if (requested > maxThreads) {
            logger.info("number_of_threads {} is capped at {} (twice the processor count).", requested, maxThreads);
            return maxThreads;
        }
        return requested;
    }

    /**
     * Builds the worker pool, with the lifecycle {@code fess-ds-microsoft365} uses: a fixed number
     * of threads, a bounded hand-off queue, and {@code CallerRunsPolicy} so a submission can never
     * be silently dropped.
     *
     * <p>This crawler submits exactly {@code nThreads} long-lived tasks, one per thread, so neither
     * the hand-off queue nor the rejection policy is exercised in practice; they are the safe
     * configuration to have if that ever changes.
     *
     * @param nThreads the number of threads, already capped
     * @return the pool
     */
    private static ExecutorService newFixedThreadPool(final int nThreads) {
        return new ThreadPoolExecutor(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(nThreads),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    private void validate(final CrawlerConfig config) {
        if (config.url == null) {
            throw new ValidationException("url param is required.");
        }
        if (config.siteName == null && StringUtils.isBlank(config.sitePath)) {
            throw new ValidationException("sitename param is required unless site.path is set.");
        }
        // Validated here, in the constructor, rather than only where GetList/GetList2013
        // interpolate it into an OData guid'...' literal: this runs before
        // SharePointDataStore#storeData's try block even starts (createCrawler is called
        // outside it), so the ValidationException below escapes uncaught and fails the job -
        // the same way a missing url or siteName already does. Validating only at the OData
        // call site would leave a malformed site.list_id to reach doCrawl's generic
        // catch (Exception e) during an actual crawl attempt instead: that branch discards the
        // stats key and wraps the exception in a DataStoreCrawlingException whose 3-arg
        // constructor sets abort=false, which storeData then downgrades to a warning and
        // finishes as a zero-document "success" - not a retry, just one catch and a quiet exit.
        if (StringUtils.isNotBlank(config.initialListId)) {
            SharePointApi.requireGuidLiteral(config.initialListId, "site.list_id");
        }
        validateSingleAuthenticationMethod(config);
        validateSingleKerberosSecret(config);
    }

    /**
     * Rejects a configuration that sets both {@code auth.kerberos.keytab} and
     * {@code auth.kerberos.password}.
     *
     * <p>The two are documented as mutually exclusive, and the login can only use one: a keytab
     * makes {@code Krb5LoginModule} run with {@code useKeyTab=true} and the password is never
     * consulted. Left unchecked, an operator who added a keytab without removing the password they
     * were using before would get the keytab silently and no sign that half the configuration is
     * dead. Both parameters are new, so nothing can be relying on the old behaviour.
     *
     * @param config the crawler configuration
     */
    private void validateSingleKerberosSecret(final CrawlerConfig config) {
        if (StringUtils.isNotBlank(config.kerberosKeytab) && StringUtils.isNotBlank(config.kerberosPassword)) {
            throw new ValidationException("auth.kerberos.keytab and auth.kerberos.password are mutually exclusive, but both are set."
                    + " A keytab makes the password unused rather than a fallback. Remove whichever one is not wanted.");
        }
    }

    /**
     * Rejects a configuration that sets more than one authentication method.
     *
     * <p>This is what keeps the combination from failing silently rather than loudly.
     * {@link SharePointClientBuilder#setCredential} holds a single value, and {@link #createClient}
     * sets the NTLM credential first and the Kerberos one second - so configuring both does not
     * produce two registered credentials to choose between: the NTLM credential the operator
     * configured is discarded before the client is ever built, with nothing logged. What is left
     * registered is the Kerberos credential, and {@code KerberosCredential#getAuthScope} narrows
     * it to {@code AuthSchemes.SPNEGO} rather than {@code AuthScope.ANY}. A farm - or a reverse
     * proxy in front of one - that answers with a {@code Basic} or {@code NTLM} challenge instead
     * of {@code Negotiate} therefore finds no credential registered for that scheme at all, and
     * nothing says so: {@code AuthenticationStrategyImpl#select} skips a scheme with no credentials
     * at debug level, and {@code HttpAuthenticator} logs the resulting authentication error and
     * carries on. The operator sees unexplained 401s from a crawl configured with the very
     * credentials that would have answered the challenge.
     *
     * <p>OAuth is in the same list because it is applied as an {@code Authorization} header by
     * {@code SharePointApi}, independently of whatever the credentials provider answers with, so
     * combining it with either of the others means two different identities racing on the same
     * request.
     *
     * @param config the crawler configuration
     */
    private void validateSingleAuthenticationMethod(final CrawlerConfig config) {
        final List<String> configured = new ArrayList<>(3);
        if (StringUtils.isNotBlank(config.kerberosPrincipal)) {
            configured.add("auth.kerberos.principal");
        }
        if (StringUtils.isNotBlank(config.ntlmUser)) {
            configured.add("auth.ntlm.user");
        }
        if (StringUtils.isNotBlank(config.oauthClientId)) {
            configured.add("auth.oauth.client_id");
        }
        if (configured.size() > 1) {
            throw new ValidationException("Only one authentication method may be configured, but " + String.join(", ", configured)
                    + " are all set. Only one credential is ever registered, so the others are silently discarded - or, for OAuth,"
                    + " applied on top as a competing identity - leaving the crawl authenticating as something other than what was"
                    + " configured, with no log line explaining it. Remove all but one.");
        }
    }

    private SharePointClient createClient(final CrawlerConfig config) {
        final SharePointClientBuilder builder = SharePointClient.builder()
                .setUrl(config.getUrl())
                .setSite(config.getSiteName())
                .setSitePath(config.getSiteRelativePath())
                .setRequestConfig(buildRequestConfig(config))
                // Every worker shares this one client - including the sibling clients
                // SharePointClient#forSitePath hands a subsite crawl, which share its connection
                // pool. Apache HttpClient allows 2 connections per route by default and a crawl is
                // a single route, so without this every thread past the second would spend the
                // crawl waiting for a connection. Below 2 threads this is a no-op and the client
                // keeps the pool it has always had.
                .setMaxConnections(numberOfThreads);
        final String ntlmUser = config.getNtlmUser();
        if (StringUtils.isNotBlank(ntlmUser)) {
            final String ntlmPass = config.getNtlmPassword();
            // Both default to null, so an installation that has never set them gets exactly the
            // credential this connector has always built. That includes one that writes
            // DOMAIN\\user into auth.ntlm.user: NTCredentials does not split it, so the whole
            // string keeps going out as the NTLM user name exactly as it does today.
            builder.setCredential(new NtlmCredential(ntlmUser, ntlmPass, config.getNtlmWorkstation(), config.getNtlmDomain()));
        }
        final String kerberosPrincipal = config.getKerberosPrincipal();
        if (StringUtils.isNotBlank(kerberosPrincipal)) {
            // validate() has already rejected this being set alongside auth.ntlm.user or
            // auth.oauth.client_id, so this never replaces a credential set above.
            builder.setCredential(new KerberosCredential(kerberosPrincipal, config.getKerberosKeytab(), config.getKerberosPassword(),
                    config.getKerberosKrb5Conf(), config.isKerberosStripPort(), config.isKerberosUseCanonicalHostname(),
                    config.isKerberosDebug()));
        }
        if (StringUtils.isNotBlank(config.getOauthClientId())) {
            builder.setOAuth(
                    new OAuth(config.getOauthClientId(), config.getOauthClientSecret(), config.getOauthTenant(), config.getOauthRealm()));
        }
        if ("2013".equals(config.getSharePointVersion())) {
            builder.apply2013();
            if (StringUtils.isNotBlank(config.getOauthClientId())) {
                // Every SharePoint 2013 API call in this plugin goes through
                // SharePointApi#doXmlRequest, which never calls OAuth#apply, or through
                // GetFile2013#execute, which calls the HTTP client directly and does not either.
                // No call under client2013 uses doJsonRequest, the one path that applies the
                // token. So auth.oauth.* with sp.version=2013 sends no Authorization header at
                // all, and the operator sees only unexplained 401s.
                logger.warn("auth.oauth.* is configured together with sp.version=2013, but no SharePoint 2013 request applies an OAuth"
                        + " token, so every request will be sent unauthenticated. Use auth.ntlm.* for SharePoint 2013.");
            }
        }
        if (StringUtils.isNotBlank(config.getProxyHost())) {
            if (config.getProxyPort() > 0) {
                builder.setProxyHost(config.getProxyHost()).setProxyPort(config.getProxyPort());
            } else {
                // SharePointClientBuilder#buildRoutePlanner returns null unless both are set, so
                // this combination silently sends every request direct.
                logger.warn("proxy_host is set to \"{}\" but proxy_port is not a positive number ({}), so no proxy is used at all."
                        + " Set both proxy_host and proxy_port.", config.getProxyHost(), config.getProxyPort());
            }
        }
        return builder.build();
    }

    /**
     * Builds the request configuration a crawl's HTTP client uses: connect and socket timeouts as
     * configured, plus a connection request timeout - the time a thread waits for a connection to
     * become free in the pool - bound to the same value as the connect timeout.
     *
     * <p>Left unset, Apache HttpClient's default is an unbounded wait. A pooled connection can be
     * held for a long time by a request the server is slow to answer, and every API call in this
     * plugin shares one pool per crawl (see {@code SharePointClientBuilder#buildHttpClient}, which
     * never calls {@code setMaxConnPerRoute}), so a thread waiting for one to free up had nothing
     * bounding that wait at all - it could block for the life of the crawl.
     *
     * <p>Package-private so a test can build the exact {@link RequestConfig} production uses
     * without constructing a whole {@link SharePointCrawler}.
     *
     * @param config the crawler configuration
     * @return the request configuration for {@code config}
     */
    static RequestConfig buildRequestConfig(final CrawlerConfig config) {
        return RequestConfig.custom()
                .setConnectTimeout(config.getConnectionTimeout())
                .setSocketTimeout(config.getSocketTimeout())
                .setConnectionRequestTimeout(config.getConnectionTimeout())
                .build();
    }

    /**
     * Builds the {@code include_pattern}/{@code exclude_pattern} filter once for the whole crawl,
     * using the same {@link UrlFilter} component every other {@code fess-ds-*} plugin with this
     * parameter uses.
     *
     * <p>Built once here, in the constructor, rather than fetched fresh wherever a crawl unit
     * needs to check a value: {@link UrlFilter} is prototype-scoped and backed by an in-memory
     * service keyed by session ID (see {@code UrlFilterServiceImpl}/{@code MemoryDataHelper} in
     * fess-crawler), so building and re-initializing one per file would keep appending the same
     * patterns to that session's pattern list on every call, in a map nothing clears mid-crawl.
     *
     * @param config the crawler configuration
     * @return a filter to reuse for the whole crawl, or null if neither pattern is configured or
     *         the component is unavailable (as in this plugin's own unit tests, whose container
     *         does not wire it)
     */
    private UrlFilter buildUrlFilter(final CrawlerConfig config) {
        if (StringUtils.isBlank(config.getIncludePattern()) && StringUtils.isBlank(config.getExcludePattern())) {
            return null;
        }
        final UrlFilter filter;
        try {
            filter = ComponentUtil.getComponent(UrlFilter.class);
        } catch (final ComponentNotFoundException e) {
            logger.warn("include_pattern/exclude_pattern is configured, but no UrlFilter component is available; not filtering.", e);
            return null;
        }
        if (StringUtils.isNotBlank(config.getIncludePattern())) {
            filter.addInclude(config.getIncludePattern());
        }
        if (StringUtils.isNotBlank(config.getExcludePattern())) {
            filter.addExclude(config.getExcludePattern());
        }
        filter.init(config.getSessionId());
        return filter;
    }

    private void setFirstCrawl(final CrawlerConfig crawlerConfig) {
        final Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache = new ConcurrentHashMap<>();
        // Keyed by listId, shared the same way sharePointGroupCache is: getForms() depends only
        // on the list, but is otherwise re-fetched once per list item, per file, and per
        // attachment - see ItemCrawl, FolderCrawl and ItemAttachmentsCrawl.
        final Map<String, String> formsCache = new ConcurrentHashMap<>();
        if (crawlerConfig.getInitialListId() == null && crawlerConfig.getInitialListName() == null
                && crawlerConfig.getInitialDocLibPath() == null) {
            // Shared by every SiteCrawl this crawl ever creates, including every subsite
            // discovered along the way (see SiteCrawl#crawlSubsites), so a site reachable through
            // more than one path is queued at most once. Seeded with the root's own path so a
            // subsite that lists an ancestor - or itself - as a child cannot be queued again.
            final Set<String> visitedSitePaths = ConcurrentHashMap.newKeySet();
            visitedSitePaths.add(client.getSitePath());
            crawlingQueue.offer(new SiteCrawl(client, crawlerConfig, sharePointGroupCache, formsCache, urlFilter, 0, visitedSitePaths));
        } else {
            if (crawlerConfig.getInitialListId() != null || crawlerConfig.getInitialListName() != null) {
                crawlingQueue.offer(new ListCrawl(client, crawlerConfig.getInitialListId(), crawlerConfig.getInitialListName(),
                        crawlerConfig.listItemNumPerPages, sharePointGroupCache, formsCache, crawlerConfig.isSubPage(),
                        crawlerConfig.isSkipRole(), crawlerConfig.getListContentIncludeFields(),
                        crawlerConfig.getListContentExcludeFields(), crawlerConfig.isIgnoreError(), crawlerConfig.getExtractorName(),
                        crawlerConfig.getSupportedMimeTypes(), crawlerConfig.getMaxContentLength(), urlFilter));
            }
            if (crawlerConfig.getInitialDocLibPath() != null) {
                crawlingQueue.offer(new FolderCrawl(client, crawlerConfig.getInitialDocLibPath(), crawlerConfig.isSkipRole(),
                        sharePointGroupCache, formsCache, crawlerConfig.isIgnoreError(), crawlerConfig.getExtractorName(),
                        crawlerConfig.getSupportedMimeTypes(), crawlerConfig.getMaxContentLength(), urlFilter));
            }
        }
    }

    /**
     * Checks if there are remaining targets to crawl.
     *
     * <p>With workers running this is not simply "the queue is not empty": a unit a worker has
     * already taken is in no queue at all, and neither is the document it is about to produce.
     * {@link CrawlQueue#isDrained()} covers both - it counts a unit from the moment it is queued
     * until the moment its worker is completely finished with it - and is read before
     * {@code results}, so a document put there by the last worker to finish cannot be missed: when
     * the count reaches zero every hand-off has already happened.
     *
     * @return true if there are more targets to crawl
     */
    public boolean hasCrawlTarget() {
        if (executorService == null) {
            return !crawlingQueue.isEmpty();
        }
        if (closing) {
            return false;
        }
        return !crawlingQueue.isDrained() || !results.isEmpty();
    }

    /**
     * Replaces the wait applied before retrying a 503, for a test that needs the retry loop to
     * exercise the backoff without actually waiting out its delay.
     *
     * @param backoff the backoff to use instead of the real, sleeping default
     */
    void setBackoff(final SharePointBackoff backoff) {
        this.backoff = backoff;
    }

    /**
     * Supplies the signal {@link #doCrawl} checks to decide whether the job has been asked to stop.
     *
     * @param stopRequested returns true once the crawl should stop; must be safe to call from the
     *            crawling thread while another thread flips whatever it reads
     */
    public void setStopRequested(final BooleanSupplier stopRequested) {
        this.stopRequested = stopRequested;
    }

    /**
     * Returns the configuration this crawler was built from, for a test that needs to see what
     * {@code SharePointDataStore#createCrawler} made of a set of data config parameters.
     *
     * @return the configuration passed to the constructor
     */
    CrawlerConfig getCrawlerConfig() {
        return config;
    }

    /**
     * Appends a crawl unit directly to the queue, for a test that needs {@link #doCrawl} to see a
     * particular unit's behavior without it coming from real discovery (a folder or list listing).
     *
     * @param crawl the crawl unit to enqueue
     */
    void offerCrawlTargetForTest(final SharePointCrawl crawl) {
        crawlingQueue.offer(crawl);
    }

    /**
     * Returns the number of crawl units this crawl works on at once, after the cap has been
     * applied, for a test that needs to see what a configured {@code number_of_threads} resolved
     * to.
     *
     * @return the resolved thread count, never below 1
     */
    int getNumberOfThreads() {
        return numberOfThreads;
    }

    /**
     * Returns the worker pool, or null when this crawl runs entirely on its caller's thread, for a
     * test that needs to prove the default configuration creates no pool at all.
     *
     * @return the worker pool, or null
     */
    ExecutorService getExecutorService() {
        return executorService;
    }

    /**
     * Performs a crawl operation.
     *
     * <p>This drains the queue until a crawl unit produces a document, so a single call can span
     * many crawl units and many requests - a {@code FolderCrawl} alone makes three requests per
     * file for a whole folder listing and then returns null, which counts as success here and
     * sends this loop straight to the next queue entry. The caller's own {@code alive} check only
     * runs between the documents this returns, so without the {@link #stopRequested} checks below
     * an operator's stop could not take effect until a whole queue's worth of listings had
     * finished. The checks bound that to one crawl unit: the loop stops before polling the next
     * queue entry, and a target being retried stops before waiting out a backoff and before
     * spending another attempt. A listing already in progress inside {@code SharePointCrawl#doCrawl}
     * still runs to completion - those inner loops take no stop signal.
     *
     * <p>With {@code number_of_threads} greater than 1 the queue is drained by a pool of workers
     * instead, and this method hands over the documents they finished - one per call, in the same
     * shape, so nothing above it changes. See {@link #takeFromWorkers}.
     *
     * @param dataConfig the data configuration
     * @return a pair containing the crawled data map and stats key, or null if no data
     */
    public Pair<Map<String, Object>, StatsKeyObject> doCrawl(final DataConfig dataConfig) {
        if (executorService == null) {
            return crawlOnCallersThread(dataConfig);
        }
        return takeFromWorkers(dataConfig);
    }

    /**
     * Drains the queue on the calling thread until a crawl unit produces a document - the single
     * threaded crawl, unchanged, and the only path a configuration without {@code number_of_threads}
     * ever takes.
     *
     * @param dataConfig the data configuration
     * @return a pair containing the crawled data map and stats key, or null if no data
     */
    private Pair<Map<String, Object>, StatsKeyObject> crawlOnCallersThread(final DataConfig dataConfig) {
        while (!crawlingQueue.isEmpty()) {
            if (stopRequested.getAsBoolean()) {
                logger.info("A stop was requested; leaving {} crawl target(s) uncrawled.", crawlingQueue.size());
                return null;
            }
            final SharePointCrawl crawl = crawlingQueue.poll();
            if (crawl == null) {
                continue;
            }
            try {
                final Pair<Map<String, Object>, StatsKeyObject> result = crawlUnit(dataConfig, crawl);
                if (result != null) {
                    return result;
                }
            } finally {
                crawlingQueue.finished();
            }
        }
        return null;
    }

    /**
     * Hands over the next document a worker has finished, waiting for one if the crawl is still
     * running.
     *
     * <p><b>Why the crawl cannot end early.</b> The obvious test - the queue is empty - is not
     * enough: a worker that has taken a unit but has not yet enqueued the children it discovers
     * leaves the queue legitimately empty with work still outstanding. Splitting that into "the
     * queue is empty <em>and</em> nothing is in flight" only moves the problem, because those are
     * two reads and a unit can move from one to the other between them. So {@link CrawlQueue}
     * counts a unit from the moment it is <em>queued</em> until the moment its worker is completely
     * finished with it - children enqueued (and therefore counted) and document handed over - and
     * this method tests that single count. When it reads zero, every child that will ever exist has
     * already been counted and every document that will ever be produced has already been put on
     * {@code results}, so the {@code results} check that follows it is final rather than a race.
     *
     * <p><b>Why the crawl cannot hang.</b> A worker only ever waits on the queue (with a timeout),
     * on an HTTP response (bounded by the socket timeout) or for room in {@code results} (which
     * this method is emptying, and which it stops waiting for once a stop is requested). This
     * method only ever waits for a document with a timeout, and re-tests the count each time.
     *
     * <p>Outstanding work is only worth waiting for while there is a worker left to do it, so this
     * method tests the two things that make every worker leave - {@link #closing} and
     * {@link #stopRequested} - before it waits at all. Those are the only ways a worker exits its
     * loop, so no combination of them leaves this method waiting on threads that have gone.
     *
     * @param dataConfig the data configuration
     * @return a pair containing the crawled data map and stats key, or null once the crawl is
     *         finished or has been stopped
     */
    private Pair<Map<String, Object>, StatsKeyObject> takeFromWorkers(final DataConfig dataConfig) {
        startWorkers(dataConfig);
        while (true) {
            if (closing) {
                // The workers leave on this flag, so waiting for a document after it is set is
                // waiting for a thread that has already gone: without this check a close() from
                // another thread wedges this one until the process ends, silently, with crawl
                // targets still outstanding. It is also the only reason the loop below can trust
                // that "work is outstanding" means "a worker is coming back with it".
                logger.warn("The crawler was closed while a crawl was still running; leaving {} crawl target(s) uncrawled.",
                        crawlingQueue.size());
                return null;
            }
            if (stopRequested.getAsBoolean()) {
                logger.info("A stop was requested; leaving {} crawl target(s) uncrawled.", crawlingQueue.size());
                return null;
            }
            final CrawlResult result;
            try {
                result = results.poll(POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            } catch (final InterruptedException e) {
                // Nothing in core interrupts this thread - a stop is a flag - so this is only
                // reachable if something else does. End the crawl rather than returning null to a
                // caller that would immediately ask again and get nothing, forever.
                Thread.currentThread().interrupt();
                closing = true;
                logger.warn("Interrupted while waiting for a crawled document; ending the crawl.");
                return null;
            }
            if (result != null) {
                return result.get();
            }
            if (crawlingQueue.isDrained() && results.isEmpty()) {
                return null;
            }
        }
    }

    /**
     * Starts one worker per thread, on the first call. The workers run until the crawl is stopped
     * or {@link #close()} is called; they are not restarted.
     *
     * @param dataConfig the data configuration every unit is crawled with. One crawl runs against
     *            one data config - {@code SharePointDataStore#storeData} passes the same instance
     *            on every call - so the workers capture the one they are started with.
     */
    private void startWorkers(final DataConfig dataConfig) {
        if (!workersStarted.compareAndSet(false, true)) {
            return;
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Starting {} SharePoint crawl worker(s).", numberOfThreads);
        }
        for (int i = 0; i < numberOfThreads; i++) {
            executorService.execute(() -> runWorker(dataConfig));
        }
    }

    /**
     * Takes crawl units off the queue and hands the documents they produce to {@link #doCrawl}.
     *
     * <p>The unit is only counted as finished - {@link CrawlQueue#finished()}, in the {@code
     * finally} - once its {@code doCrawl} has returned, so the children it enqueued are already
     * counted, and once its document has been handed over, so it is already visible. That ordering
     * is the whole termination argument; see {@link #takeFromWorkers}.
     *
     * @param dataConfig the data configuration
     */
    private void runWorker(final DataConfig dataConfig) {
        while (!closing && !stopRequested.getAsBoolean()) {
            final SharePointCrawl crawl;
            try {
                crawl = crawlingQueue.poll(POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (crawl == null) {
                continue;
            }
            try {
                final CrawlResult result = runUnitCatchingFailures(dataConfig, crawl);
                if (result != null) {
                    handOff(result);
                }
            } catch (final InterruptedException e) {
                // Only reachable from close()'s shutdownNow, after the crawl is already over.
                Thread.currentThread().interrupt();
                return;
            } finally {
                crawlingQueue.finished();
            }
        }
    }

    /**
     * Crawls one unit, turning anything it throws into a result the crawling thread can rethrow.
     *
     * <p>A failure must not be left on the worker: {@code SharePointDataStore#storeData} counts the
     * exceptions it catches, and a crawl that quietly loses targets without counting them is a
     * crawl that reports success and lets core delete every document it never reached. Carrying the
     * exception across and rethrowing it there keeps the accounting exactly where it already was.
     *
     * @param dataConfig the data configuration
     * @param crawl the crawl unit
     * @return the document to hand over, the failure to hand over, or null if this unit produced
     *         neither
     */
    private CrawlResult runUnitCatchingFailures(final DataConfig dataConfig, final SharePointCrawl crawl) {
        try {
            final Pair<Map<String, Object>, StatsKeyObject> document = crawlUnit(dataConfig, crawl);
            return document == null ? null : new CrawlResult(document, null);
        } catch (final RuntimeException | Error e) {
            return new CrawlResult(null, e);
        }
    }

    /**
     * Hands one finished document to the crawling thread, waiting for room if it is still working
     * through the previous ones.
     *
     * <p>Gives up on a stop rather than waiting for room that will never be made: the crawling
     * thread has already stopped draining by then. The document is dropped, which is what a stop
     * does to every target still queued behind it as well.
     *
     * <p>A result carrying a <em>failure</em> rather than a document is dropped here too, and that
     * one is not merely a target the stop did not get to: it is a target that was attempted and
     * lost. {@link #crawlUnit} throws it from its generic {@code catch} before reaching the failure
     * count, and {@link CrawlResult#get()} - which would have rethrown it on the crawling thread
     * for {@code SharePointDataStore#storeData} to count - is never called on a dropped result. So
     * nothing else has recorded it, and it is counted and logged here instead. Whether
     * {@code storeData} observes the count depends on timing: it reads {@link #getFailureCount()}
     * once the crawling thread has stopped draining, which can be before a worker still finishing
     * its last hand-off gets here. The log line is the record that does not race.
     *
     * @param result the document or failure to hand over
     * @throws InterruptedException if this worker is interrupted while waiting for room
     */
    private void handOff(final CrawlResult result) throws InterruptedException {
        while (!closing && !stopRequested.getAsBoolean()) {
            if (results.offer(result, POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                return;
            }
        }
        final Pair<Map<String, Object>, StatsKeyObject> document = result.document;
        if (document != null) {
            logger.info("Dropping the crawled document for {}: the crawl is stopping.", document.getSecond().getId());
            ComponentUtil.getCrawlerStatsHelper().done(document.getSecond());
            return;
        }
        failureCount.incrementAndGet();
        logger.warn("Dropping a failed crawl target: the crawl is stopping before its failure could be reported.", result.failure);
    }

    /**
     * Crawls one unit to completion: its retries, its backoff, its failure accounting and its
     * statistics. Runs on the crawling thread when single-threaded and on a worker otherwise, and
     * is the same code either way.
     *
     * <p>A retry stays on the thread that owns the unit - it is this loop, not a re-queue - and
     * nothing here puts a failed target back on the queue. Nothing should: a re-queued target would
     * be crawled a second time by another worker, with its own requests, its own retries and its
     * own entry in the failure count.
     *
     * <p>That is a statement about crawl <em>units</em>, not about stats keys, and the difference
     * matters. {@code Queue#poll} hands each unit to exactly one worker, but two different units
     * can carry the same {@link StatsKeyObject} id - it is built from a list's <em>name</em>, so
     * two subsites that each have a list called {@code Tasks} produce {@code item#Tasks:1} for two
     * genuinely different documents, and a list's paging can queue the same item more than once. So
     * with workers running, two threads can record against one id, and
     * {@code CrawlerStatsHelper} keeps an unsynchronized {@code LinkedHashMap} per id. <b>The
     * consequence is confined to statistics</b>: a garbled or lost statistics line, from calls that
     * are already inside a {@code catch (Exception)}. No document is affected, and neither is
     * {@link #failureCount} - the thing that decides whether stale documents may be deleted - which
     * is an {@link AtomicLong} that never goes through that helper. Do not "fix" this by
     * de-duplicating queued units on that id: because the id is not unique across sites, that would
     * silently drop the second subsite's documents, trading a cosmetic defect for data loss.
     *
     * @param dataConfig the data configuration
     * @param crawl the crawl unit
     * @return the document this unit produced, or null if it produced none
     */
    private Pair<Map<String, Object>, StatsKeyObject> crawlUnit(final DataConfig dataConfig, final SharePointCrawl crawl) {
        final CrawlerStatsHelper crawlerStatsHelper = ComponentUtil.getCrawlerStatsHelper();
        final StatsKeyObject statsKey = crawl.getStatsKey();
        crawlerStatsHelper.begin(statsKey);
        int retryCount = 0;
        boolean succeeded = false;
        RuntimeException lastFailure = null;
        while (retryCount <= config.getRetryLimit() && !stopRequested.getAsBoolean()) {
            try {
                final Map<String, Object> dataMap = crawl.doCrawl(dataConfig, crawlingQueue);
                crawlerStatsHelper.record(statsKey, StatsAction.ACCESSED);
                if (dataMap != null) {
                    return new Pair<>(dataMap, statsKey);
                }
                succeeded = true;
                break;
            } catch (final SharePointServerException e) {
                lastFailure = e;
                if (retryCount + 1 <= config.getRetryLimit()) {
                    logger.warn("Api server error: {}  [Retry:{}]", e.getMessage(), retryCount);
                    // SharePoint's on-premises throttling signal: wait longer with each
                    // successive retry of this same target rather than hammering a server that
                    // just said it is overloaded. Only a genuine 503 waits - a 404 or 403
                    // retrying anyway is not evidence of load, so it is not worth delaying.
                    if (e.getStatusCode() == 503) {
                        awaitUnlessStopping(retryCount);
                    }
                } else {
                    logger.warn("Api server error: {}", e.getMessage(), e);
                }
            } catch (final SharePointClientException e) {
                lastFailure = e;
                if (retryCount + 1 <= config.getRetryLimit()) {
                    logger.warn("Error occured: {}  [Retry:{}]", e.getMessage(), retryCount);
                    // GetFile/GetFile2013 report every HTTP error this way instead of as a
                    // SharePointServerException, but still carry the status code (see
                    // SharePointClientException(String, int)), so a 503 from a file download
                    // backs off exactly like a 503 from any other API call.
                    if (e.getStatusCode() == 503) {
                        awaitUnlessStopping(retryCount);
                    }
                } else {
                    logger.warn("Error occured. {}", e.getMessage(), e);
                }
            } catch (final Exception e) {
                crawlerStatsHelper.discard(statsKey);
                throw new DataStoreCrawlingException(statsKey.getId(), "Failed to crawl " + statsKey.getId(), e);
            }
            retryCount++;
            crawlerStatsHelper.record(statsKey, StatsAction.EXCEPTION.name().toLowerCase(Locale.ENGLISH) + "@" + retryCount);
        }
        if (!succeeded) {
            // Losing this target loses every document it would have produced. Count it so the
            // crawl can decline to delete stale documents, and record it so an operator can
            // find it in the failure URL list instead of only in the log.
            failureCount.incrementAndGet();
            if (retryCount == 0 && lastFailure == null && stopRequested.getAsBoolean()) {
                // The retry loop's own condition is the first stop check made after this
                // target was polled, so a stop requested in the window between the queue
                // check above and that condition leaves retryCount at 0 and lastFailure null
                // without a single attempt having been made.
                logger.warn("Gave up on {}: a stop was requested before it could be attempted.", statsKey.getId());
            } else {
                logger.warn("Gave up on {} after {} attempt(s).", statsKey.getId(), retryCount);
            }
            if (lastFailure != null) {
                storeFailureUrl(dataConfig, statsKey, lastFailure);
            }
        }
        crawlerStatsHelper.done(statsKey);
        return null;
    }

    /**
     * Waits out the backoff for the given retry, unless the job has been asked to stop.
     *
     * <p>The wait is up to about 38.7 seconds by default - the backoff's 30-second cap is applied
     * before jitter, which can scale it up to 129% - and the crawling thread is never interrupted,
     * so sitting it out after a stop is exactly the delay an operator experiences as the stop
     * button doing nothing. Skipping it does not by itself end the retry loop; the same flag in
     * that loop's own condition does, on the next check.
     *
     * @param retryCount the zero-based retry this wait precedes
     */
    private void awaitUnlessStopping(final int retryCount) {
        if (stopRequested.getAsBoolean()) {
            return;
        }
        backoff.await(retryCount);
    }

    /**
     * Records a crawl unit lost to exhausted retries through the failure URL service, so an
     * operator can find it in the admin UI instead of only in the log.
     *
     * <p>The value stored in that record's URL column is the stats key's id, which is not a URL:
     * every {@code SharePointCrawl} builds it as {@code <type>#<identifier>}, so it reads as
     * {@code file#/sites/x/a.pdf} or {@code folder#/sites/x/docs} for the paths that have a
     * server-relative URL to name, and as {@code site#mysite}, {@code list#Tasks:<list id>},
     * {@code item#Tasks:5} or {@code item_attachment#Tasks:5} for the paths that do not. It
     * identifies the lost crawl unit; it is not something that can be opened or re-crawled as a
     * URL. {@code SharePointDataStore#storeData} stores its own failure records for the exceptions
     * that escape this method, and those carry a third and fourth shape - see that method.
     *
     * @param dataConfig the data configuration being crawled
     * @param statsKey identifies the crawl unit that was given up on
     * @param lastFailure the exception from the final retry attempt
     */
    private void storeFailureUrl(final DataConfig dataConfig, final StatsKeyObject statsKey, final RuntimeException lastFailure) {
        ComponentUtil.getComponent(FailureUrlService.class)
                .store(dataConfig, lastFailure.getClass().getCanonicalName(), statsKey.getId(), lastFailure);
    }

    /**
     * Returns how many crawl units were given up on after exhausting their retries.
     *
     * <p>Counts the units the retry loop in {@link #crawlUnit} abandoned, which are the ones
     * that produce no document and let the crawl continue to the next queue entry. A unit that
     * fails with an exception the retry loop does not retry is normally not counted here, because
     * that exception leaves {@link #crawlUnit} before this counter is reached - the caller counts
     * that one where it catches it, so the two together cover every lost unit. The one exception
     * is a worker's failure dropped by {@link #handOff} while the crawl is stopping, which the
     * caller never gets to see and which is therefore counted here instead.
     *
     * @return the number of crawl units that were given up on
     */
    public long getFailureCount() {
        return failureCount.get();
    }

    /**
     * Stops the workers and releases the HTTP connection pool held by the underlying SharePoint
     * client.
     *
     * <p>In that order, and not the other way round: the workers make their requests through that
     * client - including through the sibling clients a subsite crawl builds from it, which share
     * its connection pool - so closing it first would pull the pool out from under a request still
     * in flight.
     *
     * @throws IOException if the client fails to close
     */
    @Override
    public void close() throws IOException {
        try {
            shutdownWorkers();
        } finally {
            // In a finally so that a failure while stopping the workers cannot leak the connection
            // pool - a leaked pool holds its connections for the life of the JVM, and one crawl
            // per data config per run adds up. By the time this runs the workers have been waited
            // for twice; see shutdownWorkers.
            client.close();
        }
    }

    /**
     * Asks the workers to finish, waits a bounded time for them, interrupts whatever is left, and
     * waits again.
     *
     * <p>The second wait is the point: an interrupt does not stop a thread that is inside a
     * blocking read on a socket, and the caller releases the connection pool the moment this
     * returns. Returning on {@code shutdownNow()} alone would hand that pool to
     * {@code client.close()} while a worker was still reading from it.
     */
    private void shutdownWorkers() {
        if (executorService == null) {
            return;
        }
        // Set before the shutdown so a worker waiting on the queue or for room in results leaves
        // on its own, rather than having to be interrupted out of an HTTP request.
        closing = true;
        executorService.shutdown();
        if (awaitWorkers()) {
            return;
        }
        logger.warn("The SharePoint crawl workers did not finish within {} seconds; interrupting them.", EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS);
        executorService.shutdownNow();
        if (!awaitWorkers()) {
            logger.warn("A SharePoint crawl worker is still running {} seconds after being interrupted; releasing its connection pool"
                    + " anyway. Requests it has in flight will fail.", EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS);
        }
    }

    /**
     * Waits a bounded time for every worker to finish.
     *
     * @return true if they all finished, false on the timeout or on an interrupt
     */
    private boolean awaitWorkers() {
        try {
            return executorService.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while waiting for the SharePoint crawl workers to finish.", e);
            return false;
        }
    }

    /**
     * The crawl queue, and the count of how much work is outstanding.
     *
     * <p>A unit is counted from the moment it is <em>offered</em> - by the initial seeding, or by
     * another unit's discovery - until {@link #finished()} says its worker is completely done with
     * it. That is deliberately wider than "is in the queue": between a worker taking a unit and
     * that unit enqueuing what it discovered, the queue is empty and the crawl is anything but
     * finished. Because both facts live in one counter, {@link #isDrained()} is a single read and
     * cannot see a unit that is momentarily in neither place.
     *
     * <p>{@code AbstractQueue} rather than a bare field so that a crawl unit, which is handed this
     * queue to enqueue what it discovers, counts its children without knowing anything about the
     * counter.
     */
    private static final class CrawlQueue extends AbstractQueue<SharePointCrawl> {
        private final LinkedBlockingQueue<SharePointCrawl> delegate = new LinkedBlockingQueue<>();

        private final AtomicInteger outstanding = new AtomicInteger();

        @Override
        public boolean offer(final SharePointCrawl crawl) {
            outstanding.incrementAndGet();
            if (delegate.offer(crawl)) {
                return true;
            }
            outstanding.decrementAndGet();
            return false;
        }

        @Override
        public SharePointCrawl poll() {
            return delegate.poll();
        }

        /**
         * Waits a bounded time for a crawl unit, so a worker with nothing to do neither spins nor
         * misses a stop.
         *
         * @param timeout how long to wait
         * @param unit the unit of {@code timeout}
         * @return the next crawl unit, or null if none arrived in time
         * @throws InterruptedException if the waiting thread is interrupted
         */
        public SharePointCrawl poll(final long timeout, final TimeUnit unit) throws InterruptedException {
            return delegate.poll(timeout, unit);
        }

        @Override
        public SharePointCrawl peek() {
            return delegate.peek();
        }

        @Override
        public Iterator<SharePointCrawl> iterator() {
            return delegate.iterator();
        }

        @Override
        public int size() {
            return delegate.size();
        }

        /**
         * Records that one crawl unit is completely finished with: whatever it discovered has been
         * enqueued (and so counted here) and whatever document it produced has been handed over.
         */
        public void finished() {
            outstanding.decrementAndGet();
        }

        /**
         * Returns whether nothing is queued and nothing is being worked on.
         *
         * @return true if no crawl unit is outstanding
         */
        public boolean isDrained() {
            return outstanding.get() == 0;
        }
    }

    /**
     * What a worker hands to the crawling thread: either a crawled document or the failure that
     * came of trying.
     */
    private static final class CrawlResult {
        private final Pair<Map<String, Object>, StatsKeyObject> document;

        private final Throwable failure;

        CrawlResult(final Pair<Map<String, Object>, StatsKeyObject> document, final Throwable failure) {
            this.document = document;
            this.failure = failure;
        }

        /**
         * Returns the document, or rethrows the worker's failure on the crawling thread so the
         * caller handles it exactly where it has always handled one.
         *
         * @return the crawled document
         */
        Pair<Map<String, Object>, StatsKeyObject> get() {
            if (failure instanceof final RuntimeException e) {
                throw e;
            }
            if (failure instanceof final Error e) {
                throw e;
            }
            return document;
        }
    }

    /**
     * Configuration class for SharePointCrawler.
     */
    public static class CrawlerConfig {
        /**
         * Creates a new CrawlerConfig instance with default settings.
         */
        public CrawlerConfig() {
            // default constructor
        }

        private String url = null;
        private String siteName = null;
        private String sitePath = null;
        private String initialListId = null;
        private String initialListName = null;
        private String initialDocLibPath = null;
        private String ntlmUser = null;
        private String ntlmPassword = null;
        private String ntlmDomain = null;
        private String ntlmWorkstation = null;
        private String kerberosPrincipal = null;
        private String kerberosKeytab = null;
        private String kerberosPassword = null;
        private String kerberosKrb5Conf = null;
        private boolean kerberosStripPort = true;
        private boolean kerberosUseCanonicalHostname = false;
        private boolean kerberosDebug = false;
        private String oauthClientId = null;
        private String oauthClientSecret = null;
        private String oauthTenant = null;
        private String oauthRealm = null;
        private int connectionTimeout = 30000;
        private int socketTimeout = 30000;
        private int listItemNumPerPages = 100;
        private String sharePointVersion = null;
        private int retryLimit = 2;
        private boolean isSubPage = false;
        private List<String> listContentIncludeFields = new ArrayList<>();
        private List<String> listContentExcludeFields = new ArrayList<>();
        private boolean skipRole = false;
        private List<String> excludeList = new ArrayList<>();
        private List<String> excludeFolder = new ArrayList<>();
        private boolean ignoreError = false;
        private String proxyHost = null;
        private int proxyPort = -1;
        private String extractorName = FileCrawl.DEFAULT_EXTRACTOR_NAME;
        private String[] supportedMimeTypes = FileCrawl.DEFAULT_SUPPORTED_MIMETYPES;
        private long maxContentLength = FileCrawl.DEFAULT_MAX_CONTENT_LENGTH;
        private String includePattern = null;
        private String excludePattern = null;
        private String sessionId = null;
        private boolean crawlSubsites = false;
        private int maxDepth = 10;
        private int numberOfThreads = 1;

        /**
         * Returns the SharePoint server URL.
         *
         * @return the URL
         */
        public String getUrl() {
            return url;
        }

        /**
         * Sets the SharePoint server URL.
         *
         * @param url the URL
         */
        public void setUrl(final String url) {
            this.url = url.endsWith("/") ? url : url + "/";
        }

        /**
         * Returns the site name.
         *
         * @return the site name
         */
        public String getSiteName() {
            return siteName;
        }

        /**
         * Sets the site name.
         *
         * @param siteName the site name
         */
        public void setSiteName(final String siteName) {
            this.siteName = siteName;
        }

        /**
         * Sets the server-relative managed path of the site, such as {@code /teams/eng} or
         * {@code /} for the root site collection. Overrides the {@code /sites/<siteName>} path
         * this connector builds by default; leaving this unset keeps that default exactly.
         *
         * @param sitePath the server-relative site path
         */
        public void setSitePath(final String sitePath) {
            this.sitePath = sitePath;
        }

        /**
         * Returns the server-relative path of the site being crawled, normalized to start and end
         * with a slash. This is the single place the connector decides what that path is.
         *
         * @return the normalized, server-relative site path
         */
        public String getSiteRelativePath() {
            return SharePointClient.normalizeSitePath(sitePath, siteName);
        }

        /**
         * Returns the initial list ID.
         *
         * @return the list ID
         */
        public String getInitialListId() {
            return initialListId;
        }

        /**
         * Sets the initial list ID.
         *
         * @param initialListId the list ID
         */
        public void setInitialListId(final String initialListId) {
            this.initialListId = initialListId;
        }

        /**
         * Returns the initial list name.
         *
         * @return the list name
         */
        public String getInitialListName() {
            return initialListName;
        }

        /**
         * Sets the initial list name.
         *
         * @param initialListName the list name
         */
        public void setInitialListName(final String initialListName) {
            this.initialListName = initialListName;
        }

        /**
         * Returns the initial document library path.
         *
         * @return the document library path
         */
        public String getInitialDocLibPath() {
            if (initialDocLibPath == null) {
                return null;
            }
            final String base = getSiteRelativePath();
            // base ends with a slash; initialDocLibPath begins with one.
            return base.substring(0, base.length() - 1) + initialDocLibPath;
        }

        /**
         * Sets the initial document library path.
         *
         * @param initialDocLibPath the path
         */
        public void setInitialDocLibPath(final String initialDocLibPath) {
            this.initialDocLibPath = initialDocLibPath.startsWith("/") ? initialDocLibPath : "/" + initialDocLibPath;
        }

        /**
         * Returns the NTLM username.
         *
         * @return the username
         */
        public String getNtlmUser() {
            return ntlmUser;
        }

        /**
         * Sets the NTLM username.
         *
         * @param ntlmUser the username
         */
        public void setNtlmUser(final String ntlmUser) {
            this.ntlmUser = ntlmUser;
        }

        /**
         * Returns the NTLM password.
         *
         * @return the password
         */
        public String getNtlmPassword() {
            return ntlmPassword;
        }

        /**
         * Sets the NTLM password.
         *
         * @param ntlmPassword the password
         */
        public void setNtlmPassword(final String ntlmPassword) {
            this.ntlmPassword = ntlmPassword;
        }

        /**
         * Returns the NTLM domain.
         *
         * @return the domain, or null when none is configured
         */
        public String getNtlmDomain() {
            return ntlmDomain;
        }

        /**
         * Sets the NTLM domain, sent as its own field of the NTLM negotiation.
         *
         * <p>Left unset the credential is built exactly as before, so an installation that writes
         * the domain into the user name instead - as <code>DOMAIN&#92;user</code> - keeps sending
         * exactly what it sends today. {@link org.apache.http.auth.NTCredentials} does not split
         * that string; it passes the whole thing through as the user name, so whether it is
         * accepted is up to the server.
         *
         * @param ntlmDomain the domain
         */
        public void setNtlmDomain(final String ntlmDomain) {
            this.ntlmDomain = ntlmDomain;
        }

        /**
         * Returns the NTLM workstation name.
         *
         * @return the workstation name, or null when none is configured
         */
        public String getNtlmWorkstation() {
            return ntlmWorkstation;
        }

        /**
         * Sets the NTLM workstation name - the {@code hostName} argument of
         * {@link org.apache.http.auth.NTCredentials}. Left unset, the credential is built exactly
         * as before.
         *
         * @param ntlmWorkstation the workstation name
         */
        public void setNtlmWorkstation(final String ntlmWorkstation) {
            this.ntlmWorkstation = ntlmWorkstation;
        }

        /**
         * Returns the Kerberos client principal.
         *
         * @return the principal, or null when Kerberos is not configured
         */
        public String getKerberosPrincipal() {
            return kerberosPrincipal;
        }

        /**
         * Sets the Kerberos client principal. Setting it is what enables Kerberos; write it as
         * {@code user@REALM} so it does not depend on the JVM-global default realm.
         *
         * @param kerberosPrincipal the principal
         */
        public void setKerberosPrincipal(final String kerberosPrincipal) {
            this.kerberosPrincipal = kerberosPrincipal;
        }

        /**
         * Returns the path to the Kerberos keytab.
         *
         * @return the keytab path, or null when a password is used instead
         */
        public String getKerberosKeytab() {
            return kerberosKeytab;
        }

        /**
         * Sets the path to a keytab holding a key for the principal. Mutually exclusive with the
         * password: a configured keytab wins.
         *
         * @param kerberosKeytab the keytab path
         */
        public void setKerberosKeytab(final String kerberosKeytab) {
            this.kerberosKeytab = kerberosKeytab;
        }

        /**
         * Returns the Kerberos password.
         *
         * @return the password, or null when a keytab is used instead
         */
        public String getKerberosPassword() {
            return kerberosPassword;
        }

        /**
         * Sets the Kerberos principal's password, used only when no keytab is configured.
         *
         * @param kerberosPassword the password
         */
        public void setKerberosPassword(final String kerberosPassword) {
            this.kerberosPassword = kerberosPassword;
        }

        /**
         * Returns the configured krb5.conf path.
         *
         * @return the path, or null when none is configured
         */
        public String getKerberosKrb5Conf() {
            return kerberosKrb5Conf;
        }

        /**
         * Sets the krb5.conf to point {@code java.security.krb5.conf} at, and only when nothing
         * has set that property yet - it is JVM-global and one crawler JVM runs every data config
         * of a crawl job.
         *
         * @param kerberosKrb5Conf the krb5.conf path
         */
        public void setKerberosKrb5Conf(final String kerberosKrb5Conf) {
            this.kerberosKrb5Conf = kerberosKrb5Conf;
        }

        /**
         * Returns whether the port is stripped from the service principal name.
         *
         * @return true if the port is stripped
         */
        public boolean isKerberosStripPort() {
            return kerberosStripPort;
        }

        /**
         * Sets whether the port is stripped from the service principal name. Defaults to true,
         * matching Apache HttpClient.
         *
         * @param kerberosStripPort whether to strip the port
         */
        public void setKerberosStripPort(final boolean kerberosStripPort) {
            this.kerberosStripPort = kerberosStripPort;
        }

        /**
         * Returns whether the target host is resolved to its canonical name for the service
         * principal name.
         *
         * @return true if the canonical host name is used
         */
        public boolean isKerberosUseCanonicalHostname() {
            return kerberosUseCanonicalHostname;
        }

        /**
         * Sets whether the target host is resolved to its canonical name for the service principal
         * name. Defaults to false, deliberately unlike Apache HttpClient's own default: reverse
         * DNS behind alternate access mappings or a load balancer resolves to a name no SPN is
         * registered for, and the resulting failure says nothing about DNS.
         *
         * @param kerberosUseCanonicalHostname whether to use the canonical host name
         */
        public void setKerberosUseCanonicalHostname(final boolean kerberosUseCanonicalHostname) {
            this.kerberosUseCanonicalHostname = kerberosUseCanonicalHostname;
        }

        /**
         * Returns whether {@code Krb5LoginModule} debug output is enabled.
         *
         * @return true if debug output is enabled
         */
        public boolean isKerberosDebug() {
            return kerberosDebug;
        }

        /**
         * Sets whether {@code Krb5LoginModule} writes its debug output to standard output.
         *
         * @param kerberosDebug whether to enable debug output
         */
        public void setKerberosDebug(final boolean kerberosDebug) {
            this.kerberosDebug = kerberosDebug;
        }

        /**
         * Returns the OAuth client ID.
         *
         * @return the client ID
         */
        public String getOauthClientId() {
            return oauthClientId;
        }

        /**
         * Sets the OAuth client ID.
         *
         * @param oauthClientId the client ID
         */
        public void setOauthClientId(final String oauthClientId) {
            this.oauthClientId = oauthClientId;
        }

        /**
         * Returns the OAuth client secret.
         *
         * @return the client secret
         */
        public String getOauthClientSecret() {
            return oauthClientSecret;
        }

        /**
         * Sets the OAuth client secret.
         *
         * @param oauthClientSecret the client secret
         */
        public void setOauthClientSecret(final String oauthClientSecret) {
            this.oauthClientSecret = oauthClientSecret;
        }

        /**
         * Returns the OAuth tenant.
         *
         * @return the tenant
         */
        public String getOauthTenant() {
            return oauthTenant;
        }

        /**
         * Sets the OAuth tenant.
         *
         * @param oauthTenant the tenant
         */
        public void setOauthTenant(final String oauthTenant) {
            this.oauthTenant = oauthTenant;
        }

        /**
         * Returns the OAuth realm.
         *
         * @return the realm
         */
        public String getOauthRealm() {
            return oauthRealm;
        }

        /**
         * Sets the OAuth realm.
         *
         * @param oauthRealm the realm
         */
        public void setOauthRealm(final String oauthRealm) {
            this.oauthRealm = oauthRealm;
        }

        /**
         * Returns the connection timeout in milliseconds.
         *
         * @return the timeout
         */
        public int getConnectionTimeout() {
            return connectionTimeout;
        }

        /**
         * Sets the connection timeout.
         *
         * @param connectionTimeout timeout in ms
         */
        public void setConnectionTimeout(final int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        /**
         * Returns the socket timeout in milliseconds.
         *
         * @return the timeout
         */
        public int getSocketTimeout() {
            return socketTimeout;
        }

        /**
         * Sets the socket timeout.
         *
         * @param socketTimeout timeout in ms
         */
        public void setSocketTimeout(final int socketTimeout) {
            this.socketTimeout = socketTimeout;
        }

        /**
         * Returns the number of list items per page.
         *
         * @return items per page
         */
        public int getListItemNumPerPages() {
            return listItemNumPerPages;
        }

        /**
         * Sets the number of list items per page.
         *
         * @param listItemNumPerPages items
         */
        public void setListItemNumPerPages(final int listItemNumPerPages) {
            this.listItemNumPerPages = listItemNumPerPages;
        }

        /**
         * Returns the SharePoint version.
         *
         * @return the version
         */
        public String getSharePointVersion() {
            return sharePointVersion;
        }

        /**
         * Sets the SharePoint version.
         *
         * @param sharePointVersion the version
         */
        public void setSharePointVersion(final String sharePointVersion) {
            this.sharePointVersion = sharePointVersion;
        }

        /**
         * Returns the retry limit.
         *
         * @return the limit
         */
        public int getRetryLimit() {
            return retryLimit;
        }

        /**
         * Sets the retry limit.
         *
         * @param retryLimit the limit
         */
        public void setRetryLimit(final int retryLimit) {
            this.retryLimit = retryLimit;
        }

        /**
         * Returns whether to crawl sub pages.
         *
         * @return true if enabled
         */
        public boolean isSubPage() {
            return isSubPage;
        }

        /**
         * Sets whether to crawl sub pages.
         *
         * @param subPage true to enable
         */
        public void setSubPage(final boolean subPage) {
            isSubPage = subPage;
        }

        /**
         * Returns the fields to include.
         *
         * @return the field list
         */
        public List<String> getListContentIncludeFields() {
            return listContentIncludeFields;
        }

        /**
         * Sets the fields to include.
         *
         * @param listContentIncludeFields fields
         */
        public void setListContentIncludeFields(final String listContentIncludeFields) {
            this.listContentIncludeFields = Arrays.asList(listContentIncludeFields.trim().split(","));
        }

        /**
         * Returns the fields to exclude.
         *
         * @return the field list
         */
        public List<String> getListContentExcludeFields() {
            return listContentExcludeFields;
        }

        /**
         * Sets the fields to exclude.
         *
         * @param listContentExcludeFields fields
         */
        public void setListContentExcludeFields(final String listContentExcludeFields) {
            this.listContentExcludeFields = Arrays.asList(listContentExcludeFields.trim().split(","));
        }

        /**
         * Returns the lists to exclude.
         *
         * @return the list names
         */
        public List<String> getExcludeList() {
            return excludeList;
        }

        /**
         * Sets the lists to exclude.
         *
         * @param excludeList the list names
         */
        public void setExcludeList(final String excludeList) {
            this.excludeList = Arrays.asList(excludeList.split(","));
        }

        /**
         * Returns the folders to exclude.
         *
         * @return the folder names
         */
        public List<String> getExcludeFolder() {
            return excludeFolder;
        }

        /**
         * Sets the folders to exclude.
         *
         * @param excludeFolder the folder names
         */
        public void setExcludeFolder(final String excludeFolder) {
            this.excludeFolder = Arrays.asList(excludeFolder.split(","));
        }

        /**
         * Returns whether to skip role fetching.
         *
         * @return true if skipping
         */
        public boolean isSkipRole() {
            return skipRole;
        }

        /**
         * Sets whether to skip role fetching.
         *
         * @param skipRole true to skip
         */
        public void setSkipRole(final boolean skipRole) {
            this.skipRole = skipRole;
        }

        /**
         * Returns whether a file's content extraction failure is logged instead of failing its
         * crawl target.
         *
         * @return true if such a failure is logged instead of thrown
         */
        public boolean isIgnoreError() {
            return ignoreError;
        }

        /**
         * Sets whether a file's content extraction failure is logged instead of failing its crawl
         * target.
         *
         * @param ignoreError true to log such a failure instead of throwing
         */
        public void setIgnoreError(final boolean ignoreError) {
            this.ignoreError = ignoreError;
        }

        /**
         * Returns the HTTP proxy host to route requests through.
         *
         * @return the proxy host, or null when no proxy is configured
         */
        public String getProxyHost() {
            return proxyHost;
        }

        /**
         * Sets the HTTP proxy host to route requests through.
         *
         * @param proxyHost the proxy host
         */
        public void setProxyHost(final String proxyHost) {
            this.proxyHost = proxyHost;
        }

        /**
         * Returns the HTTP proxy port to route requests through.
         *
         * @return the proxy port
         */
        public int getProxyPort() {
            return proxyPort;
        }

        /**
         * Sets the HTTP proxy port to route requests through.
         *
         * @param proxyPort the proxy port
         */
        public void setProxyPort(final int proxyPort) {
            this.proxyPort = proxyPort;
        }

        /**
         * Returns the name of the extractor component used to extract a file's content.
         *
         * @return the extractor component name
         */
        public String getExtractorName() {
            return extractorName;
        }

        /**
         * Sets the name of the extractor component used to extract a file's content.
         *
         * @param extractorName the extractor component name
         */
        public void setExtractorName(final String extractorName) {
            this.extractorName = extractorName;
        }

        /**
         * Returns the patterns a file's MIME type must match at least one of to be crawled.
         *
         * @return the MIME type patterns
         */
        public String[] getSupportedMimeTypes() {
            return supportedMimeTypes;
        }

        /**
         * Sets the patterns a file's MIME type must match at least one of to be crawled. A blank
         * value is treated as unset rather than split into a single empty-string pattern, which
         * would match nothing and silently skip every file.
         *
         * @param supportedMimeTypes comma-separated regular expressions
         */
        public void setSupportedMimeTypes(final String supportedMimeTypes) {
            if (StringUtils.isBlank(supportedMimeTypes)) {
                this.supportedMimeTypes = FileCrawl.DEFAULT_SUPPORTED_MIMETYPES;
                return;
            }
            this.supportedMimeTypes = Arrays.stream(supportedMimeTypes.split(",")).map(String::trim).toArray(String[]::new);
        }

        /**
         * Returns the maximum file size in bytes.
         *
         * @return the maximum size, or a negative number for no limit
         */
        public long getMaxContentLength() {
            return maxContentLength;
        }

        /**
         * Sets the maximum file size in bytes.
         *
         * @param maxContentLength the maximum size, or a negative number for no limit
         */
        public void setMaxContentLength(final long maxContentLength) {
            this.maxContentLength = maxContentLength;
        }

        /**
         * Returns the regular expression a crawled item's URL-ish value must match to be crawled.
         *
         * @return the include pattern, or null/blank if unset
         */
        public String getIncludePattern() {
            return includePattern;
        }

        /**
         * Sets the regular expression a crawled item's URL-ish value must match to be crawled.
         *
         * @param includePattern the include pattern
         */
        public void setIncludePattern(final String includePattern) {
            this.includePattern = includePattern;
        }

        /**
         * Returns the regular expression that excludes a crawled item from being crawled.
         *
         * @return the exclude pattern, or null/blank if unset
         */
        public String getExcludePattern() {
            return excludePattern;
        }

        /**
         * Sets the regular expression that excludes a crawled item from being crawled.
         *
         * @param excludePattern the exclude pattern
         */
        public void setExcludePattern(final String excludePattern) {
            this.excludePattern = excludePattern;
        }

        /**
         * Returns the crawling session ID {@link org.codelibs.fess.crawler.filter.UrlFilter} is
         * initialized with.
         *
         * @return the session ID, or null/blank if this crawl has none
         */
        public String getSessionId() {
            return sessionId;
        }

        /**
         * Sets the crawling session ID {@link org.codelibs.fess.crawler.filter.UrlFilter} is
         * initialized with.
         *
         * @param sessionId the session ID
         */
        public void setSessionId(final String sessionId) {
            this.sessionId = sessionId;
        }

        /**
         * Returns whether a site crawl recurses into its subsites, discovered via
         * {@code _api/web/webinfos}.
         *
         * @return true if subsite recursion is enabled
         */
        public boolean isCrawlSubsites() {
            return crawlSubsites;
        }

        /**
         * Sets whether a site crawl recurses into its subsites.
         *
         * @param crawlSubsites true to enable subsite recursion
         */
        public void setCrawlSubsites(final boolean crawlSubsites) {
            this.crawlSubsites = crawlSubsites;
        }

        /**
         * Returns how many subsite hops below the root site {@link #isCrawlSubsites()} may
         * recurse. The root site itself is depth 0, so a value of 1 crawls the root's direct
         * children and no further.
         *
         * @return the maximum subsite recursion depth
         */
        public int getMaxDepth() {
            return maxDepth;
        }

        /**
         * Sets how many subsite hops below the root site {@link #isCrawlSubsites()} may recurse.
         *
         * @param maxDepth the maximum subsite recursion depth
         */
        public void setMaxDepth(final int maxDepth) {
            this.maxDepth = maxDepth;
        }

        /**
         * Returns how many crawl targets are worked on at once. The default, {@code 1}, is the
         * single-threaded crawl this connector has always run: no thread pool is created at all.
         *
         * @return the configured number of threads
         */
        public int getNumberOfThreads() {
            return numberOfThreads;
        }

        /**
         * Sets how many crawl targets are worked on at once. The value is capped at twice the
         * processor count when the crawler is built.
         *
         * @param numberOfThreads the number of threads
         */
        public void setNumberOfThreads(final int numberOfThreads) {
            this.numberOfThreads = numberOfThreads;
        }
    }
}
