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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.TestInfo;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.codelibs.fess.ds.sharepoint.client.credential.NtlmCredential;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;
import org.codelibs.fess.ds.sharepoint.util.SharePointMockServer;
import org.codelibs.fess.util.ComponentUtil;
import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;

public class SharePointClientBuilderTest extends UnitDsTestCase {

    @Override
    protected String prepareConfigFile() {
        return "test_app.xml";
    }

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Override
    public void tearDown(TestInfo testInfo) throws Exception {
        ComponentUtil.setFessConfig(null);
        super.tearDown(testInfo);
    }

    @Test
    public void test_setUrl_withTrailingSlash() {
        final SharePointClientBuilder builder = SharePointClient.builder();
        builder.setUrl("https://example.com/");
        // Test that URL is properly set (would need reflection or build to verify)
        assertNotNull(builder);
    }

    @Test
    public void test_setUrl_withoutTrailingSlash() {
        final SharePointClientBuilder builder = SharePointClient.builder();
        builder.setUrl("https://example.com");
        // Test that URL is properly set with trailing slash added
        assertNotNull(builder);
    }

    @Test
    public void test_builderChaining() {
        final RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(10000).setConnectTimeout(10000).build();
        final CloseableHttpClient httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();

        final SharePointClientBuilder builder = SharePointClient.builder()
                .setUrl("https://example.com/")
                .setSite("testsite")
                .setRequestConfig(requestConfig)
                .setHttpClient(httpClient);

        assertNotNull(builder);
    }

    @Test
    public void test_builderWithCredentials() {
        final NtlmCredential credential = new NtlmCredential("user", "password", "hostname", "domain");
        final SharePointClientBuilder builder =
                SharePointClient.builder().setUrl("https://example.com/").setSite("testsite").setCredential(credential);

        assertNotNull(builder);
    }

    @Test
    public void test_builderWithOAuth() {
        final OAuth oAuth = new OAuth("clientId", "clientSecret", "tenant", "realm");

        final SharePointClientBuilder builder =
                SharePointClient.builder().setUrl("https://example.com/").setSite("testsite").setOAuth(oAuth);

        assertNotNull(builder);
    }

    @Test
    public void test_apply2013() {
        final SharePointClientBuilder builder = SharePointClient.builder().setUrl("https://example.com/").setSite("testsite").apply2013();

        assertNotNull(builder);
    }

    @Test
    public void test_buildWithMinimalConfiguration() {
        final RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(10000).setConnectTimeout(10000).build();
        final CloseableHttpClient httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();

        final SharePointClient client =
                SharePointClient.builder().setUrl("https://example.com/").setSite("testsite").setHttpClient(httpClient).build();

        assertNotNull(client);
        assertEquals("testsite", client.getSiteName());
        assertEquals("https://example.com/", client.getUrl());
        assertEquals("https://example.com/sites/testsite/", client.getSiteUrl());
    }

    @Test
    public void test_sitePathDefaultsToTheSitesManagedPath() {
        final SharePointClient client = SharePointClient.builder().setUrl("https://example.com").setSite("testsite").build();
        assertEquals("the default site path is unchanged", "/sites/testsite/", client.getSitePath());
        assertEquals("the default site URL is unchanged", "https://example.com/sites/testsite/", client.getSiteUrl());
    }

    @Test
    public void test_sitePathAcceptsAnotherManagedPath() {
        final SharePointClient client = SharePointClient.builder().setUrl("https://example.com").setSitePath("/teams/eng").build();
        assertEquals("a leading and trailing slash are added", "/teams/eng/", client.getSitePath());
        assertEquals("https://example.com/teams/eng/", client.getSiteUrl());
    }

    @Test
    public void test_sitePathAcceptsTheRootSiteCollection() {
        final SharePointClient client = SharePointClient.builder().setUrl("https://example.com").setSitePath("/").build();
        assertEquals("/", client.getSitePath());
        assertEquals("the root site collection has no path segment", "https://example.com/", client.getSiteUrl());
    }

    @Test
    public void test_sitePathWinsOverSiteName() {
        final SharePointClient client =
                SharePointClient.builder().setUrl("https://example.com").setSite("ignored").setSitePath("teams/eng/").build();
        assertEquals("/teams/eng/", client.getSitePath());
    }

    @Test
    public void test_buildRoutePlanner_returnsNullWithNoProxyConfigured() {
        final SharePointClientBuilder builder = SharePointClient.builder().setUrl("https://example.com/").setSite("testsite");

        assertNull("no proxy_host/proxy_port configured must mean requests are not routed through a proxy", builder.buildRoutePlanner());
    }

    @Test
    public void test_buildRoutePlanner_targetsTheConfiguredProxy() {
        final SharePointClientBuilder builder = SharePointClient.builder()
                .setUrl("https://example.com/")
                .setSite("testsite")
                .setProxyHost("proxy.example.com")
                .setProxyPort(8080);

        final HttpRoutePlanner routePlanner = builder.buildRoutePlanner();

        assertNotNull("proxy_host/proxy_port must produce a route planner", routePlanner);
        assertTrue("it must be a route planner that sends requests through a fixed proxy",
                routePlanner instanceof DefaultProxyRoutePlanner);
    }

    @Test
    public void test_buildRoutePlanner_returnsNullWhenOnlyHostIsConfigured() {
        final SharePointClientBuilder builder =
                SharePointClient.builder().setUrl("https://example.com/").setSite("testsite").setProxyHost("proxy.example.com");

        assertNull("a proxy_host without a proxy_port must not be treated as configured", builder.buildRoutePlanner());
    }

    /**
     * Against the unfixed code, the request arrives with Apache HttpClient's default User-Agent
     * ("Apache-HttpClient/4.5.14 (Java/21...)"), which an administrator cannot register with
     * SPHttpUserAgentAndMethodClassifier as a stable exemption - it embeds the running JRE
     * version.
     *
     * <p>The expected value is written out literally rather than read from
     * {@code SharePointClientBuilder.USER_AGENT}. Comparing the header to that constant moves both
     * sides of the assertion together, so the exact regression this guards against - the string
     * growing something environment-derived again - passed unnoticed.
     */
    @Test
    @Timeout(value = 15, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_build_sendsAStableUserAgent() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus("/sites/test/_api/web/GetFileByServerRelativePath(decodedUrl='/a.txt')/$value", 200, "text/plain", "hi");
            server.start();

            try (SharePointClient client = SharePointClient.builder().setUrl(server.getBaseUrl()).setSite("test").build()) {
                client.api().file().getFile().setServerRelativeUrl("/a.txt").execute().close();
            }

            assertEquals(1, server.getRecordedRequests().size());
            assertEquals("the User-Agent must be the stable string an administrator can register as an exemption",
                    "FessSharePointDataStore/1.0", server.getRecordedRequests().get(0).getHeader("User-Agent"));
        }
    }

    /**
     * The constant an administrator is told to register with
     * {@code SPHttpUserAgentAndMethodClassifier} - see the README - must stay exactly what the
     * README says, so changing it is a deliberate act with a documentation change attached rather
     * than a silent break of every exemption rule already deployed.
     */
    @Test
    public void test_userAgentConstantIsTheDocumentedLiteral() {
        assertEquals("the documented User-Agent must not change without the README changing with it", "FessSharePointDataStore/1.0",
                SharePointClientBuilder.USER_AGENT);
    }

    /**
     * Apache HttpClient's connection pool allows 2 connections per route by default, and a whole
     * SharePoint crawl is one route, so a crawl running more than two threads would spend its time
     * waiting for a connection instead of making requests - and only until the connection request
     * timeout ran out, after which the third thread's request fails outright. That is exactly what
     * {@code SharePointCrawlerTest#test_buildRequestConfig_connectionRequestTimeoutIsHonouredUnderPoolExhaustion}
     * demonstrates against a client built without this. Here the same three concurrent requests
     * must all get through.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_theConnectionPoolIsSizedForTheThreadCount() throws Exception {
        final String slowFileUrl = "/sites/test/_api/web/GetFileByServerRelativePath(decodedUrl='/slow.txt')/$value";
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(slowFileUrl, 200, "text/plain", "done");
            server.withDelay(slowFileUrl, 3000L);
            server.start();

            // A bounded wait for a pooled connection, so a pool that is too small fails this test
            // in a second rather than hanging it.
            final RequestConfig requestConfig =
                    RequestConfig.custom().setConnectTimeout(1000).setSocketTimeout(30000).setConnectionRequestTimeout(1000).build();
            try (SharePointClient client = SharePointClient.builder()
                    .setUrl(server.getBaseUrl())
                    .setSite("test")
                    .setRequestConfig(requestConfig)
                    .setMaxConnections(4)
                    .build()) {
                final ExecutorService occupiers = Executors.newFixedThreadPool(2);
                final CountDownLatch bothStarted = new CountDownLatch(2);
                try {
                    for (int i = 0; i < 2; i++) {
                        occupiers.submit(() -> {
                            bothStarted.countDown();
                            try {
                                client.api().file().getFile().setServerRelativeUrl("/slow.txt").execute().close();
                            } catch (final Exception e) {
                                // Not under test here - only that the connection stayed leased.
                            }
                        });
                    }
                    assertTrue("both occupying requests must have started", bothStarted.await(10, TimeUnit.SECONDS));
                    // Give the two occupiers a moment to actually lease their connections.
                    Thread.sleep(300L);

                    assertDoesNotThrow(() -> client.api().file().getFile().setServerRelativeUrl("/slow.txt").execute().close(),
                            "a third concurrent request must get a connection of its own when the pool is sized for the thread count");
                } finally {
                    occupiers.shutdownNow();
                }
            }
        }
    }
}
