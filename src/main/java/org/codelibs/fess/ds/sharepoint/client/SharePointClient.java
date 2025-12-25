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
package org.codelibs.fess.ds.sharepoint.client;

import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApis;
import org.codelibs.fess.ds.sharepoint.client.helper.SharePointHelper;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;
import org.codelibs.fess.ds.sharepoint.client2013.api.SharePoint2013Apis;

/**
 * Client for communicating with SharePoint REST API.
 */
public class SharePointClient {
    private final String url;
    private final String siteUrl;
    private final String siteName;

    private SharePointApis sharePointApis;
    private final SharePointHelper sharePointHelper;

    /**
     * Creates a new SharePointClient instance.
     *
     * @param httpClient the HTTP client to use for requests
     * @param url the base URL of the SharePoint server
     * @param siteName the name of the SharePoint site
     * @param oAuth the OAuth configuration, or null if not using OAuth
     * @param verson2013 true if using SharePoint 2013 API
     */
    protected SharePointClient(final CloseableHttpClient httpClient, final String url, final String siteName, final OAuth oAuth,
            final boolean verson2013) {
        this.siteUrl = buildSiteUrl(url, siteName);
        this.url = url;
        this.siteName = siteName;
        this.sharePointHelper = new SharePointHelper(this, verson2013);
        if (verson2013) {
            this.sharePointApis = new SharePoint2013Apis(httpClient, siteUrl, oAuth);
        } else {
            this.sharePointApis = new SharePointApis(httpClient, siteUrl, oAuth);
        }
    }

    /**
     * Returns the SharePoint API interface.
     *
     * @return the SharePoint APIs object
     */
    public SharePointApis api() {
        return sharePointApis;
    }

    /**
     * Returns the SharePoint helper utility.
     *
     * @return the SharePoint helper object
     */
    public SharePointHelper helper() {
        return sharePointHelper;
    }

    /**
     * Returns the site name.
     *
     * @return the SharePoint site name
     */
    public String getSiteName() {
        return siteName;
    }

    /**
     * Returns the base URL of the SharePoint server.
     *
     * @return the base URL
     */
    public String getUrl() {
        return url;
    }

    /**
     * Returns the full site URL.
     *
     * @return the full site URL including the site path
     */
    public String getSiteUrl() {
        return siteUrl;
    }

    /**
     * Creates a new builder for constructing SharePointClient instances.
     *
     * @return a new SharePointClientBuilder
     */
    public static SharePointClientBuilder builder() {
        return new SharePointClientBuilder();
    }

    private String buildSiteUrl(final String url, final String siteName) {
        return url + "sites/" + siteName + "/";
    }
}
