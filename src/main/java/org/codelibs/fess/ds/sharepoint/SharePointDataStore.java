/*
 * Copyright 2012-2021 CodeLibs Project and the Others.
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

import org.codelibs.fess.crawler.exception.CrawlingAccessException;
import org.codelibs.fess.crawler.exception.MultipleCrawlingAccessException;
import org.codelibs.fess.ds.AbstractDataStore;
import org.codelibs.fess.ds.callback.IndexUpdateCallback;
import org.codelibs.fess.es.config.exentity.DataConfig;
import org.codelibs.fess.exception.DataStoreCrawlingException;
import org.codelibs.fess.mylasta.direction.FessConfig;
import org.codelibs.fess.util.ComponentUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SharePointDataStore extends AbstractDataStore {
    private static final Logger logger = LoggerFactory.getLogger(SharePointDataStore.class);

    @Override
    protected String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    protected void storeData(final DataConfig dataConfig, final IndexUpdateCallback callback, final Map<String, String> paramMap,
            final Map<String, String> scriptMap, final Map<String, Object> defaultDataMap) {
        final FessConfig fessConfig = ComponentUtil.getFessConfig();
        final String roleField = fessConfig.getIndexFieldRole();
        final SharePointCrawler crawler = createCrawler(paramMap);
        final long readInterval = getReadInterval(paramMap);
        final String scriptType = getScriptType(paramMap);
        boolean running = true;
        while (running && crawler.hasCrawlTarget()) {
            try {
                final Map<String, Object> resultMap = crawler.doCrawl();
                if (logger.isDebugEnabled()) {
                    logger.debug("ResultMap: " + resultMap);
                }
                if (resultMap != null) {
                    final Map<String, Object> dataMap = new HashMap<>(defaultDataMap);
                    if (dataMap.containsKey(roleField) && resultMap.containsKey(roleField)) {
                        final List<Object> roles = new ArrayList<>((List) dataMap.get(roleField));
                        roles.addAll((List) resultMap.get(roleField));
                        dataMap.put(roleField, roles);
                    } else {
                        dataMap.put(roleField, resultMap.get(roleField));
                    }
                    resultMap.remove(roleField);
                    for (final Map.Entry<String, String> entry : scriptMap.entrySet()) {
                        final Object convertValue = convertValue(scriptType, entry.getValue(), resultMap);
                        if (convertValue != null) {
                            dataMap.put(entry.getKey(), convertValue);
                        }
                    }
                    callback.store(paramMap, dataMap);
                }
            } catch (final CrawlingAccessException e) {
                logger.warn("Crawling Access Exception: ", e);

                Throwable target = e;
                if (target instanceof MultipleCrawlingAccessException) {
                    final Throwable[] causes = ((MultipleCrawlingAccessException) target).getCauses();
                    if (causes.length > 0) {
                        target = causes[causes.length - 1];
                    }
                }

                if (target instanceof DataStoreCrawlingException) {
                    final DataStoreCrawlingException dce = (DataStoreCrawlingException) target;
                    if (dce.aborted()) {
                        running = false;
                    }
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

    private SharePointCrawler createCrawler(final Map<String, String> paramMap) {
        final SharePointCrawler.CrawlerConfig config = new SharePointCrawler.CrawlerConfig();
        config.setUrl(paramMap.get("url"));
        if (paramMap.containsKey("auth.ntlm.user")) {
            config.setNtlmUser(paramMap.get("auth.ntlm.user"));
            config.setNtlmPassword(paramMap.get("auth.ntlm.password"));
        }
        if (paramMap.containsKey("auth.oauth.client_id")) {
            config.setOauthClientId(paramMap.get("auth.oauth.client_id"));
            config.setOauthClientSecret(paramMap.get("auth.oauth.client_secret"));
            config.setOauthTenant(paramMap.get("auth.oauth.tenant"));
            config.setOauthRealm(paramMap.get("auth.oauth.realm"));
        }
        config.setSiteName(paramMap.get("site.name"));
        if (paramMap.containsKey("site.list_id")) {
            config.setInitialListId(paramMap.get("site.list_id"));
        }
        if (paramMap.containsKey("site.list_name")) {
            config.setInitialListName(paramMap.get("site.list_name"));
        }
        if (paramMap.containsKey("site.doclib_path")) {
            config.setInitialDocLibPath(paramMap.get("site.doclib_path"));
        }
        if (paramMap.containsKey("site.exclude_list")) {
            config.setExcludeList(paramMap.get("site.exclude_list"));
        }
        if (paramMap.containsKey("site.exclude_folder")) {
            config.setExcludeFolder(paramMap.get("site.exclude_folder"));
        }
        if (paramMap.containsKey("list.items.number_per_page")) {
            config.setListItemNumPerPages(Integer.parseInt(paramMap.get("list.items.number_per_page")));
        }
        if (paramMap.containsKey("list.item.content.include_fields")) {
            config.setListContentIncludeFields(paramMap.get("list.item.content.include_fields"));
        }
        if (paramMap.containsKey("list.item.content.exclude_fields")) {
            config.setListContentExcludeFields(paramMap.get("list.item.content.exclude_fields"));
        }
        if (paramMap.containsKey("list.is_sub_page")) {
            config.setSubPage(Boolean.parseBoolean(paramMap.get("list.is_sub_page")));
        }
        if (paramMap.containsKey("http.connection_timeout")) {
            config.setConnectionTimeout(Integer.parseInt(paramMap.get("http.connection_timeout")));
        }
        if (paramMap.containsKey("http.socket_timeout")) {
            config.setSocketTimeout(Integer.parseInt(paramMap.get("http.socket_timeout")));
        }
        if (paramMap.containsKey("sp.version")) {
            config.setSharePointVersion(paramMap.get("sp.version"));
        }
        if (paramMap.containsKey("retry_limit")) {
            config.setRetryLimit(Integer.parseInt(paramMap.get("retry_limit")));
        }
        if (paramMap.containsKey("role.skip")) {
            config.setSkipRole(Boolean.parseBoolean(paramMap.get("role.skip")));
        }
        return new SharePointCrawler(config);
    }
}
