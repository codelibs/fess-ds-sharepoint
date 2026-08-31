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

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Lookup;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.codelibs.fess.ds.sharepoint.client.credential.SharePointCredential;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;

/**
 * Builder class for creating SharePointClient instances.
 */
public class SharePointClientBuilder {

    /**
     * The User-Agent sent with every request, in place of Apache HttpClient's default
     * ("Apache-HttpClient/4.5.14 (Java/21...)").
     *
     * <p>Deliberately avoids "crawler", "bot", "spider" or similar: SharePoint's built-in
     * {@code SPSearchCrawlingRequestClassifier} matches those patterns in a request's User-Agent
     * and defaults a request it matches to its "FirstStage" throttle level - not the throttling
     * this whole change exists to cooperate with rather than trigger. Separately, an administrator
     * can register this exact string with {@code SPHttpUserAgentAndMethodClassifier} at
     * {@code ThrottleLevel.Never} to exempt it from throttling entirely - see the README.
     */
    public static final String USER_AGENT = "FessSharePointDataStore/1.0";

    private String url = null;
    private String siteName = null;
    private String sitePath = null;
    private SharePointCredential credential = null;
    private OAuth oAuth = null;
    private RequestConfig requestConfig = null;
    private CloseableHttpClient httpClient = null;
    private boolean verson2013 = false;
    private String proxyHost = null;
    private int proxyPort = -1;
    private int maxConnections = 0;

    /**
     * Creates a new SharePointClientBuilder instance.
     */
    protected SharePointClientBuilder() {
    }

    /**
     * Sets the SharePoint server URL.
     *
     * @param url the SharePoint server URL
     * @return this builder instance
     */
    public SharePointClientBuilder setUrl(final String url) {
        this.url = url.endsWith("/") ? url : url + "/";
        return this;
    }

    /**
     * Sets the SharePoint site name.
     *
     * @param siteName the site name
     * @return this builder instance
     */
    public SharePointClientBuilder setSite(final String siteName) {
        this.siteName = siteName;
        return this;
    }

    /**
     * Sets the server-relative managed path of the site, such as {@code /teams/eng} or {@code /}
     * for the root site collection. Overrides the {@code /sites/<siteName>} path this connector
     * builds by default; leaving this unset keeps that default exactly.
     *
     * @param sitePath the server-relative site path
     * @return this builder instance
     */
    public SharePointClientBuilder setSitePath(final String sitePath) {
        this.sitePath = sitePath;
        return this;
    }

    /**
     * Sets the credential for authentication.
     *
     * @param credential the SharePoint credential
     * @return this builder instance
     */
    public SharePointClientBuilder setCredential(final SharePointCredential credential) {
        this.credential = credential;
        return this;
    }

    /**
     * Sets the OAuth configuration.
     *
     * @param oAuth the OAuth configuration
     * @return this builder instance
     */
    public SharePointClientBuilder setOAuth(final OAuth oAuth) {
        this.oAuth = oAuth;
        return this;
    }

    /**
     * Sets a custom HTTP client.
     *
     * @param httpClient the HTTP client to use
     * @return this builder instance
     */
    public SharePointClientBuilder setHttpClient(final CloseableHttpClient httpClient) {
        this.httpClient = httpClient;
        return this;
    }

    /**
     * Sets the HTTP request configuration.
     *
     * @param requestConfig the request configuration
     * @return this builder instance
     */
    public SharePointClientBuilder setRequestConfig(final RequestConfig requestConfig) {
        this.requestConfig = requestConfig;
        return this;
    }

    /**
     * Configures the builder to use SharePoint 2013 API.
     *
     * @return this builder instance
     */
    public SharePointClientBuilder apply2013() {
        verson2013 = true;
        return this;
    }

    /**
     * Sets the HTTP proxy host to route requests through.
     *
     * @param proxyHost the proxy host
     * @return this builder instance
     */
    public SharePointClientBuilder setProxyHost(final String proxyHost) {
        this.proxyHost = proxyHost;
        return this;
    }

    /**
     * Sets the HTTP proxy port to route requests through.
     *
     * @param proxyPort the proxy port
     * @return this builder instance
     */
    public SharePointClientBuilder setProxyPort(final int proxyPort) {
        this.proxyPort = proxyPort;
        return this;
    }

    /**
     * Sets how many HTTP connections the client may hold open, both in total and to the single
     * route every request of a crawl takes.
     *
     * <p>Apache HttpClient's pool defaults to <b>2 connections per route</b>, and a SharePoint
     * crawl is one route (one scheme, host and port), so without raising this every crawl thread
     * past the second spends its time waiting for a pooled connection to free up rather than
     * making requests - and waits only as long as the connection request timeout allows before
     * failing outright. Set this to the number of threads that will share the client.
     *
     * <p>A value below 2 leaves Apache HttpClient's own defaults in place, so a single-threaded
     * crawl keeps exactly the pool it has always had.
     *
     * @param maxConnections the maximum number of connections, in total and per route
     * @return this builder instance
     */
    public SharePointClientBuilder setMaxConnections(final int maxConnections) {
        this.maxConnections = maxConnections;
        return this;
    }

    /**
     * Builds a new SharePointClient instance with the configured settings.
     *
     * @return a new SharePointClient instance
     */
    public SharePointClient build() {
        final CloseableHttpClient httpClient = buildHttpClient();
        if (oAuth != null) {
            oAuth.updateAccessToken(httpClient);
        }
        return new SharePointClient(httpClient, url, siteName, sitePath, oAuth, verson2013);
    }

    private CloseableHttpClient buildHttpClient() {
        if (httpClient != null) {
            return httpClient;
        }

        final HttpClientBuilder builder = HttpClientBuilder.create();
        builder.setUserAgent(USER_AGENT);
        if (maxConnections >= 2) {
            // Only raised when asked for: below 2 this leaves Apache HttpClient's own defaults
            // alone, so a single-threaded crawl keeps the pool it has always had rather than
            // being narrowed to one connection.
            builder.setMaxConnPerRoute(maxConnections).setMaxConnTotal(maxConnections);
        }
        builder.setDefaultRequestConfig(buildEffectiveRequestConfig());

        final CredentialsProvider credentialsProvider = buildCredentialsProvider();
        if (credentialsProvider != null) {
            builder.setDefaultCredentialsProvider(credentialsProvider);
        }
        final Lookup<AuthSchemeProvider> authSchemeRegistry = buildAuthSchemeRegistry();
        if (authSchemeRegistry != null) {
            builder.setDefaultAuthSchemeRegistry(authSchemeRegistry);
        }
        final HttpRoutePlanner routePlanner = buildRoutePlanner();
        if (routePlanner != null) {
            builder.setRoutePlanner(routePlanner);
        }
        return builder.build();
    }

    /**
     * Builds the credentials provider the HTTP client is built with.
     *
     * <p>The credential decides its own {@link org.apache.http.auth.AuthScope}. Every credential
     * except {@code KerberosCredential} inherits {@code AuthScope.ANY} from
     * {@link SharePointCredential#getAuthScope()}, which is what this connector has always
     * registered them under - NTLM credentials answering a {@code Basic} or {@code Digest}
     * challenge included.
     *
     * <p>Package-private so a test can inspect what a credential is registered under without
     * reaching into the built client, the same way {@link #buildRoutePlanner()} is exposed.
     *
     * @return the provider, or {@code null} when no credential is configured
     */
    protected CredentialsProvider buildCredentialsProvider() {
        if (credential == null) {
            return null;
        }
        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(credential.getAuthScope(), credential.getCredential());
        return credentialsProvider;
    }

    /**
     * The authentication scheme registry the HTTP client is built with, or {@code null} to leave
     * Apache HttpClient's own default registry in place.
     *
     * <p>{@code null} is the answer for every credential that does not override
     * {@link SharePointCredential#getAuthSchemeRegistry()}, which today is all of them except
     * {@code KerberosCredential}. That matters more than it looks:
     * {@code HttpClientBuilder#setDefaultAuthSchemeRegistry} <b>replaces</b> the default registry
     * outright, so a credential handed the wrong one loses every scheme that registry does not
     * name. A default that returned anything but {@code null} would give an NTLM or unauthenticated
     * crawl a registry built for Kerberos.
     *
     * <p>Package-private so a test can pin exactly that.
     *
     * @return the registry to install, or {@code null} to keep Apache HttpClient's default
     */
    protected Lookup<AuthSchemeProvider> buildAuthSchemeRegistry() {
        if (credential == null) {
            return null;
        }
        return credential.getAuthSchemeRegistry();
    }

    /**
     * Builds the request configuration the HTTP client is built with: the one set with
     * {@link #setRequestConfig}, or this connector's own 30-second defaults, plus the credential's
     * preferred authentication schemes when it asks for any.
     *
     * <p>A credential that returns no preferred schemes - which is every credential except
     * {@code KerberosCredential} - leaves the request configuration exactly as it was.
     *
     * <p>Package-private so a test can assert what the client is actually built with.
     *
     * @return the request configuration
     */
    protected RequestConfig buildEffectiveRequestConfig() {
        final RequestConfig config =
                requestConfig != null ? requestConfig : RequestConfig.custom().setSocketTimeout(30000).setConnectTimeout(30000).build();
        if (credential == null) {
            return config;
        }
        final List<String> preferredAuthSchemes = credential.getPreferredAuthSchemes();
        if (preferredAuthSchemes == null) {
            return config;
        }
        return RequestConfig.copy(config).setTargetPreferredAuthSchemes(preferredAuthSchemes).build();
    }

    /**
     * Builds the route planner that sends requests through the configured HTTP proxy.
     *
     * @return a route planner targeting {@link #proxyHost}/{@link #proxyPort}, or {@code null}
     *         when no proxy is configured
     */
    protected HttpRoutePlanner buildRoutePlanner() {
        if (StringUtils.isBlank(proxyHost) || proxyPort <= 0) {
            return null;
        }
        return new DefaultProxyRoutePlanner(new HttpHost(proxyHost, proxyPort));
    }
}
