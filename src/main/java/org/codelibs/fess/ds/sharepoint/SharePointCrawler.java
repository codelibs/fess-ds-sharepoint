package org.codelibs.fess.ds.sharepoint;

import org.apache.http.client.config.RequestConfig;
import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.client.SharePointClientBuilder;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemRoleResponse;
import org.codelibs.fess.ds.sharepoint.client.credential.NtlmCredential;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointServerException;
import org.codelibs.fess.ds.sharepoint.crawl.doclib.FolderCrawl;
import org.codelibs.fess.ds.sharepoint.crawl.list.ListCrawl;
import org.codelibs.fess.ds.sharepoint.crawl.SharePointCrawl;
import org.codelibs.fess.ds.sharepoint.crawl.SiteCrawl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.ValidationException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SharePointCrawler {
    private static final Logger logger = LoggerFactory.getLogger(SharePointCrawler.class);

    private final SharePointClient client;

    private final ConcurrentLinkedQueue<SharePointCrawl> crawlingQueue = new ConcurrentLinkedQueue<>();

    public SharePointCrawler(CrawlerConfig config) {
        validate(config);
        this.client = createClient(config);
        setFirstCrawl(config);
        if (crawlingQueue.isEmpty()) {
            logger.error("Failed to start crawl.");
        }
    }

    private void validate(CrawlerConfig config) {
        if (config.url == null) {
            throw new ValidationException("url param is required.");
        }
        if (config.siteName == null) {
            throw new ValidationException("sitename param is required.");
        }
        if (config.initialDocLibPath == null && config.initialListName == null && config.initialListId == null) {
            throw new ValidationException("initial param is required.");
        }
    }

    private SharePointClient createClient(CrawlerConfig config) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(config.getConnectionTimeout())
                .setSocketTimeout(config.getSocketTimeout())
                .build();
        SharePointClientBuilder builder = SharePointClient.builder().setUrl(config.getUrl()).setSite(config.getSiteName()).setRequestConfig(requestConfig);
        final String ntlmUser = config.getNtlmUser();
        if (ntlmUser != null) {
            final String ntlmPass = config.getNtlmPassword();
            builder.setCredential(new NtlmCredential(ntlmUser, ntlmPass, null, null));
        }
        return builder.build();
    }

    private void setFirstCrawl(CrawlerConfig crawlerConfig) {
        final Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache = new ConcurrentHashMap<>();
        if (crawlerConfig.getInitialListId() == null && crawlerConfig.getInitialListName() == null && crawlerConfig.getInitialDocLibPath() == null) {
            crawlingQueue.offer(new SiteCrawl(client, crawlerConfig.getSiteName(), crawlerConfig.getListItemNumPerPages(), sharePointGroupCache));
        } else {
            if (crawlerConfig.getInitialListId() != null || crawlerConfig.getInitialListName() != null) {
                crawlingQueue.offer(new ListCrawl(client, crawlerConfig.getInitialListId(), crawlerConfig.getInitialListName(), crawlerConfig.listItemNumPerPages, sharePointGroupCache));
            }
            if (crawlerConfig.getInitialDocLibPath() != null) {
                crawlingQueue.offer(new FolderCrawl(client, crawlerConfig.getInitialDocLibPath(), sharePointGroupCache));
            }
        }

    }

    public boolean hasCrawlTarget() {
        return !crawlingQueue.isEmpty();
    }

    public Map<String, Object> doCrawl() {
        while(!crawlingQueue.isEmpty()) {
            SharePointCrawl crawl = crawlingQueue.poll();
            if (crawl == null) {
                continue;
            }
            try {
                Map<String, Object> dataMap = crawl.doCrawl(crawlingQueue);
                if (dataMap != null) {
                    return dataMap;
                }
            } catch (SharePointServerException e){
                logger.warn("Api server error: {}", e.getMessage());
                if (logger.isDebugEnabled()) {
                    logger.debug("Error detail.", e);
                }
            } catch (SharePointClientException e) {
                logger.error("Error occured. " + e.getMessage(), e);
            }
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
        private int connectionTimeout = 30000;
        private int socketTimeout = 30000;
        private int listItemNumPerPages = 100;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url.endsWith("/") ? url : url + "/";
        }

        public String getSiteName() {
            return siteName;
        }

        public void setSiteName(String siteName) {
            this.siteName = siteName;
        }

        public String getInitialListId() {
            return initialListId;
        }

        public void setInitialListId(String initialListId) {
            this.initialListId = initialListId;
        }

        public String getInitialListName() {
            if (initialListName == null) {
                return null;
            }
            return initialListName;
        }

        public void setInitialListName(String initialListName) {
            this.initialListName = initialListName;
        }

        public String getInitialDocLibPath() {
            if (initialDocLibPath == null) {
                return null;
            }
            return "/sites/" + siteName + initialDocLibPath;
        }

        public void setInitialDocLibPath(String initialDocLibPath) {
            this.initialDocLibPath = initialDocLibPath.startsWith("/") ? initialDocLibPath : "/" + initialDocLibPath;
        }

        public String getNtlmUser() {
            return ntlmUser;
        }

        public void setNtlmUser(String ntlmUser) {
            this.ntlmUser = ntlmUser;
        }

        public String getNtlmPassword() {
            return ntlmPassword;
        }

        public void setNtlmPassword(String ntlmPassword) {
            this.ntlmPassword = ntlmPassword;
        }

        public int getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        public int getSocketTimeout() {
            return socketTimeout;
        }

        public void setSocketTimeout(int socketTimeout) {
            this.socketTimeout = socketTimeout;
        }

        public int getListItemNumPerPages() {
            return listItemNumPerPages;
        }

        public void setListItemNumPerPages(int listItemNumPerPages) {
            this.listItemNumPerPages = listItemNumPerPages;
        }
    }
}
