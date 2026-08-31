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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;

import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.KerberosCredentials;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.config.Lookup;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.auth.NTLMSchemeFactory;
import org.apache.kerby.kerberos.kerb.server.SimpleKdcServer;
import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.client.api.file.getfile.GetFileResponse;
import org.codelibs.fess.ds.sharepoint.client.credential.KerberosCredential;
import org.codelibs.fess.ds.sharepoint.client.credential.NtlmCredential;
import org.codelibs.fess.ds.sharepoint.client.credential.SharePointCredential;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.util.SharePointMockServer;
import org.codelibs.fess.entity.DataStoreParams;
import org.codelibs.fess.helper.CrawlerStatsHelper;
import org.codelibs.fess.helper.SystemHelper;
import org.codelibs.fess.util.ComponentUtil;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.Oid;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;

import jakarta.validation.ValidationException;

/**
 * Drives the {@code auth.kerberos.*} path against a real, in-process Kerberos KDC and a mock
 * SharePoint that answers with {@code WWW-Authenticate: Negotiate}.
 *
 * <p><b>What an in-process KDC proves and what it does not.</b> It proves the wiring inside this
 * connector: that a ticket really is obtained for the configured principal, that it is handed to
 * Apache HttpClient as a {@code KerberosCredentials} rather than dropped, that the SPNEGO scheme is
 * registered and reached, and that a 401 {@code Negotiate} challenge is turned into an
 * {@code Authorization: Negotiate} header the acceptor validates. It proves <b>nothing</b> about a
 * real farm: alternate access mappings, the {@code Host} header a load balancer rewrites, reverse
 * DNS, whether the SPN is actually registered on the right service account, or IIS Extended
 * Protection. Those are all decided outside this process.
 */
public class KerberosAuthenticationTest extends UnitDsTestCase {

    /** The realm the in-process KDC serves. */
    private static final String REALM = "EXAMPLE.COM";

    /** The client principal a crawl authenticates as. */
    private static final String CLIENT_PRINCIPAL = "fess@" + REALM;

    /** The client principal's password. */
    private static final String CLIENT_PASSWORD = "fess-password";

    /** The SPNEGO mechanism OID (RFC 4178). */
    private static final String SPNEGO_OID = "1.3.6.1.5.5.2";

    /** How many times the KDC is given to bind before the test gives up. */
    private static final int KDC_START_ATTEMPTS = 10;

    /** The JVM-global system property naming the Kerberos configuration file. */
    private static final String KRB5_CONF_PROPERTY = "java.security.krb5.conf";

    /** The path the mock SharePoint serves a file from. */
    private static final String FILE_PATH = "/sites/test/_api/web/GetFileByServerRelativePath(decodedUrl='/a.txt')/$value";

    private static Locale originalLocale;

    private static String originalKrb5Conf;

    private static Path workDir;

    private static SimpleKdcServer kdc;

    private static File keytab;

    /**
     * The host name Apache HttpClient derives when {@code use_canonical_hostname} is on, resolved
     * through exactly the call {@code GGSSchemeBase#resolveCanonicalHostname} makes.
     */
    private static String canonicalHostname;

    private static Subject acceptorSubject;

    private static GSSCredential acceptorCredential;

    /** The client principal names the acceptor has authenticated, in arrival order. */
    private static final List<String> acceptedPrincipals = new CopyOnWriteArrayList<>();

    /** Every token the acceptor rejected, so a failed handshake is visible rather than a bare 401. */
    private static final List<Exception> acceptFailures = new CopyOnWriteArrayList<>();

    /**
     * Bounded like every test method is, and for the same reason: everything here runs outside any
     * {@code @Timeout} on a test, there is no {@code junit-platform.properties} setting a default,
     * and a KDC that comes up but never answers - or a JAAS login waiting on one - would hang the
     * build rather than fail it. {@link ThreadMode#SEPARATE_THREAD} is what makes the timeout able
     * to interrupt a blocked socket read.
     */
    @BeforeAll
    @Timeout(value = 180, unit = SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
    public static void startKerberos() throws Exception {
        // Kerby 2.1.2 still calls String#toLowerCase() with no locale in KdcConfigKey,
        // KrbConfigKey and EncryptionUtil, so a Turkish default locale turns "kdcTcpPort" into a
        // key nothing matches and the KDC comes up misconfigured.
        originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);

        // SimpleKdcServer#init sets this itself, so it has to be put back afterwards: one
        // surefire JVM runs every test class in this module.
        originalKrb5Conf = System.getProperty(KRB5_CONF_PROPERTY);

        workDir = Files.createTempDirectory("fess-ds-sharepoint-kdc");

        // Derived, not hardcoded: this is the same call HttpClient makes for the SPN when
        // use_canonical_hostname is on, and on some machines it is not "localhost".
        canonicalHostname = InetAddress.getByName("localhost").getCanonicalHostName().toLowerCase(Locale.ROOT);

        kdc = startKdcWithRetries();
        kdc.createPrincipal(CLIENT_PRINCIPAL, CLIENT_PASSWORD);

        // Both spellings the client can derive - "localhost" with use_canonical_hostname off (the
        // default) and the canonical name with it on - so neither setting depends on what this
        // machine's resolver happens to answer.
        final Set<String> servicePrincipals = new LinkedHashSet<>();
        servicePrincipals.add("HTTP/localhost@" + REALM);
        servicePrincipals.add("HTTP/" + canonicalHostname + "@" + REALM);
        keytab = workDir.resolve("service.keytab").toFile();
        kdc.createAndExportPrincipals(keytab, servicePrincipals.toArray(new String[0]));

        acceptorSubject = loginAcceptor();
        acceptorCredential = createAcceptorCredential();
    }

    @AfterAll
    @Timeout(value = 60, unit = SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
    public static void stopKerberos() throws Exception {
        if (kdc != null) {
            kdc.stop();
            kdc = null;
        }
        if (originalKrb5Conf == null) {
            System.clearProperty(KRB5_CONF_PROPERTY);
        } else {
            System.setProperty(KRB5_CONF_PROPERTY, originalKrb5Conf);
        }
        if (originalLocale != null) {
            Locale.setDefault(originalLocale);
        }
        if (workDir != null) {
            try (var paths = Files.walk(workDir)) {
                paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
            workDir = null;
        }
    }

    @Override
    public void setUp(final TestInfo testInfo) throws Exception {
        super.setUp(testInfo);
        acceptedPrincipals.clear();
        acceptFailures.clear();
        ComponentUtil.register(new SystemHelper(), "systemHelper");
        final CrawlerStatsHelper crawlerStatsHelper = new CrawlerStatsHelper();
        crawlerStatsHelper.init();
        ComponentUtil.register(crawlerStatsHelper, "crawlerStatsHelper");
    }

    @Override
    public void tearDown(final TestInfo testInfo) throws Exception {
        ComponentUtil.setFessConfig(null);
        super.tearDown(testInfo);
    }

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    /**
     * The whole point: a 401 {@code Negotiate} challenge is answered with a ticket for the
     * configured principal and the retried request is served.
     */
    @Test
    @Timeout(value = 120, unit = SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_aNegotiateChallengeIsAnsweredWithATicketAndTheRequestSucceeds() throws Exception {
        try (SharePointMockServer server = negotiateServer()) {
            try (SharePointClient client =
                    SharePointClient.builder().setUrl(server.getBaseUrl()).setSite("test").setCredential(passwordCredential()).build();
                    GetFileResponse response = client.api().file().getFile().setServerRelativeUrl("/a.txt").execute()) {
                assertEquals("the file must be served once the handshake completes", "kerberos-ok",
                        new String(response.getFileContent().readAllBytes(), StandardCharsets.UTF_8));
            }

            assertEquals("the acceptor must have authenticated the configured principal exactly once", List.of(CLIENT_PRINCIPAL),
                    acceptedPrincipals);
            assertEquals("no token may have been rejected", List.of(), acceptFailures);

            final List<SharePointMockServer.RecordedRequest> requests = server.getRecordedRequests();
            assertEquals("the handshake must be a challenge and one authenticated retry", 2, requests.size());
            assertNull("the first request must go out unauthenticated, as HttpClient always does",
                    requests.get(0).getHeader("Authorization"));
            assertTrue("the retry must carry a Negotiate token", requests.get(1).getHeader("Authorization").startsWith("Negotiate "));
        }
    }

    /** The same handshake, authenticating from a keytab instead of a password. */
    @Test
    @Timeout(value = 120, unit = SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_aKeytabAuthenticatesTheSameWayAPasswordDoes() throws Exception {
        final File clientKeytab = workDir.resolve("client.keytab").toFile();
        kdc.exportPrincipal(CLIENT_PRINCIPAL, clientKeytab);

        try (SharePointMockServer server = negotiateServer()) {
            final KerberosCredential credential =
                    new KerberosCredential(CLIENT_PRINCIPAL, clientKeytab.getAbsolutePath(), null, null, true, false, false);
            try (SharePointClient client =
                    SharePointClient.builder().setUrl(server.getBaseUrl()).setSite("test").setCredential(credential).build();
                    GetFileResponse response = client.api().file().getFile().setServerRelativeUrl("/a.txt").execute()) {
                assertEquals("a keytab login must reach the same served file", "kerberos-ok",
                        new String(response.getFileContent().readAllBytes(), StandardCharsets.UTF_8));
            }
            assertEquals("the acceptor must have authenticated the configured principal", List.of(CLIENT_PRINCIPAL), acceptedPrincipals);
        }
    }

    /**
     * {@code auth.kerberos.use_canonical_hostname=true} must still complete the handshake.
     *
     * <p>How much this distinguishes depends on the machine: where
     * {@code InetAddress.getByName("localhost").getCanonicalHostName()} answers {@code "localhost"}
     * - which is the common case, and is the case on the machine this was written on - the SPN it
     * derives is the same one the default derives, so this only shows the flag does not break the
     * handshake. That the flag actually reaches the registered scheme is asserted deterministically,
     * with no DNS involved, by
     * {@code SharePointClientBuilderTest#test_theRegisteredSpnegoFactoryCarriesTheConfiguredHostnameFlags}.
     * Both SPNs are in the acceptor's keytab either way.
     */
    @Test
    @Timeout(value = 120, unit = SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_theCanonicalHostnameFlagIsHonouredByTheRegisteredScheme() throws Exception {
        try (SharePointMockServer server = negotiateServer()) {
            final KerberosCredential credential = new KerberosCredential(CLIENT_PRINCIPAL, null, CLIENT_PASSWORD, null, true, true, false);
            try (SharePointClient client =
                    SharePointClient.builder().setUrl(server.getBaseUrl()).setSite("test").setCredential(credential).build();
                    GetFileResponse response = client.api().file().getFile().setServerRelativeUrl("/a.txt").execute()) {
                assertEquals("a canonical-hostname SPN must authenticate against the same keytab", "kerberos-ok",
                        new String(response.getFileContent().readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    /**
     * Refutation test 1. The trap this task exists to close.
     *
     * <p>{@code AuthScope.ANY} - which is what {@code NtlmCredential} is registered under, and must
     * keep being registered under - scores as a match for a {@code Negotiate} challenge, so
     * HttpClient hands NTLM credentials to the SPNEGO scheme. {@code GGSSchemeBase} then finds they
     * are not {@code KerberosCredentials}, drops the {@code GSSCredential} to null and falls back
     * to the JVM's ambient ticket cache. Nothing logs the substitution.
     *
     * <p>Two things are asserted here, because together they show the failure is silent: the
     * request ends in a bare 401, and no {@code Authorization} header was ever put on the wire - so
     * there is not even a rejected attempt to find in a server log. That the credentials provider
     * really does hand NTLM credentials back for a {@code Negotiate} scope is pinned separately, in
     * {@code SharePointClientBuilderTest#test_ntlmCredentialsAreStillRegisteredForAnyScheme}, where
     * it needs no KDC. The loud failure that replaces all of it is
     * {@code SharePointCrawler#validate}, asserted last.
     */
    @Test
    @Timeout(value = 120, unit = SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_aNegotiateChallengeIsNotAnsweredWithNtlmCredentials() throws Exception {
        try (SharePointMockServer server = negotiateServer()) {
            // That the credentials provider really does hand these back for a Negotiate scope is
            // pinned separately, with no KDC involved, by
            // SharePointClientBuilderTest#test_ntlmCredentialsAreStillRegisteredForAnyScheme.
            try (SharePointClient client = SharePointClient.builder()
                    .setUrl(server.getBaseUrl())
                    .setSite("test")
                    .setCredential(new NtlmCredential("EXAMPLE\\fess", CLIENT_PASSWORD, null, null))
                    .build()) {
                final SharePointClientException e = assertThrows(SharePointClientException.class,
                        () -> client.api().file().getFile().setServerRelativeUrl("/a.txt").execute(),
                        "NTLM credentials cannot satisfy a Negotiate challenge");
                assertEquals("the crawl target fails with a bare 401", 401, e.getStatusCode());
            }

            assertEquals("no token reached the acceptor at all", List.of(), acceptedPrincipals);
            for (final SharePointMockServer.RecordedRequest request : server.getRecordedRequests()) {
                assertNull("nothing was even attempted - there is no rejected Authorization header to find in a log",
                        request.getHeader("Authorization"));
            }
        }

        // The loud failure that replaces the silent one: the combination never gets as far as a
        // request, because the crawler refuses to be constructed.
        final SharePointCrawler.CrawlerConfig config = new SharePointCrawler.CrawlerConfig();
        config.setUrl("http://localhost/");
        config.setSiteName("test");
        config.setNtlmUser("EXAMPLE\\fess");
        config.setKerberosPrincipal(CLIENT_PRINCIPAL);
        final ValidationException validationException = assertThrows(ValidationException.class, () -> new SharePointCrawler(config),
                "NTLM and Kerberos together must be rejected rather than silently colliding");
        assertTrue("the message must name both parameters so an operator can act on it",
                validationException.getMessage().contains("auth.kerberos.principal")
                        && validationException.getMessage().contains("auth.ntlm.user"));
    }

    /**
     * Refutation test 2. Without the SPNEGO scheme registration the request comes back as a plain
     * 401 with no {@code Authorization} header ever sent - HttpClient logs "Authentication scheme
     * negotiate not supported" at warn and moves on - so the only thing that distinguishes a
     * missing registration from a wrong password is that nothing was attempted at all.
     *
     * <p>This is also the negative control for
     * {@link #test_aNegotiateChallengeIsAnsweredWithATicketAndTheRequestSucceeds()}: the same
     * credential, the same server, and a 401 instead of a 200, which is what shows that test is
     * not passing because the mock server would have served anyone.
     */
    @Test
    @Timeout(value = 120, unit = SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_omittingTheSchemeRegistrationFailsLoudlyRatherThanSilently() throws Exception {
        try (SharePointMockServer server = negotiateServer()) {
            // A genuine Kerberos credential, correctly scoped, with only the scheme registration
            // taken away.
            final KerberosCredential delegate = passwordCredential();
            final SharePointCredential withoutSpnego = new SharePointCredential() {
                @Override
                public Credentials getCredential() {
                    return delegate.getCredential();
                }

                @Override
                public AuthScope getAuthScope() {
                    return delegate.getAuthScope();
                }

                @Override
                public Lookup<AuthSchemeProvider> getAuthSchemeRegistry() {
                    return RegistryBuilder.<AuthSchemeProvider> create()
                            .register(AuthSchemes.BASIC, new BasicSchemeFactory())
                            .register(AuthSchemes.NTLM, new NTLMSchemeFactory())
                            .build();
                }

                @Override
                public List<String> getPreferredAuthSchemes() {
                    return delegate.getPreferredAuthSchemes();
                }
            };

            try (SharePointClient client =
                    SharePointClient.builder().setUrl(server.getBaseUrl()).setSite("test").setCredential(withoutSpnego).build()) {
                final SharePointClientException e = assertThrows(SharePointClientException.class,
                        () -> client.api().file().getFile().setServerRelativeUrl("/a.txt").execute(),
                        "with no Negotiate scheme registered the challenge cannot be answered");
                assertEquals("the request comes back as a bare 401", 401, e.getStatusCode());
            }

            assertEquals("the credential was never used", List.of(), acceptedPrincipals);
            for (final SharePointMockServer.RecordedRequest request : server.getRecordedRequests()) {
                assertNull("and no Authorization header was ever sent, which is the only visible sign of the omission",
                        request.getHeader("Authorization"));
            }
        }
    }

    /**
     * The credential HttpClient is handed must be an {@link KerberosCredentials}, that exact type:
     * {@code GGSSchemeBase#generateGSSToken} checks for it with {@code instanceof} and silently
     * uses the ambient ticket cache instead when the check fails.
     */
    @Test
    @Timeout(value = 120, unit = SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_getCredentialReturnsKerberosCredentialsCarryingAGssCredential() throws Exception {
        final Credentials credentials = passwordCredential().getCredential();
        assertTrue("GGSSchemeBase only reads a GSSCredential out of this exact type", credentials instanceof KerberosCredentials);
        final GSSCredential gssCredential = ((KerberosCredentials) credentials).getGSSCredential();
        assertNotNull("the GSSCredential must be present, not left for the ambient ticket cache", gssCredential);
        assertEquals("it must be the configured principal's", CLIENT_PRINCIPAL, gssCredential.getName().toString());
    }

    /**
     * The whole {@code auth.kerberos.*} parameter set must reach the crawler and produce a working
     * client, which is the only place the parsing and the login are exercised together.
     */
    @Test
    @Timeout(value = 120, unit = SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_theDataStoreParametersReachAWorkingKerberosCrawl() throws Exception {
        try (SharePointMockServer server = negotiateServer()) {
            final DataStoreParams paramMap = new DataStoreParams();
            paramMap.put("url", server.getBaseUrl());
            paramMap.put("site.name", "test");
            paramMap.put("auth.kerberos.principal", CLIENT_PRINCIPAL);
            paramMap.put("auth.kerberos.password", CLIENT_PASSWORD);
            paramMap.put("auth.kerberos.strip_port", "true");
            paramMap.put("auth.kerberos.use_canonical_hostname", "false");
            paramMap.put("auth.kerberos.debug", "false");

            final SharePointDataStore dataStore = new SharePointDataStore();
            try (SharePointCrawler crawler = dataStore.createCrawler(paramMap)) {
                final SharePointCrawler.CrawlerConfig config = crawler.getCrawlerConfig();
                assertEquals("the principal must reach the config", CLIENT_PRINCIPAL, config.getKerberosPrincipal());
                assertEquals("the password must reach the config", CLIENT_PASSWORD, config.getKerberosPassword());
                assertTrue("strip_port must reach the config", config.isKerberosStripPort());
                assertFalse("use_canonical_hostname must reach the config", config.isKerberosUseCanonicalHostname());
                // Constructing the crawler already built the client, which means the login ran and
                // the ticket was obtained: a broken parameter path would have thrown by now.
                assertEquals("the crawl must have authenticated nothing yet - the ticket is obtained, not used, at build time", List.of(),
                        acceptedPrincipals);
            }
        }
    }

    /**
     * {@code auth.kerberos.strip_port} and {@code auth.kerberos.use_canonical_hostname} default to
     * {@code true} and {@code false}. {@code Boolean.parseBoolean} on a blank admin-UI field would
     * flip the first of those to {@code false} without a word.
     */
    @Test
    @Timeout(value = 120, unit = SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_blankKerberosFlagsKeepTheirDocumentedDefaults() throws Exception {
        try (SharePointMockServer server = negotiateServer()) {
            final DataStoreParams paramMap = new DataStoreParams();
            paramMap.put("url", server.getBaseUrl());
            paramMap.put("site.name", "test");
            paramMap.put("auth.kerberos.principal", CLIENT_PRINCIPAL);
            paramMap.put("auth.kerberos.password", CLIENT_PASSWORD);
            // What the admin UI produces from a field left empty rather than removed.
            paramMap.put("auth.kerberos.strip_port", "");
            paramMap.put("auth.kerberos.use_canonical_hostname", "");

            final SharePointDataStore dataStore = new SharePointDataStore();
            try (SharePointCrawler crawler = dataStore.createCrawler(paramMap)) {
                assertTrue("a blank strip_port must stay at its documented default of true",
                        crawler.getCrawlerConfig().isKerberosStripPort());
                assertFalse("a blank use_canonical_hostname must stay at its documented default of false",
                        crawler.getCrawlerConfig().isKerberosUseCanonicalHostname());
            }
        }
    }

    /**
     * {@code java.security.krb5.conf} is JVM-global and one crawler JVM runs every data config of a
     * crawl job, so a data config that names a krb5.conf must not overwrite one already in place.
     */
    @Test
    @Timeout(value = 120, unit = SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_anAlreadySetKrb5ConfIsNeverOverwritten() throws Exception {
        // The KDC's own krb5.conf is already in this property - that is what makes the rest of
        // this class work - so this is the real "already set" case.
        final String inPlace = System.getProperty(KRB5_CONF_PROPERTY);
        assertNotNull("the in-process KDC must have set the property for this test to mean anything", inPlace);

        final KerberosCredential credential = new KerberosCredential(CLIENT_PRINCIPAL, null, CLIENT_PASSWORD,
                workDir.resolve("not-a-real-krb5.conf").toString(), true, false, false);
        credential.getCredential();

        assertEquals("a data config's auth.kerberos.krb5_conf must not replace what is already set", inPlace,
                System.getProperty(KRB5_CONF_PROPERTY));
    }

    /**
     * A blank principal must be rejected where the credential is built.
     *
     * <p>{@code Krb5LoginModule} with no {@code principal} option and no ticket cache falls back to
     * the {@code user.name} system property, so this would otherwise try to authenticate as
     * whichever OS account the crawler process runs under - a different identity, chosen silently.
     * The data store cannot reach this today because {@code auth.kerberos.principal} is what
     * enables Kerberos at all, but {@code KerberosCredential} is a public class.
     */
    @Test
    @Timeout(value = 120, unit = SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_aBlankPrincipalIsRejectedRatherThanFallingBackToTheOsUser() throws Exception {
        for (final String blank : new String[] { null, "", "   " }) {
            final SharePointClientException e = assertThrows(SharePointClientException.class,
                    () -> new KerberosCredential(blank, null, "password", null, true, false, false), "a blank principal must be rejected");
            assertTrue("the message must name the parameter an operator has to set", e.getMessage().contains("auth.kerberos.principal"));
        }
    }

    /** A wrong password must fail with a message naming the principal, not hang or return null. */
    @Test
    @Timeout(value = 120, unit = SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_aBadPasswordFailsWithAMessageNamingThePrincipal() throws Exception {
        final KerberosCredential credential = new KerberosCredential(CLIENT_PRINCIPAL, null, "wrong-password", null, true, false, false);
        final SharePointClientException e =
                assertThrows(SharePointClientException.class, credential::getCredential, "a bad password must fail loudly");
        assertTrue("the message must name the principal that failed to log in", e.getMessage().contains(CLIENT_PRINCIPAL));
    }

    // ===== harness =====

    /** A password-based credential for {@link #CLIENT_PRINCIPAL} with the documented defaults. */
    private static KerberosCredential passwordCredential() {
        return new KerberosCredential(CLIENT_PRINCIPAL, null, CLIENT_PASSWORD, null, true, false, false);
    }

    /** A started mock SharePoint that demands {@code Negotiate} and serves one file. */
    private static SharePointMockServer negotiateServer() throws Exception {
        final SharePointMockServer server = new SharePointMockServer();
        server.onPathStatus(FILE_PATH, 200, "text/plain", "kerberos-ok");
        server.requireAuthorization("Negotiate", KerberosAuthenticationTest::acceptNegotiate);
        server.start();
        return server;
    }

    /**
     * The GSS-API acceptor side of the handshake.
     *
     * @param authorization the request's {@code Authorization} header, or null
     * @return the {@code WWW-Authenticate} value for an accepted request, or null to challenge
     */
    private static String acceptNegotiate(final String authorization) {
        if (authorization == null || !authorization.startsWith("Negotiate ")) {
            return null;
        }
        final byte[] token = Base64.getDecoder().decode(authorization.substring("Negotiate ".length()).trim());
        try {
            final GSSContext context = Subject.callAs(acceptorSubject, () -> GSSManager.getInstance().createContext(acceptorCredential));
            try {
                final byte[] out = Subject.callAs(acceptorSubject, () -> context.acceptSecContext(token, 0, token.length));
                acceptedPrincipals.add(context.getSrcName().toString());
                return out == null || out.length == 0 ? "" : "Negotiate " + Base64.getEncoder().encodeToString(out);
            } finally {
                context.dispose();
            }
        } catch (final Exception e) {
            // Recorded rather than swallowed: a rejected token and a request that never carried
            // one both come back as 401, and only this tells them apart.
            acceptFailures.add(e);
            return null;
        }
    }

    /**
     * Starts the KDC, retrying the bind.
     *
     * <p>Kerby picks its port by opening a {@code ServerSocket(0)}, reading the number and closing
     * it again before the KDC binds, so under load something else can take the port in between.
     *
     * @return the started KDC
     * @throws Exception if it will not start after {@link #KDC_START_ATTEMPTS} attempts
     */
    private static SimpleKdcServer startKdcWithRetries() throws Exception {
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= KDC_START_ATTEMPTS; attempt++) {
            final SimpleKdcServer candidate = new SimpleKdcServer();
            candidate.setKdcRealm(REALM);
            candidate.setKdcHost("localhost");
            // Writes udp_preference_limit = 1 into the generated krb5.conf. Without it the JDK's
            // KdcComm tries UDP first and waits out three 30-second timeouts before falling back
            // to TCP, which looks exactly like a hung test.
            candidate.setAllowUdp(false);
            candidate.setKdcTcpPort(freePort());
            candidate.setWorkDir(workDir.toFile());
            try {
                candidate.init();
                candidate.start();
                return candidate;
            } catch (final Exception e) {
                lastFailure = e;
                try {
                    candidate.stop();
                } catch (final Exception ignore) {
                    // Nothing to do: the KDC never came up.
                }
            }
        }
        throw new IllegalStateException("The test KDC did not start in " + KDC_START_ATTEMPTS + " attempts.", lastFailure);
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /**
     * Logs the acceptor in against the whole keytab rather than one principal, so the
     * {@code GSSName} handed to {@code createCredential} can be null and any SPN in the keytab is
     * accepted. That takes server-side host-name canonicalization out of the picture and leaves
     * the client - the code under test - as the only host-name-sensitive party.
     *
     * @return the acceptor's subject
     * @throws Exception if the login fails
     */
    private static Subject loginAcceptor() throws Exception {
        final Map<String, String> options = new HashMap<>();
        options.put("principal", "*");
        options.put("useKeyTab", "true");
        options.put("keyTab", keytab.getAbsolutePath());
        options.put("storeKey", "true");
        options.put("isInitiator", "false");
        options.put("doNotPrompt", "true");
        options.put("refreshKrb5Config", "true");
        final LoginContext loginContext = new LoginContext("fess-ds-sharepoint-test-acceptor", new Subject(), callbacks -> {
            throw new UnsupportedOperationException("The acceptor login must never prompt.");
        }, new TestJaasConfiguration(options));
        loginContext.login();
        return loginContext.getSubject();
    }

    private static GSSCredential createAcceptorCredential() throws Exception {
        final Oid spnegoOid = new Oid(SPNEGO_OID);
        final GSSManager manager = GSSManager.getInstance();
        return Subject.callAs(acceptorSubject,
                () -> manager.createCredential(null, GSSCredential.INDEFINITE_LIFETIME, spnegoOid, GSSCredential.ACCEPT_ONLY));
    }

    /** The test's own copy of the production JAAS configuration, which is package-private. */
    private static final class TestJaasConfiguration extends javax.security.auth.login.Configuration {
        private final Map<String, String> options;

        TestJaasConfiguration(final Map<String, String> options) {
            this.options = new HashMap<>(options);
        }

        @Override
        public javax.security.auth.login.AppConfigurationEntry[] getAppConfigurationEntry(final String name) {
            return new javax.security.auth.login.AppConfigurationEntry[] {
                    new javax.security.auth.login.AppConfigurationEntry("com.sun.security.auth.module.Krb5LoginModule",
                            javax.security.auth.login.AppConfigurationEntry.LoginModuleControlFlag.REQUIRED, options) };
        }
    }
}
