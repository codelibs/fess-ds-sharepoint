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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.core.misc.Pair;
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
     * Creates a new SharePointDataStore instance.
     */
    public SharePointDataStore() {
        // default constructor
    }

    @Override
    protected String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    protected void storeData(final DataConfig dataConfig, final IndexUpdateCallback callback, final DataStoreParams paramMap,
            final Map<String, String> scriptMap, final Map<String, Object> defaultDataMap) {
        final FessConfig fessConfig = ComponentUtil.getFessConfig();
        final CrawlerStatsHelper crawlerStatsHelper = ComponentUtil.getCrawlerStatsHelper();
        final String roleField = fessConfig.getIndexFieldRole();
        final SharePointCrawler crawler = createCrawler(paramMap);
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
                        if (dataMap.containsKey(roleField) && resultMap.containsKey(roleField)) {
                            final List<Object> roles = new ArrayList<>();
                            if (dataMap.get(roleField) instanceof List<?> roleList) {
                                roles.addAll(roleList);
                            }
                            if (resultMap.get(roleField) instanceof List<?> roleList) {
                                roles.addAll(roleList);
                            }
                            dataMap.put(roleField, roles);
                        } else {
                            dataMap.put(roleField, resultMap.get(roleField));
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
            }
            if (readInterval > 0) {
                sleep(readInterval);
            }
        }
        callback.commit();
    }

    private SharePointCrawler createCrawler(final DataStoreParams paramMap) {
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
