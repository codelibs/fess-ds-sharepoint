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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.core.misc.Pair;
import org.codelibs.fess.Constants;
import org.codelibs.fess.crawler.exception.CrawlingAccessException;
import org.codelibs.fess.crawler.exception.MultipleCrawlingAccessException;
import org.codelibs.fess.ds.AbstractDataStore;
import org.codelibs.fess.ds.callback.IndexUpdateCallback;
import org.codelibs.fess.entity.DataStoreParams;
import org.codelibs.fess.exception.DataStoreCrawlingException;
import org.codelibs.fess.helper.CrawlerStatsHelper;
import org.codelibs.fess.helper.CrawlerStatsHelper.StatsAction;
import org.codelibs.fess.helper.CrawlerStatsHelper.StatsKeyObject;
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
        // Targets this loop lost to an exception. The crawler counts the ones it gave up on after
        // exhausting their retries; neither counter can see the other's targets, because the
        // exception that lands below left SharePointCrawler#doCrawl at its generic catch, which
        // throws before the retry counter at the end of that loop is reached.
        long failedTargets = 0;
        try {
            final long readInterval = getReadInterval(paramMap);
            final String scriptType = getScriptType(paramMap);
            boolean running = true;
            while (running && crawler.hasCrawlTarget()) {
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
                            resultMap.remove(roleField);
                            crawlerStatsHelper.record(statsKey, StatsAction.PREPARED);
                            for (final Map.Entry<String, String> entry : scriptMap.entrySet()) {
                                final Object convertValue = convertValue(scriptType, entry.getValue(), resultMap);
                                if (convertValue != null) {
                                    dataMap.put(entry.getKey(), convertValue);
                                }
                            }
                            crawlerStatsHelper.record(statsKey, StatsAction.EVALUATED);
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

                    if (target instanceof DataStoreCrawlingException dce && dce.aborted()) {
                        running = false;
                    }
                } catch (final Throwable t) {
                    logger.warn("Crawling Access Exception: ", t);
                    failedTargets++;
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
            config.setListItemNumPerPages(Integer.parseInt(paramMap.getAsString("list.items.number_per_page")));
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
            config.setConnectionTimeout(Integer.parseInt(paramMap.getAsString("http.connection_timeout")));
        }
        if (paramMap.containsKey("http.socket_timeout")) {
            config.setSocketTimeout(Integer.parseInt(paramMap.getAsString("http.socket_timeout")));
        }
        if (paramMap.containsKey("sp.version")) {
            config.setSharePointVersion(paramMap.getAsString("sp.version"));
        }
        if (paramMap.containsKey("retry_limit")) {
            config.setRetryLimit(Integer.parseInt(paramMap.getAsString("retry_limit")));
        }
        if (paramMap.containsKey("role.skip")) {
            config.setSkipRole(Boolean.parseBoolean(paramMap.getAsString("role.skip")));
        }
        return new SharePointCrawler(config);
    }
}
