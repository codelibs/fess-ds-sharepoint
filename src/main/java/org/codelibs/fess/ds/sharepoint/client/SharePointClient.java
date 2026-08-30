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

import java.io.Closeable;
import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApis;
import org.codelibs.fess.ds.sharepoint.client.helper.SharePointHelper;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;
import org.codelibs.fess.ds.sharepoint.client2013.api.SharePoint2013Apis;

/**
 * Client for communicating with SharePoint REST API.
 *
 * <p>The client owns the {@link CloseableHttpClient} it was built with, including one handed to
 * {@link SharePointClientBuilder#setHttpClient}, and releases its connection pool in
 * {@link #close()}.
 */
public class SharePointClient implements Closeable {
    private final String url;
    private final String siteUrl;
    private final String siteName;
    private final String sitePath;

    /** Kept so the connection pool can be released; the APIs below hold their own reference. */
    private final CloseableHttpClient httpClient;

    private SharePointApis sharePointApis;
    private final SharePointHelper sharePointHelper;

    /**
     * Creates a new SharePointClient instance.
     *
     * @param httpClient the HTTP client to use for requests
     * @param url the base URL of the SharePoint server
     * @param siteName the name of the SharePoint site
     * @param sitePath the server-relative managed path of the site, or blank to fall back to
     *            {@code /sites/<siteName>/}
     * @param oAuth the OAuth configuration, or null if not using OAuth
     * @param verson2013 true if using SharePoint 2013 API
     */
    protected SharePointClient(final CloseableHttpClient httpClient, final String url, final String siteName, final String sitePath,
            final OAuth oAuth, final boolean verson2013) {
        this.sitePath = normalizeSitePath(sitePath, siteName);
        this.siteUrl = buildSiteUrl(url, this.sitePath);
        this.url = url;
        this.siteName = siteName;
        this.httpClient = httpClient;
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
     * Returns the server-relative path of the site this client targets, normalized to always
     * start and end with a slash (e.g. {@code /sites/mysite/}, {@code /teams/eng/}, or {@code /}
     * for the root site collection).
     *
     * @return the server-relative site path
     */
    public String getSitePath() {
        return sitePath;
    }

    /**
     * Releases the underlying HTTP client and its connection pool.
     *
     * <p>The client is unusable afterwards: the API objects it hands out all share that one
     * {@link CloseableHttpClient}.
     *
     * @throws IOException if the HTTP client fails to close
     */
    @Override
    public void close() throws IOException {
        httpClient.close();
    }

    /**
     * Creates a new builder for constructing SharePointClient instances.
     *
     * @return a new SharePointClientBuilder
     */
    public static SharePointClientBuilder builder() {
        return new SharePointClientBuilder();
    }

    /**
     * Normalizes a configured site path to a server-relative path that starts and ends with a
     * slash. A blank value falls back to the {@code /sites/<siteName>/} this connector has always
     * built, so an existing configuration produces exactly the URLs it produced before.
     *
     * <p>Public, rather than package-visible, because {@code SharePointCrawler.CrawlerConfig}
     * (package {@code org.codelibs.fess.ds.sharepoint}) needs to call it and package-private
     * access does not cross the {@code .client} package boundary - this is still the single place
     * the connector normalizes a site path; {@code CrawlerConfig#getSiteRelativePath()} is the
     * only other caller.
     *
     * @param sitePath the configured {@code site.path}, or blank/null if not set
     * @param siteName the configured site name, used only when {@code sitePath} is blank
     * @return the normalized, server-relative site path
     */
    public static String normalizeSitePath(final String sitePath, final String siteName) {
        if (StringUtils.isBlank(sitePath)) {
            return "/sites/" + siteName + "/";
        }
        final String trimmed = sitePath.trim();
        final String withLeading = trimmed.startsWith("/") ? trimmed : "/" + trimmed;
        return withLeading.endsWith("/") ? withLeading : withLeading + "/";
    }

    private String buildSiteUrl(final String url, final String sitePath) {
        // url always ends with a slash and sitePath always begins with one.
        return url + sitePath.substring(1);
    }
}
