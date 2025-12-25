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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import org.codelibs.fess.exception.DataStoreCrawlingException;
import org.codelibs.fess.helper.CrawlerStatsHelper;
import org.codelibs.fess.helper.CrawlerStatsHelper.StatsAction;
import org.codelibs.fess.helper.CrawlerStatsHelper.StatsKeyObject;
import org.codelibs.fess.opensearch.config.exentity.DataConfig;
import org.codelibs.fess.util.ComponentUtil;

import jakarta.validation.ValidationException;

/**
 * Crawler for crawling SharePoint sites.
 */
public class SharePointCrawler {
    private static final Logger logger = LogManager.getLogger(SharePointCrawler.class);

    private final SharePointClient client;

    private final ConcurrentLinkedQueue<SharePointCrawl> crawlingQueue = new ConcurrentLinkedQueue<>();

    private final CrawlerConfig config;

    /**
     * Creates a new SharePointCrawler with the specified configuration.
     *
     * @param config the crawler configuration
     */
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

    /**
     * Checks if there are remaining targets to crawl.
     *
     * @return true if there are more targets to crawl
     */
    public boolean hasCrawlTarget() {
        return !crawlingQueue.isEmpty();
    }

    /**
     * Performs a crawl operation.
     *
     * @param dataConfig the data configuration
     * @return a pair containing the crawled data map and stats key, or null if no data
     */
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
            return "/sites/" + siteName + initialDocLibPath;
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
    }
}
