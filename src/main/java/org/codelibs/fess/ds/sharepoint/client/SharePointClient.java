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
package org.codelibs.fess.ds.sharepoint.client;

import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApis;
import org.codelibs.fess.ds.sharepoint.client.helper.SharePointHelper;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;
import org.codelibs.fess.ds.sharepoint.client2013.api.SharePoint2013Apis;

public class SharePointClient {
    private final String url;
    private final String siteUrl;
    private final String siteName;

    private SharePointApis sharePointApis;
    private final SharePointHelper sharePointHelper;

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

    private String buildSiteUrl(final String url, final String siteName) {
        return url + "sites/" + siteName + "/";
    }
}
