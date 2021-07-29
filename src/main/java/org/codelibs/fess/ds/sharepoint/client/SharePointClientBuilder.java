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
package org.codelibs.fess.ds.sharepoint.client;

import org.apache.http.auth.AuthScope;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.codelibs.fess.ds.sharepoint.client.credential.SharePointCredential;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;

public class SharePointClientBuilder {
    private String url = null;
    private String siteName = null;
    private SharePointCredential credential = null;
    private OAuth oAuth = null;
    private RequestConfig requestConfig = null;
    private CloseableHttpClient httpClient = null;
    private int retryCount = 0;
    private boolean verson2013 = false;

    protected SharePointClientBuilder() {
    }

    public SharePointClientBuilder setUrl(final String url) {
        this.url = url.endsWith("/") ? url : url + "/";
        return this;
    }

    public SharePointClientBuilder setSite(final String siteName) {
        this.siteName = siteName;
        return this;
    }

    public SharePointClientBuilder setCredential(final SharePointCredential credential) {
        this.credential = credential;
        return this;
    }

    public SharePointClientBuilder setOAuth(final OAuth oAuth) {
        this.oAuth = oAuth;
        return this;
    }

    public SharePointClientBuilder setHttpClient(final CloseableHttpClient httpClient) {
        this.httpClient = httpClient;
        return this;
    }

    public SharePointClientBuilder setRequestConfig(final RequestConfig requestConfig) {
        this.requestConfig = requestConfig;
        return this;
    }

    public SharePointClientBuilder setRetryCount(final int retryCount) {
        this.retryCount = retryCount;
        return this;
    }

    public SharePointClientBuilder apply2013() {
        verson2013 = true;
        return this;
    }

    public SharePointClient build() {
        final CloseableHttpClient httpClient = buildHttpClient();
        if (oAuth != null) {
            oAuth.updateAccessToken(httpClient);
        }
        return new SharePointClient(httpClient, url, siteName, oAuth, verson2013);
    }

    private CloseableHttpClient buildHttpClient() {
        if (httpClient != null) {
            return httpClient;
        }

        final HttpClientBuilder builder = HttpClientBuilder.create();
        if (requestConfig != null) {
            builder.setDefaultRequestConfig(requestConfig);
        } else {
            final RequestConfig config = RequestConfig.custom().setSocketTimeout(30000).setConnectTimeout(30000).build();
            builder.setDefaultRequestConfig(config);
        }

        if (credential != null) {
            final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY, credential.getCredential());
            builder.setDefaultCredentialsProvider(credentialsProvider);
        }
        if (retryCount > 0) {
            builder.setRetryHandler(new DefaultHttpRequestRetryHandler(retryCount, true));
        }
        return builder.build();
    }
}
