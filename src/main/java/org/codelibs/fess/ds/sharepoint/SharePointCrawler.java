/*
 * Copyright 2012-2023 CodeLibs Project and the Others.
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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.validation.ValidationException;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.config.RequestConfig;
import org.codelibs.core.misc.Pair;
import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.client.SharePointClientBuilder;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemRoleResponse;
import org.codelibs.fess.ds.sharepoint.client.credential.NtlmCredential;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointServerException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;
import org.codelibs.fess.ds.sharepoint.crawl.SharePointCrawl;
import org.codelibs.fess.ds.sharepoint.crawl.SiteCrawl;
import org.codelibs.fess.ds.sharepoint.crawl.doclib.FolderCrawl;
import org.codelibs.fess.ds.sharepoint.crawl.list.ListCrawl;
import org.codelibs.fess.es.config.exentity.DataConfig;
import org.codelibs.fess.exception.DataStoreCrawlingException;
import org.codelibs.fess.helper.CrawlerStatsHelper;
import org.codelibs.fess.helper.CrawlerStatsHelper.StatsAction;
import org.codelibs.fess.helper.CrawlerStatsHelper.StatsKeyObject;
import org.codelibs.fess.util.ComponentUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SharePointCrawler {
    private static final Logger logger = LoggerFactory.getLogger(SharePointCrawler.class);

    private final SharePointClient client;

    private final ConcurrentLinkedQueue<SharePointCrawl> crawlingQueue = new ConcurrentLinkedQueue<>();

    private final CrawlerConfig config;

    public SharePointCrawler(final CrawlerConfig config) {
        validate(config);
        this.client = createClient(config);
        this.config = config;
        setFirstCrawl(config);
        if (crawlingQueue.isEmpty()) {
            logger.error("Failed to start crawl.");
        }
    }

    private void validate(final CrawlerConfig config) {
        if (config.url == null) {
            throw new ValidationException("url param is required.");
        }
        if (config.siteName == null) {
            throw new ValidationException("sitename param is required.");
        }
    }

    private SharePointClient createClient(final CrawlerConfig config) {
        final RequestConfig requestConfig =
                RequestConfig.custom().setConnectTimeout(config.getConnectionTimeout()).setSocketTimeout(config.getSocketTimeout()).build();
        final SharePointClientBuilder builder =
                SharePointClient.builder().setUrl(config.getUrl()).setSite(config.getSiteName()).setRequestConfig(requestConfig);
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
        }
        return builder.build();
    }

    private void setFirstCrawl(final CrawlerConfig crawlerConfig) {
        final Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache = new ConcurrentHashMap<>();
        if (crawlerConfig.getInitialListId() == null && crawlerConfig.getInitialListName() == null
                && crawlerConfig.getInitialDocLibPath() == null) {
            crawlingQueue.offer(new SiteCrawl(client, crawlerConfig, sharePointGroupCache));
        } else {
            if (crawlerConfig.getInitialListId() != null || crawlerConfig.getInitialListName() != null) {
                crawlingQueue.offer(new ListCrawl(client, crawlerConfig.getInitialListId(), crawlerConfig.getInitialListName(),
                        crawlerConfig.listItemNumPerPages, sharePointGroupCache, crawlerConfig.isSubPage(), crawlerConfig.isSkipRole(),
                        crawlerConfig.getListContentIncludeFields(), crawlerConfig.getListContentExcludeFields()));
            }
            if (crawlerConfig.getInitialDocLibPath() != null) {
                crawlingQueue.offer(
                        new FolderCrawl(client, crawlerConfig.getInitialDocLibPath(), crawlerConfig.isSkipRole(), sharePointGroupCache));
            }
        }
    }

    public boolean hasCrawlTarget() {
        return !crawlingQueue.isEmpty();
    }

    public Pair<Map<String, Object>, StatsKeyObject> doCrawl(final DataConfig dataConfig) {
        final CrawlerStatsHelper crawlerStatsHelper = ComponentUtil.getCrawlerStatsHelper();
        while (!crawlingQueue.isEmpty()) {
            final SharePointCrawl crawl = crawlingQueue.poll();
            if (crawl == null) {
                continue;
            }
            final StatsKeyObject statsKey = crawl.getStatsKey();
            crawlerStatsHelper.begin(statsKey);
            int retryCount = 0;
            while (retryCount <= config.getRetryLimit()) {
                try {
                    final Map<String, Object> dataMap = crawl.doCrawl(dataConfig, crawlingQueue);
                    crawlerStatsHelper.record(statsKey, StatsAction.ACCESSED);
                    if (dataMap != null) {
                        return new Pair<>(dataMap, statsKey);
                    }
                    break;
                } catch (final SharePointServerException e) {
                    if (retryCount + 1 <= config.getRetryLimit()) {
                        logger.warn("Api server error: {}  [Retry:{}]", e.getMessage(), retryCount);
                    } else {
                        logger.warn("Api server error: {}", e.getMessage(), e);
                    }
                } catch (final SharePointClientException e) {
                    if (retryCount + 1 <= config.getRetryLimit()) {
                        logger.warn("Error occured: {}  [Retry:{}]", e.getMessage(), retryCount);
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
            crawlerStatsHelper.done(statsKey);
        }
        return null;
    }

    public static class CrawlerConfig {
        private String url = null;
        private String siteName = null;
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

        public String getUrl() {
            return url;
        }

        public void setUrl(final String url) {
            this.url = url.endsWith("/") ? url : url + "/";
        }

        public String getSiteName() {
            return siteName;
        }

        public void setSiteName(final String siteName) {
            this.siteName = siteName;
        }

        public String getInitialListId() {
            return initialListId;
        }

        public void setInitialListId(final String initialListId) {
            this.initialListId = initialListId;
        }

        public String getInitialListName() {
            return initialListName;
        }

        public void setInitialListName(final String initialListName) {
            this.initialListName = initialListName;
        }

        public String getInitialDocLibPath() {
            if (initialDocLibPath == null) {
                return null;
            }
            return "/sites/" + siteName + initialDocLibPath;
        }

        public void setInitialDocLibPath(final String initialDocLibPath) {
            this.initialDocLibPath = initialDocLibPath.startsWith("/") ? initialDocLibPath : "/" + initialDocLibPath;
        }

        public String getNtlmUser() {
            return ntlmUser;
        }

        public void setNtlmUser(final String ntlmUser) {
            this.ntlmUser = ntlmUser;
        }

        public String getNtlmPassword() {
            return ntlmPassword;
        }

        public void setNtlmPassword(final String ntlmPassword) {
            this.ntlmPassword = ntlmPassword;
        }

        public String getOauthClientId() {
            return oauthClientId;
        }

        public void setOauthClientId(final String oauthClientId) {
            this.oauthClientId = oauthClientId;
        }

        public String getOauthClientSecret() {
            return oauthClientSecret;
        }

        public void setOauthClientSecret(final String oauthClientSecret) {
            this.oauthClientSecret = oauthClientSecret;
        }

        public String getOauthTenant() {
            return oauthTenant;
        }

        public void setOauthTenant(final String oauthTenant) {
            this.oauthTenant = oauthTenant;
        }

        public String getOauthRealm() {
            return oauthRealm;
        }

        public void setOauthRealm(final String oauthRealm) {
            this.oauthRealm = oauthRealm;
        }

        public int getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(final int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        public int getSocketTimeout() {
            return socketTimeout;
        }

        public void setSocketTimeout(final int socketTimeout) {
            this.socketTimeout = socketTimeout;
        }

        public int getListItemNumPerPages() {
            return listItemNumPerPages;
        }

        public void setListItemNumPerPages(final int listItemNumPerPages) {
            this.listItemNumPerPages = listItemNumPerPages;
        }

        public String getSharePointVersion() {
            return sharePointVersion;
        }

        public void setSharePointVersion(final String sharePointVersion) {
            this.sharePointVersion = sharePointVersion;
        }

        public int getRetryLimit() {
            return retryLimit;
        }

        public void setRetryLimit(final int retryLimit) {
            this.retryLimit = retryLimit;
        }

        public boolean isSubPage() {
            return isSubPage;
        }

        public void setSubPage(final boolean subPage) {
            isSubPage = subPage;
        }

        public List<String> getListContentIncludeFields() {
            return listContentIncludeFields;
        }

        public void setListContentIncludeFields(final String listContentIncludeFields) {
            this.listContentIncludeFields = Arrays.asList(listContentIncludeFields.trim().split(","));
        }

        public List<String> getListContentExcludeFields() {
            return listContentExcludeFields;
        }

        public void setListContentExcludeFields(final String listContentExcludeFields) {
            this.listContentExcludeFields = Arrays.asList(listContentExcludeFields.trim().split(","));
        }

        public List<String> getExcludeList() {
            return excludeList;
        }

        public void setExcludeList(final String excludeList) {
            this.excludeList = Arrays.asList(excludeList.split(","));
        }

        public List<String> getExcludeFolder() {
            return excludeFolder;
        }

        public void setExcludeFolder(final String excludeFolder) {
            this.excludeFolder = Arrays.asList(excludeFolder.split(","));
        }

        public boolean isSkipRole() {
            return skipRole;
        }

        public void setSkipRole(final boolean skipRole) {
            this.skipRole = skipRole;
        }
    }
}
