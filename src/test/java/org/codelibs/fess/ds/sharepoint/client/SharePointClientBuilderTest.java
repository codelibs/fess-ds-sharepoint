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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.TestInfo;

import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Lookup;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.auth.SPNegoSchemeFactory;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.codelibs.fess.ds.sharepoint.client.credential.KerberosCredential;
import org.codelibs.fess.ds.sharepoint.client.credential.NtlmCredential;
import org.codelibs.fess.ds.sharepoint.client.credential.SharePointCredential;
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

    /**
     * Pins what the NTLM path registers, because it must not change.
     *
     * <p>{@code AuthScope.ANY} means NTLM credentials also answer a {@code Basic} or
     * {@code Digest} challenge, and an installation that points {@code auth.ntlm.user} at a
     * SharePoint fronted by basic authentication depends on exactly that; narrowing the scope
     * would stop it working with nothing to explain why.
     *
     * <p>The same breadth is what makes NTLM and Kerberos impossible to register together: the
     * last assertion here is the collision itself. {@code AuthScope#match} scores {@code ANY} as a
     * match for a {@code Negotiate} scope, so these credentials come back for a Kerberos
     * challenge, {@code GGSSchemeBase} finds they are not {@code KerberosCredentials} and falls
     * back to an empty ticket cache without a word. That is closed by
     * {@code SharePointCrawler#validate} rejecting the combination, not by narrowing this scope -
     * see {@code SharePointCrawlerTest#test_construction_rejectsMoreThanOneAuthenticationMethod}.
     */
    @Test
    public void test_ntlmCredentialsAreStillRegisteredForAnyScheme() {
        final SharePointClientBuilder builder = SharePointClient.builder()
                .setUrl("https://example.com/")
                .setSite("testsite")
                .setCredential(new NtlmCredential("user", "password", null, null));

        final CredentialsProvider credentialsProvider = builder.buildCredentialsProvider();
        assertNotNull("a configured credential must produce a credentials provider", credentialsProvider);
        assertNotNull("NTLM credentials must still answer a Basic challenge, as they always have",
                credentialsProvider.getCredentials(new AuthScope("example.com", 443, AuthScope.ANY_REALM, AuthSchemes.BASIC)));
        assertNotNull("and a Digest challenge",
                credentialsProvider.getCredentials(new AuthScope("example.com", 443, AuthScope.ANY_REALM, AuthSchemes.DIGEST)));
        assertNotNull("and an NTLM challenge",
                credentialsProvider.getCredentials(new AuthScope("example.com", 443, AuthScope.ANY_REALM, AuthSchemes.NTLM)));

        final Credentials forNegotiate =
                credentialsProvider.getCredentials(new AuthScope("example.com", 443, AuthScope.ANY_REALM, AuthSchemes.SPNEGO));
        assertNotNull("AuthScope.ANY also matches a Negotiate scope, which is the collision validate() exists to prevent", forNegotiate);
        assertTrue("and what comes back is NTCredentials, which GGSSchemeBase cannot get a GSSCredential out of",
                forNegotiate instanceof NTCredentials);
    }

    /**
     * The one invariant the whole design rests on: a credential that does not ask for a scheme
     * registry must not be given one.
     *
     * <p>{@code HttpClientBuilder#setDefaultAuthSchemeRegistry} <b>replaces</b> the default
     * registry outright rather than merging into it, so a non-null default on
     * {@link SharePointCredential#getAuthSchemeRegistry()} would hand every NTLM and
     * unauthenticated crawl a registry built for Kerberos - and a Negotiate-shaped one at that,
     * which is exactly the silent 401 this whole change exists to prevent. Changing that default
     * breaks nothing else in the suite, so it is pinned here from three directions: the interface
     * default itself, the sole existing implementor, and the builder seam that consumes it.
     */
    @Test
    public void test_aCredentialThatAsksForNoSchemeRegistryIsNotGivenOne() {
        final SharePointCredential bareImplementor = () -> new NtlmCredential("user", "password", null, null).getCredential();
        assertNull("the SharePointCredential default must keep Apache HttpClient's own registry, which means returning null",
                bareImplementor.getAuthSchemeRegistry());
        assertNull("NtlmCredential must not override it", new NtlmCredential("user", "password", null, null).getAuthSchemeRegistry());

        assertNull("an unauthenticated crawl must not get a registry either",
                SharePointClient.builder().setUrl("https://example.com/").setSite("testsite").buildAuthSchemeRegistry());
        assertNull("and neither must an NTLM crawl",
                SharePointClient.builder()
                        .setUrl("https://example.com/")
                        .setSite("testsite")
                        .setCredential(new NtlmCredential("user", "password", null, null))
                        .buildAuthSchemeRegistry());

        // The other side of the same seam, so this test fails if the registry stops being
        // installed at all rather than only if it starts being installed everywhere.
        assertNotNull("a Kerberos crawl must get the registry it asks for",
                SharePointClient.builder()
                        .setUrl("https://example.com/")
                        .setSite("testsite")
                        .setCredential(new KerberosCredential("fess@EXAMPLE.COM", null, "password", null, true, false, false))
                        .buildAuthSchemeRegistry());
    }

    /**
     * The behavioural half of the guarantee {@code AuthScope.ANY} exists for: an installation that
     * points {@code auth.ntlm.user} at a SharePoint fronted by basic authentication works, over the
     * wire and not merely in a scope lookup.
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_ntlmCredentialsStillSatisfyABasicChallenge() throws Exception {
        final String filePath = "/sites/test/_api/web/GetFileByServerRelativePath(decodedUrl='/a.txt')/$value";
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(filePath, 200, "text/plain", "basic-ok");
            server.requireAuthorization("Basic realm=\"test\"",
                    authorization -> authorization != null && authorization.startsWith("Basic ") ? "" : null);
            server.start();

            try (SharePointClient client = SharePointClient.builder()
                    .setUrl(server.getBaseUrl())
                    .setSite("test")
                    .setCredential(new NtlmCredential("user", "password", null, null))
                    .build()) {
                assertDoesNotThrow(() -> client.api().file().getFile().setServerRelativeUrl("/a.txt").execute().close(),
                        "NTLM credentials must keep answering a Basic challenge");
            }
        }
    }

    /** No credential configured must still mean no credentials provider is installed. */
    @Test
    public void test_noCredentialMeansNoCredentialsProvider() {
        assertNull("an unauthenticated crawl must not get a credentials provider",
                SharePointClient.builder().setUrl("https://example.com/").setSite("testsite").buildCredentialsProvider());
    }

    /**
     * A credential that asks for no preferred schemes - which is every credential except
     * {@code KerberosCredential} - must leave the request configuration byte for byte as it was.
     */
    @Test
    public void test_aCredentialWithNoSchemePreferencesLeavesTheRequestConfigAlone() {
        final RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(11000).setConnectTimeout(12000).build();
        final SharePointClientBuilder builder = SharePointClient.builder()
                .setUrl("https://example.com/")
                .setSite("testsite")
                .setRequestConfig(requestConfig)
                .setCredential(new NtlmCredential("user", "password", null, null));

        assertSame("the configured request config must be passed straight through", requestConfig, builder.buildEffectiveRequestConfig());
        assertNull("and it must carry no preferred auth schemes", builder.buildEffectiveRequestConfig().getTargetPreferredAuthSchemes());
    }

    /** A credential that does ask for preferred schemes must have them reach the request config. */
    @Test
    public void test_aCredentialsPreferredSchemesReachTheRequestConfig() {
        final RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(11000).setConnectTimeout(12000).build();
        final SharePointClientBuilder builder = SharePointClient.builder()
                .setUrl("https://example.com/")
                .setSite("testsite")
                .setRequestConfig(requestConfig)
                .setCredential(new KerberosCredential("fess@EXAMPLE.COM", null, "password", null, true, false, false));

        final RequestConfig effective = builder.buildEffectiveRequestConfig();
        assertEquals("Negotiate must be the only scheme tried against the target host", List.of(AuthSchemes.SPNEGO),
                List.copyOf(effective.getTargetPreferredAuthSchemes()));
        assertEquals("and nothing else about the request config may change", 11000, effective.getSocketTimeout());
        assertEquals(12000, effective.getConnectTimeout());
    }

    /**
     * {@code KerberosCredential} scopes itself to {@code Negotiate}, so a {@code Basic} challenge
     * from something in front of the farm is never answered with a Kerberos ticket. Asserted on the
     * credential itself rather than through a built provider: building one would run the JAAS
     * login, which needs a KDC (see {@code KerberosAuthenticationTest}).
     */
    @Test
    public void test_kerberosCredentialsScopeThemselvesToNegotiate() {
        final KerberosCredential credential = new KerberosCredential("fess@EXAMPLE.COM", null, "password", null, true, false, false);

        final AuthScope scope = credential.getAuthScope();
        assertTrue("a Negotiate challenge must match",
                scope.match(new AuthScope("example.com", 443, AuthScope.ANY_REALM, AuthSchemes.SPNEGO)) >= 0);
        assertEquals("a Basic challenge must not match at all", -1,
                scope.match(new AuthScope("example.com", 443, AuthScope.ANY_REALM, AuthSchemes.BASIC)));
        assertEquals("Negotiate must be the only preferred scheme", List.of(AuthSchemes.SPNEGO), credential.getPreferredAuthSchemes());
    }

    /**
     * The SPNEGO factory the credential registers must carry the configured flags, not Apache
     * HttpClient's defaults - {@code useCanonicalHostname} in particular, which HttpClient defaults
     * to {@code true} and this connector deliberately defaults to {@code false}.
     *
     * <p>The rest of HttpClient's default registry must survive: replacing the registry with the
     * Negotiate entry alone would silently drop {@code Basic}, {@code Digest} and {@code NTLM},
     * which a proxy in front of the farm can still challenge with.
     */
    @Test
    public void test_theRegisteredSpnegoFactoryCarriesTheConfiguredHostnameFlags() {
        final Lookup<AuthSchemeProvider> registry =
                new KerberosCredential("fess@EXAMPLE.COM", null, "password", null, true, false, false).getAuthSchemeRegistry();

        final SPNegoSchemeFactory spnego = (SPNegoSchemeFactory) registry.lookup(AuthSchemes.SPNEGO);
        assertTrue("strip_port must reach the registered scheme", spnego.isStripPort());
        assertFalse("use_canonical_hostname must reach the registered scheme, and defaults to false here unlike in HttpClient",
                spnego.isUseCanonicalHostname());

        final SPNegoSchemeFactory canonical =
                (SPNegoSchemeFactory) new KerberosCredential("fess@EXAMPLE.COM", null, "password", null, false, true, false)
                        .getAuthSchemeRegistry()
                        .lookup(AuthSchemes.SPNEGO);
        assertFalse("strip_port=false must reach the registered scheme too", canonical.isStripPort());
        assertTrue("and so must use_canonical_hostname=true", canonical.isUseCanonicalHostname());

        assertNotNull("Basic must survive replacing the registry", registry.lookup(AuthSchemes.BASIC));
        assertNotNull("Digest must survive replacing the registry", registry.lookup(AuthSchemes.DIGEST));
        assertNotNull("NTLM must survive replacing the registry", registry.lookup(AuthSchemes.NTLM));
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
