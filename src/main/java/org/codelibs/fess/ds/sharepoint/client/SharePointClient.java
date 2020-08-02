package org.codelibs.fess.ds.sharepoint.client;

import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApis;
import org.codelibs.fess.ds.sharepoint.client.helper.SharePointHelper;

public class SharePointClient {
    private final String url;
    private final String siteUrl;
    private final String siteName;

    private final SharePointApis sharePointApis;
    private final SharePointHelper sharePointHelper;

    protected SharePointClient(CloseableHttpClient httpClient, String url, String siteName) {
        this.siteUrl = buildSiteUrl(url, siteName);
        this.url = url;
        this.siteName = siteName;
        this.sharePointApis = new SharePointApis(httpClient, siteUrl);
        this.sharePointHelper = new SharePointHelper(this);
    }

    public SharePointApis api() {
        return sharePointApis;
    }

    public SharePointHelper helper() {
        return sharePointHelper;
    }

    public String getSiteName() {
        return siteName;
    }

    public String getUrl() {
        return url;
    }

    public String getSiteUrl() {
        return siteUrl;
    }

    public static SharePointClientBuilder builder() {
        return new SharePointClientBuilder();
    }

    private String buildSiteUrl(String url, String siteName) {
        return url + "sites/" + siteName + "/";
    }
}
