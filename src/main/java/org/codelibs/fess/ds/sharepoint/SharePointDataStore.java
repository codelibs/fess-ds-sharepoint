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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.core.misc.Pair;
import org.codelibs.fess.Constants;
import org.codelibs.fess.app.service.FailureUrlService;
import org.codelibs.fess.crawler.exception.CrawlingAccessException;
import org.codelibs.fess.crawler.exception.MultipleCrawlingAccessException;
import org.codelibs.fess.ds.AbstractDataStore;
import org.codelibs.fess.ds.callback.IndexUpdateCallback;
import org.codelibs.fess.ds.sharepoint.crawl.file.FileCrawl;
import org.codelibs.fess.entity.DataStoreParams;
import org.codelibs.fess.exception.DataStoreCrawlingException;
import org.codelibs.fess.helper.CrawlerStatsHelper;
import org.codelibs.fess.helper.CrawlerStatsHelper.StatsAction;
import org.codelibs.fess.helper.CrawlerStatsHelper.StatsKeyObject;
import org.codelibs.fess.helper.PermissionHelper;
import org.codelibs.fess.mylasta.direction.FessConfig;
import org.codelibs.fess.opensearch.config.exentity.DataConfig;
import org.codelibs.fess.util.ComponentUtil;

/**
 * DataStore implementation for crawling SharePoint sites.
 */
public class SharePointDataStore extends AbstractDataStore {
    private static final Logger logger = LogManager.getLogger(SharePointDataStore.class);

    /**
     * The parameter core reads to decide whether the documents this crawl did not refresh are
     * deleted from the index.
     */
    private static final String DELETE_OLD_DOCS = "delete_old_docs";

    /**
     * Whether a file's content extraction failure is logged instead of failing its crawl target.
     * Matches the spelling used across the other {@code fess-ds-*} plugins, but not their default:
     * they default it to {@code true}, this defaults it to {@code false}.
     *
     * <p>The suppression in {@code FileCrawl#getContent} fires when either this parameter or the
     * global {@code crawler.ignore.content.exception} setting says to ignore. Defaulting this to
     * {@code true} would therefore make the suppression unconditional, overriding an installation
     * that had deliberately set that global setting to {@code false} to get hard failures. This
     * plugin had no such parameter before, so it has a prior behaviour to preserve where the
     * sibling plugins did not; at {@code false} the condition reduces to the global setting alone,
     * which is exactly what this plugin did before the parameter existed. An operator who wants
     * failures ignored regardless of the global setting sets this to {@code true}.
     */
    protected static final String IGNORE_ERROR = "ignore_error";

    /** The HTTP proxy host to route requests through. Matches the spelling used by every other {@code fess-ds-*} plugin. */
    protected static final String PROXY_HOST = "proxy_host";

    /** The HTTP proxy port to route requests through. Matches the spelling used by every other {@code fess-ds-*} plugin. */
    protected static final String PROXY_PORT = "proxy_port";

    /**
     * The name of the extractor component used for a file whose MIME type the extractor factory
     * does not map - not the extractor used for a file in general.
     *
     * <p>{@code ExtractorBuilder#extract} in fess-crawler asks
     * {@code extractorFactory.getExtractor(mimeType)} first, and then
     * {@code getExtractor(detectedMimeType)}; only when neither the declared nor the detected MIME
     * type is mapped does it fall through to {@code crawlerContainer.getComponent(extractorName)}.
     * {@code fess-crawler-lasta}'s {@code crawler/extractor.xml} maps around 1500 lines of MIME
     * types, {@code application/pdf}, {@code text/html} and {@code text/plain} among them, so for
     * essentially any real file this parameter changes nothing.
     *
     * <p>No sibling {@code fess-ds-*} plugin reads this as a {@code DataStoreParams} key - every
     * one of them holds it as a Java field ({@code protected String extractorName}) that only
     * some expose a setter for. This plugin introduces {@code extractor_name} as a new,
     * data-config-configurable parameter because the fallback name was otherwise a
     * {@code private static final} constant nothing could change short of editing the jar.
     */
    protected static final String EXTRACTOR_NAME = "extractor_name";

    /**
     * Comma-separated regular expressions a file's MIME type must match at least one of to be
     * crawled. Matches the spelling used across the other {@code fess-ds-*} plugins.
     */
    protected static final String SUPPORTED_MIMETYPES = "supported_mimetypes";

    /**
     * The maximum file size in bytes. Matches the spelling used by {@code fess-ds-microsoft365},
     * the only sibling with this parameter.
     */
    protected static final String MAX_CONTENT_LENGTH = "max_content_length";

    /**
     * Regular expression a crawled item's URL-ish value must match to be crawled. Read once, in
     * {@link #createCrawler}, into a {@link org.codelibs.fess.crawler.filter.UrlFilter} built
     * there and threaded down through {@link SharePointCrawler.CrawlerConfig} - the same
     * component every other {@code fess-ds-*} plugin with this parameter uses. It is matched
     * against a document library file's server-relative URL, a list item's {@code FileRef}, or a
     * list item attachment's own server-relative URL - not the URL actually indexed. See the README
     * for what that means for a pattern copied from a sibling.
     */
    protected static final String INCLUDE_PATTERN = "include_pattern";

    /**
     * Regular expression that excludes a crawled item from being crawled. Read the same way as
     * {@link #INCLUDE_PATTERN}.
     */
    protected static final String EXCLUDE_PATTERN = "exclude_pattern";

    /**
     * Comma-separated permissions merged into every document's role list in addition to whatever
     * role SharePoint returned (or did not return) for it. Matches the spelling used across the
     * other {@code fess-ds-*} plugins. Read directly from {@code paramMap} in {@link #storeData},
     * not threaded through {@link SharePointCrawler.CrawlerConfig}: {@code storeData} already has
     * {@code paramMap}, and the merge happens after the crawl, not during it.
     */
    protected static final String DEFAULT_PERMISSIONS = "default_permissions";

    /**
     * The server-relative managed path of the site to crawl, such as {@code /teams/eng} or
     * {@code /} for the root site collection. When set, {@code site.name} becomes optional;
     * left unset, the crawl keeps building {@code /sites/<site.name>/} exactly as it always has.
     */
    protected static final String SITE_PATH_PARAM = "site.path";

    /**
     * Whether a site crawl recurses into its subsites, discovered via {@code _api/web/webinfos}.
     * Defaults to {@code false}: with it unset, the crawl issues exactly the same requests it
     * always has - including never requesting {@code webinfos} at all. {@code webinfos} is not
     * security-trimmed, so a subsite the crawl account cannot read is skipped with a warning
     * rather than counted as a crawl failure - see {@code SiteCrawl#doCrawl}.
     */
    protected static final String CRAWL_SUBSITES_PARAM = "site.crawl_subsites";

    /**
     * How many subsite hops below the root site {@link #CRAWL_SUBSITES_PARAM} may recurse. The
     * root site itself is depth 0, so {@code site.max_depth=1} crawls the root's direct children
     * and no further. Matches the name {@code fess-ds-json} uses for the same kind of bound.
     */
    protected static final String MAX_DEPTH_PARAM = "site.max_depth";

    /**
     * How many crawl targets are worked on at once. Matches the spelling every other
     * {@code fess-ds-*} plugin uses for the same thing, and the same default: {@code 1}, which
     * creates no thread pool at all and leaves the crawl on exactly the single-threaded path it
     * has always taken. Capped at twice the processor count - see
     * {@code SharePointCrawler#resolveNumberOfThreads}.
     *
     * <p>It does not change how fast documents are handed to the indexer: {@code read_interval}
     * still paces that, one document per interval, whatever this is set to. See the README.
     */
    protected static final String NUMBER_OF_THREADS = "number_of_threads";

    /**
     * Carries the failure count from {@link #storeData} back to {@link #store}.
     *
     * <p>It cannot be a plain field: one data store instance is registered per handler name and
     * serves every data config, and core runs one thread per data config against it. It does not
     * need to be anything richer than a thread local either, because {@code store} reaches
     * {@code storeData} through a plain call on the same thread.
     */
    private final ThreadLocal<AtomicLong> crawlFailureCount = ThreadLocal.withInitial(AtomicLong::new);

    /**
     * Creates a new SharePointDataStore instance.
     */
    public SharePointDataStore() {
        // default constructor
    }

    @Override
    protected String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * Runs the crawl and, if any crawl target was given up on, tells core to keep the documents
     * this run did not refresh.
     *
     * <p>Core runs that deletion from a {@code finally} block once this method returns, so no
     * exception on any path can prevent it. What it does check first is the
     * {@code delete_old_docs} parameter, on the same map instance passed in here - which is why
     * this belongs in this method rather than in {@link #storeData}, which is handed a copy.
     *
     * <p>An exception that escapes the crawl entirely counts as a failure as well. A malformed
     * {@code url}, {@code site.name} or {@code site.list_id} is rejected while the crawler is
     * built, which {@link #storeData} does before its own {@code try} block - so nothing has been
     * added to the failure count by the time such an exception arrives here, and without the
     * {@code catch} below a configuration typo would produce no documents and delete every
     * document the previous runs had indexed.
     *
     * @param config the data configuration
     * @param callback the callback that indexes each document
     * @param initParamMap the parameters core keeps and reads again after this returns
     */
    @Override
    public void store(final DataConfig config, final IndexUpdateCallback callback, final DataStoreParams initParamMap) {
        crawlFailureCount.remove();
        try {
            super.store(config, callback, initParamMap);
        } catch (final Throwable t) {
            crawlFailureCount.get().incrementAndGet();
            throw t;
        } finally {
            final long failures = crawlFailureCount.get().get();
            crawlFailureCount.remove();
            if (failures > 0 && !Constants.FALSE.equals(initParamMap.getAsString(DELETE_OLD_DOCS))) {
                logger.warn("{} crawl target(s) failed, so documents that were not refreshed will be kept instead of deleted."
                        + " Fix the errors above and crawl again to remove documents that no longer exist.", failures);
                initParamMap.put(DELETE_OLD_DOCS, Constants.FALSE);
            }
        }
    }

    @Override
    protected void storeData(final DataConfig dataConfig, final IndexUpdateCallback callback, final DataStoreParams paramMap,
            final Map<String, String> scriptMap, final Map<String, Object> defaultDataMap) {
        final FessConfig fessConfig = ComponentUtil.getFessConfig();
        final CrawlerStatsHelper crawlerStatsHelper = ComponentUtil.getCrawlerStatsHelper();
        final String roleField = fessConfig.getIndexFieldRole();
        // Constructed outside the try below: the finally has nothing to close or count if this
        // throws, and the exception a bad configuration produces here - a missing url, a
        // malformed site.list_id - is meant to escape this method rather than be handled inside
        // it. It is store() that counts it, on its way out to core.
        final SharePointCrawler crawler = createCrawler(paramMap);
        // Core's stop is a flag, not an interrupt: AbstractDataStore#stop clears alive and nothing
        // interrupts this thread. The while below can only read that flag between the documents
        // doCrawl returns, and one doCrawl call can span a whole folder or list listing - three
        // requests per file - before it returns anything. Handing the crawler the same flag lets
        // its own queue loop and its retry backoff notice a stop too.
        crawler.setStopRequested(() -> !alive);
        // Targets this loop lost to an exception. The crawler counts the ones it gave up on after
        // exhausting their retries; neither counter can see the other's targets, because the
        // exception that lands below left SharePointCrawler#doCrawl at its generic catch, which
        // throws before the retry counter at the end of that loop is reached.
        long failedTargets = 0;
        try {
            final long readInterval = getReadInterval(paramMap);
            final String scriptType = getScriptType(paramMap);
            boolean running = true;
            while (running && alive && crawler.hasCrawlTarget()) {
                try {
                    final Pair<Map<String, Object>, StatsKeyObject> result = crawler.doCrawl(dataConfig);
                    if (logger.isDebugEnabled()) {
                        logger.debug("result: {}", result);
                    }
                    if (result != null) {
                        final Map<String, Object> dataMap = new HashMap<>(defaultDataMap);
                        final Map<String, Object> resultMap = result.getFirst();
                        final StatsKeyObject statsKey = result.getSecond();
                        try {
                            // The crawl only contributes a role when SharePoint actually returned one.
                            // Without this guard a document with no SharePoint role overwrites the
                            // permissions configured on the data config with null, which hides the
                            // document from every user.
                            if (resultMap.containsKey(roleField)) {
                                final List<Object> roles = new ArrayList<>();
                                if (dataMap.get(roleField) instanceof List<?> roleList) {
                                    roles.addAll(roleList);
                                }
                                if (resultMap.get(roleField) instanceof List<?> roleList) {
                                    roles.addAll(roleList);
                                }
                                dataMap.put(roleField, roles);
                            }
                            // Merged outside the guard above, on purpose: default_permissions must
                            // reach the document even when SharePoint returned no role at all - the
                            // exact case that guard exists to protect. Only runs when configured, so
                            // a data config that never touches this parameter keeps producing
                            // whatever role field shape it always has.
                            //
                            // The merge order below (existing roles, then default_permissions, then
                            // distinct()) does not literally reproduce fess-ds-microsoft365's
                            // OneDriveDataStore order (item-derived, then default_permissions, then
                            // defaultDataMap): the guard above has already interleaved this plugin's
                            // own two sources - defaultDataMap's configured role and SharePoint's
                            // returned role - into "existing roles" by the time this code runs, so
                            // there is no remaining seam to insert default_permissions into at that
                            // exact point. Role membership is a set for filtering purposes, so this
                            // does not change what a document is visible to; only the reference
                            // followed for the merge+distinct() shape.
                            final String defaultPermissions = paramMap.getAsString(DEFAULT_PERMISSIONS, StringUtils.EMPTY);
                            if (StringUtils.isNotBlank(defaultPermissions)) {
                                final List<Object> roles = new ArrayList<>();
                                if (dataMap.get(roleField) instanceof List<?> roleList) {
                                    roles.addAll(roleList);
                                }
                                final PermissionHelper permissionHelper = ComponentUtil.getPermissionHelper();
                                for (final String permission : defaultPermissions.split(",")) {
                                    if (StringUtils.isNotBlank(permission)) {
                                        roles.add(permissionHelper.encode(permission));
                                    }
                                }
                                dataMap.put(roleField, roles.stream().distinct().collect(Collectors.toList()));
                            }
                            resultMap.remove(roleField);
                            crawlerStatsHelper.record(statsKey, StatsAction.PREPARED);
                            for (final Map.Entry<String, String> entry : scriptMap.entrySet()) {
                                final Object convertValue = convertValue(scriptType, entry.getValue(), resultMap);
                                if (convertValue != null) {
                                    dataMap.put(entry.getKey(), convertValue);
                                }
                            }
                            crawlerStatsHelper.record(statsKey, StatsAction.EVALUATED);
                            if (dataMap.get(fessConfig.getIndexFieldUrl()) instanceof final String statsUrl) {
                                statsKey.setUrl(statsUrl);
                            }
                            paramMap.put(Constants.CRAWLER_STATS_KEY, statsKey);
                            callback.store(paramMap, dataMap);
                            crawlerStatsHelper.record(statsKey, StatsAction.FINISHED);
                        } finally {
                            crawlerStatsHelper.done(statsKey);
                        }
                    }
                } catch (final CrawlingAccessException e) {
                    logger.warn("Crawling Access Exception: ", e);
                    failedTargets++;

                    Throwable target = e;
                    if (target instanceof MultipleCrawlingAccessException ex) {
                        final Throwable[] causes = ex.getCauses();
                        if (causes.length > 0) {
                            target = causes[causes.length - 1];
                        }
                    }

                    // What lands in the admin UI's URL column for this record is whatever the
                    // exception was constructed with, and this plugin does not construct it
                    // uniformly: FileCrawl passes a file's server-relative URL
                    // ("/sites/x/a.pdf"), while SharePointCrawler#doCrawl's generic catch passes
                    // the stats key's id, which is "<type>#<identifier>" - not a URL. See
                    // SharePointCrawler#storeFailureUrl for the full list of shapes.
                    String url = "";
                    if (target instanceof DataStoreCrawlingException dce) {
                        url = dce.getUrl();
                        if (dce.aborted()) {
                            running = false;
                        }
                    }
                    ComponentUtil.getComponent(FailureUrlService.class)
                            .store(dataConfig, target.getClass().getCanonicalName(), url, target);
                } catch (final Throwable t) {
                    logger.warn("Crawling Access Exception: ", t);
                    failedTargets++;
                    // Blank on purpose: this catch only sees exceptions that are not
                    // CrawlingAccessException, which is the one type carrying a url - the
                    // DataStoreCrawlingException doCrawl throws is a subclass of it and lands in
                    // the catch above instead. Nothing left here identifies a crawl target, so the
                    // admin UI shows this record with an empty URL column and the exception class
                    // name plus the stack trace in the log are what identify it.
                    ComponentUtil.getComponent(FailureUrlService.class).store(dataConfig, t.getClass().getCanonicalName(), "", t);
                }
                if (readInterval > 0) {
                    sleep(readInterval);
                }
            }
            callback.commit();
        } finally {
            // Whatever happened above, hand the failure count to store(), which holds the map
            // core reads afterwards, and release the connection pool the crawler was holding.
            crawlFailureCount.get().addAndGet(failedTargets + crawler.getFailureCount());
            try {
                crawler.close();
            } catch (final IOException e) {
                logger.warn("Failed to close the SharePoint client.", e);
            }
        }
    }

    /**
     * Builds the crawler for one data config. Visible to subclasses so a test can supply a crawler
     * that replays canned results instead of talking to a SharePoint farm.
     *
     * @param paramMap the data config parameters
     * @return a crawler seeded with the first crawl target
     */
    protected SharePointCrawler createCrawler(final DataStoreParams paramMap) {
        final SharePointCrawler.CrawlerConfig config = new SharePointCrawler.CrawlerConfig();
        config.setUrl(paramMap.getAsString("url"));
        if (paramMap.containsKey("auth.ntlm.user")) {
            config.setNtlmUser(paramMap.getAsString("auth.ntlm.user"));
            config.setNtlmPassword(paramMap.getAsString("auth.ntlm.password"));
        }
        if (paramMap.containsKey("auth.oauth.client_id")) {
            config.setOauthClientId(paramMap.getAsString("auth.oauth.client_id"));
            config.setOauthClientSecret(paramMap.getAsString("auth.oauth.client_secret"));
            config.setOauthTenant(paramMap.getAsString("auth.oauth.tenant"));
            config.setOauthRealm(paramMap.getAsString("auth.oauth.realm"));
        }
        config.setSiteName(paramMap.getAsString("site.name"));
        if (paramMap.containsKey(SITE_PATH_PARAM)) {
            config.setSitePath(paramMap.getAsString(SITE_PATH_PARAM));
        }
        if (paramMap.containsKey(CRAWL_SUBSITES_PARAM)) {
            config.setCrawlSubsites(Boolean.parseBoolean(paramMap.getAsString(CRAWL_SUBSITES_PARAM)));
        }
        if (paramMap.containsKey(MAX_DEPTH_PARAM)) {
            config.setMaxDepth(parseInt(paramMap.getAsString(MAX_DEPTH_PARAM), config.getMaxDepth(), MAX_DEPTH_PARAM));
        }
        if (config.isCrawlSubsites() && config.getMaxDepth() < 1) {
            // The recursion guard is depth < maxDepth and the root site is depth 0, so anything
            // below 1 turns the feature the operator just switched on back off. Left as-is rather
            // than corrected to a default, because there is no way to tell which of the two
            // settings was the mistake - but not left silent.
            logger.warn("{} is enabled but {} is {}, so no subsite will be crawled. Set {} to 1 or more to crawl the root's children.",
                    CRAWL_SUBSITES_PARAM, MAX_DEPTH_PARAM, config.getMaxDepth(), MAX_DEPTH_PARAM);
        }
        if (paramMap.containsKey(NUMBER_OF_THREADS)) {
            config.setNumberOfThreads(parseInt(paramMap.getAsString(NUMBER_OF_THREADS), config.getNumberOfThreads(), NUMBER_OF_THREADS));
        }
        if (paramMap.containsKey("site.list_id")) {
            config.setInitialListId(paramMap.getAsString("site.list_id"));
        }
        if (paramMap.containsKey("site.list_name")) {
            config.setInitialListName(paramMap.getAsString("site.list_name"));
        }
        if (paramMap.containsKey("site.doclib_path")) {
            config.setInitialDocLibPath(paramMap.getAsString("site.doclib_path"));
        }
        if (paramMap.containsKey("site.exclude_list")) {
            config.setExcludeList(paramMap.getAsString("site.exclude_list"));
        }
        if (paramMap.containsKey("site.exclude_folder")) {
            config.setExcludeFolder(paramMap.getAsString("site.exclude_folder"));
        }
        if (paramMap.containsKey("list.items.number_per_page")) {
            config.setListItemNumPerPages(parseInt(paramMap.getAsString("list.items.number_per_page"), config.getListItemNumPerPages(),
                    "list.items.number_per_page"));
        }
        if (paramMap.containsKey("list.item.content.include_fields")) {
            config.setListContentIncludeFields(paramMap.getAsString("list.item.content.include_fields"));
        }
        if (paramMap.containsKey("list.item.content.exclude_fields")) {
            config.setListContentExcludeFields(paramMap.getAsString("list.item.content.exclude_fields"));
        }
        if (paramMap.containsKey("list.is_sub_page")) {
            config.setSubPage(Boolean.parseBoolean(paramMap.getAsString("list.is_sub_page")));
        }
        if (paramMap.containsKey("http.connection_timeout")) {
            config.setConnectionTimeout(
                    parseInt(paramMap.getAsString("http.connection_timeout"), config.getConnectionTimeout(), "http.connection_timeout"));
        }
        if (paramMap.containsKey("http.socket_timeout")) {
            config.setSocketTimeout(
                    parseInt(paramMap.getAsString("http.socket_timeout"), config.getSocketTimeout(), "http.socket_timeout"));
        }
        if (paramMap.containsKey("sp.version")) {
            config.setSharePointVersion(paramMap.getAsString("sp.version"));
        }
        if (paramMap.containsKey("retry_limit")) {
            config.setRetryLimit(parseInt(paramMap.getAsString("retry_limit"), config.getRetryLimit(), "retry_limit"));
        }
        if (paramMap.containsKey("role.skip")) {
            config.setSkipRole(Boolean.parseBoolean(paramMap.getAsString("role.skip")));
        }
        config.setIgnoreError(Constants.TRUE.equalsIgnoreCase(paramMap.getAsString(IGNORE_ERROR, Constants.FALSE)));
        if (paramMap.containsKey(PROXY_HOST)) {
            config.setProxyHost(paramMap.getAsString(PROXY_HOST));
            config.setProxyPort(parseInt(paramMap.getAsString(PROXY_PORT), -1, PROXY_PORT));
        }
        if (paramMap.containsKey(EXTRACTOR_NAME)) {
            config.setExtractorName(paramMap.getAsString(EXTRACTOR_NAME));
        }
        if (paramMap.containsKey(SUPPORTED_MIMETYPES)) {
            config.setSupportedMimeTypes(paramMap.getAsString(SUPPORTED_MIMETYPES));
        }
        if (paramMap.containsKey(MAX_CONTENT_LENGTH)) {
            config.setMaxContentLength(
                    parseLong(paramMap.getAsString(MAX_CONTENT_LENGTH), FileCrawl.DEFAULT_MAX_CONTENT_LENGTH, MAX_CONTENT_LENGTH));
        }
        config.setIncludePattern(paramMap.getAsString(INCLUDE_PATTERN));
        config.setExcludePattern(paramMap.getAsString(EXCLUDE_PATTERN));
        config.setSessionId(paramMap.getAsString(Constants.CRAWLING_INFO_ID));
        return new SharePointCrawler(config);
    }

    /**
     * Parses a numeric parameter, falling back to a default instead of failing the whole crawl on
     * a value the admin UI can trivially produce - an empty field, or a typo - the way
     * {@code Integer.parseInt}/{@code Long.parseLong} called directly on it would.
     * {@code createCrawler} runs outside {@link #storeData}'s try block, so an uncaught
     * {@code NumberFormatException} here would fail the whole data-config job rather than one
     * crawl target.
     *
     * @param value the raw parameter value, possibly blank or malformed
     * @param defaultValue the value to use when {@code value} is blank or not a number
     * @param paramName the parameter name, for the warning logged on a malformed value
     * @return the parsed value, or {@code defaultValue}
     */
    private int parseInt(final String value, final int defaultValue, final String paramName) {
        if (StringUtils.isBlank(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (final NumberFormatException e) {
            logger.warn("Invalid {}: \"{}\". Using the default ({}).", paramName, value, defaultValue, e);
            return defaultValue;
        }
    }

    /**
     * Same fallback as {@link #parseInt}, for a {@code long}-valued parameter.
     *
     * @param value the raw parameter value, possibly blank or malformed
     * @param defaultValue the value to use when {@code value} is blank or not a number
     * @param paramName the parameter name, for the warning logged on a malformed value
     * @return the parsed value, or {@code defaultValue}
     */
    private long parseLong(final String value, final long defaultValue, final String paramName) {
        if (StringUtils.isBlank(value)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (final NumberFormatException e) {
            logger.warn("Invalid {}: \"{}\". Using the default ({}).", paramName, value, defaultValue, e);
            return defaultValue;
        }
    }
}
