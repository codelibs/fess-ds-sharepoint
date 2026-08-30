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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
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

    private final SharePointClient client;

    private final ConcurrentLinkedQueue<SharePointCrawl> crawlingQueue = new ConcurrentLinkedQueue<>();

    private final CrawlerConfig config;

    private final AtomicLong failureCount = new AtomicLong();

    private final UrlFilter urlFilter;

    /**
     * The wait applied before retrying a request SharePoint answered with 503, growing with each
     * successive retry of the same crawl unit. Package-private so a test can replace it with one
     * that records the delay instead of actually sleeping for it.
     */
    private SharePointBackoff backoff = SharePointBackoff.defaults();

    /**
     * Answers whether the job this crawl belongs to has been asked to stop.
     *
     * <p>Core's stop is a flag, not an interrupt: {@code AbstractDataStore#stop} clears
     * {@code alive} and nothing interrupts the crawling thread. {@code SharePointDataStore}
     * supplies a supplier reading that flag, so the queue loop below can notice it too - see
     * {@link #doCrawl} for the window that remains. Defaults to "never stop" so a crawler built
     * outside {@code SharePointDataStore} behaves exactly as it did before.
     */
    private BooleanSupplier stopRequested = () -> false;

    /**
     * Creates a new SharePointCrawler with the specified configuration.
     *
     * @param config the crawler configuration
     */
    public SharePointCrawler(final CrawlerConfig config) {
        validate(config);
        this.client = createClient(config);
        this.config = config;
        this.urlFilter = buildUrlFilter(config);
        setFirstCrawl(config);
        if (crawlingQueue.isEmpty()) {
            logger.error("Failed to start crawl.");
        }
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
    }

    private SharePointClient createClient(final CrawlerConfig config) {
        final SharePointClientBuilder builder = SharePointClient.builder()
                .setUrl(config.getUrl())
                .setSite(config.getSiteName())
                .setSitePath(config.getSiteRelativePath())
                .setRequestConfig(buildRequestConfig(config));
        final String ntlmUser = config.getNtlmUser();
        if (StringUtils.isNotBlank(ntlmUser)) {
            final String ntlmPass = config.getNtlmPassword();
            builder.setCredential(new NtlmCredential(ntlmUser, ntlmPass, null, null));
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
            crawlingQueue.offer(new SiteCrawl(client, crawlerConfig, sharePointGroupCache, formsCache, urlFilter));
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
     * @return true if there are more targets to crawl
     */
    public boolean hasCrawlTarget() {
        return !crawlingQueue.isEmpty();
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
     * @param dataConfig the data configuration
     * @return a pair containing the crawled data map and stats key, or null if no data
     */
    public Pair<Map<String, Object>, StatsKeyObject> doCrawl(final DataConfig dataConfig) {
        final CrawlerStatsHelper crawlerStatsHelper = ComponentUtil.getCrawlerStatsHelper();
        while (!crawlingQueue.isEmpty()) {
            if (stopRequested.getAsBoolean()) {
                logger.info("A stop was requested; leaving {} crawl target(s) uncrawled.", crawlingQueue.size());
                return null;
            }
            final SharePointCrawl crawl = crawlingQueue.poll();
            if (crawl == null) {
                continue;
            }
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
        }
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
     * <p>Counts only the units the retry loop in {@link #doCrawl} abandoned, which are the ones
     * that produce no document and let the crawl continue to the next queue entry. A unit that
     * fails with an exception the retry loop does not retry is not counted here, because that
     * exception leaves {@link #doCrawl} before this counter is reached - the caller counts that
     * one where it catches it, so the two together cover every lost unit.
     *
     * @return the number of crawl units that were given up on
     */
    public long getFailureCount() {
        return failureCount.get();
    }

    /**
     * Releases the HTTP connection pool held by the underlying SharePoint client.
     *
     * @throws IOException if the client fails to close
     */
    @Override
    public void close() throws IOException {
        client.close();
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
    }
}
