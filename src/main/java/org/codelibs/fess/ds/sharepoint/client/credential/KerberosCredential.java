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
package org.codelibs.fess.ds.sharepoint.client.credential;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.KerberosCredentials;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.config.Lookup;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.auth.DigestSchemeFactory;
import org.apache.http.impl.auth.KerberosSchemeFactory;
import org.apache.http.impl.auth.NTLMSchemeFactory;
import org.apache.http.impl.auth.SPNegoSchemeFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.Oid;

/**
 * Kerberos (SPNEGO) authentication credential for an on-premises SharePoint farm.
 *
 * <p>Logs in with {@code Krb5LoginModule} - from a keytab or a password - and hands Apache
 * HttpClient the resulting ticket-granting ticket as an explicit {@link GSSCredential}.
 *
 * <p>Two details here are load-bearing and easy to undo by accident:
 *
 * <ul>
 * <li>{@link #getCredential()} returns an {@link KerberosCredentials}, that exact type.
 * {@code GGSSchemeBase#generateGSSToken} uses the {@link GSSCredential} only when the credentials
 * it is handed are an instance of it, and otherwise sets the credential to {@code null} and falls
 * back to the JVM's ambient ticket cache - which in a crawler process is normally empty. It does
 * that silently.</li>
 * <li>{@link #getAuthScope()} narrows these credentials to {@link AuthSchemes#SPNEGO} rather than
 * {@link AuthScope#ANY}, so a {@code Basic} or {@code Digest} challenge from something in front of
 * the farm is never answered with a Kerberos credential that cannot satisfy it.</li>
 * </ul>
 *
 * <p>Supported envelope: one crawler JVM, one {@code krb5.conf} per Fess instance, keytab or
 * password, no delegation and no channel binding. Mutually exclusive with NTLM and OAuth -
 * {@code SharePointCrawler#validate} rejects a configuration that sets more than one of them,
 * because registering two of them together is exactly the silent failure described above.
 *
 * @see SharePointCredential
 * @see KerberosCredentials
 */
public class KerberosCredential implements SharePointCredential {

    private static final Logger logger = LogManager.getLogger(KerberosCredential.class);

    /** The SPNEGO mechanism OID (RFC 4178). */
    private static final String SPNEGO_OID = "1.3.6.1.5.5.2";

    /** The JVM-global system property naming the Kerberos configuration file. */
    private static final String KRB5_CONF_PROPERTY = "java.security.krb5.conf";

    /**
     * The application name passed to {@link LoginContext}. Never looked up in a JAAS configuration
     * file: the configuration is passed to the constructor explicitly, and it answers every name
     * with the same entry.
     */
    private static final String LOGIN_CONTEXT_NAME = "fess-ds-sharepoint";

    /** The client principal, e.g. {@code svc-fess@EXAMPLE.COM}. */
    private final String principal;

    /** Path to a keytab holding a key for {@link #principal}, or null to use {@link #password}. */
    private final String keytab;

    /** The principal's password, used only when no keytab is configured. */
    private final String password;

    /** Path to a krb5.conf, applied only when the system property is not already set. */
    private final String krb5Conf;

    /** Whether the port is stripped from the service principal name. */
    private final boolean stripPort;

    /** Whether the target host is resolved to its canonical name for the service principal name. */
    private final boolean useCanonicalHostname;

    /** Whether {@code Krb5LoginModule} writes its debug output to standard output. */
    private final boolean debug;

    /**
     * Constructs a Kerberos credential.
     *
     * @param principal the client principal, written as {@code user@REALM} so it does not depend
     *            on the JVM-global default realm
     * @param keytab path to a keytab holding a key for {@code principal}, or null/blank to
     *            authenticate with {@code password}
     * @param password the principal's password, used only when {@code keytab} is null/blank
     * @param krb5Conf path to a krb5.conf to set {@code java.security.krb5.conf} to, or null/blank to
     *            leave that property alone; an already-set property is never overwritten
     * @param stripPort whether to strip the port from the service principal name
     * @param useCanonicalHostname whether to resolve the target host to its canonical name when
     *            building the service principal name
     * @param debug whether {@code Krb5LoginModule} writes its debug output to standard output
     * @throws SharePointClientException if {@code principal} is null or blank
     */
    public KerberosCredential(final String principal, final String keytab, final String password, final String krb5Conf,
            final boolean stripPort, final boolean useCanonicalHostname, final boolean debug) {
        if (StringUtils.isBlank(principal)) {
            // Not merely unhelpful: Krb5LoginModule with no principal option and no ticket cache
            // falls back to the "user.name" system property, so a blank one would authenticate as
            // whichever OS account the crawler process runs under - a different identity, silently.
            throw new SharePointClientException("auth.kerberos.principal is required, and must be written as user@REALM.");
        }
        this.principal = principal;
        this.keytab = keytab;
        this.password = password;
        this.krb5Conf = krb5Conf;
        this.stripPort = stripPort;
        this.useCanonicalHostname = useCanonicalHostname;
        this.debug = debug;
    }

    /**
     * Logs in and returns the ticket-granting ticket wrapped as HttpClient credentials.
     *
     * <p>Called once per crawl, when the HTTP client is built. The lifetime of the returned
     * credential is the lifetime of the ticket the KDC issued; this connector does not renew it, so
     * a crawl longer than the ticket lifetime starts failing to authenticate partway through.
     *
     * @return the credentials, always an {@link KerberosCredentials}
     * @throws SharePointClientException if the login or the credential acquisition fails
     */
    @Override
    public Credentials getCredential() {
        applyKrb5Conf();
        return new KerberosCredentials(createGssCredential(login()));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Narrowed to {@link AuthSchemes#SPNEGO}. At {@link AuthScope#ANY} these credentials would
     * also be handed back for a {@code Basic} or {@code Digest} challenge, which they cannot
     * answer.
     */
    @Override
    public AuthScope getAuthScope() {
        return new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, AuthScope.ANY_REALM, AuthSchemes.SPNEGO);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Apache HttpClient's own default registry is reproduced entry for entry, with only the
     * SPNEGO and Kerberos factories replaced by ones carrying the configured {@code stripPort} and
     * {@code useCanonicalHostname}. Registering the configured factories alone would silently drop
     * {@code Basic}, {@code Digest} and {@code NTLM}, which a proxy in front of the farm may still
     * challenge with.
     */
    @Override
    public Lookup<AuthSchemeProvider> getAuthSchemeRegistry() {
        return RegistryBuilder.<AuthSchemeProvider> create()
                .register(AuthSchemes.BASIC, new BasicSchemeFactory())
                .register(AuthSchemes.DIGEST, new DigestSchemeFactory())
                .register(AuthSchemes.NTLM, new NTLMSchemeFactory())
                .register(AuthSchemes.SPNEGO, new SPNegoSchemeFactory(stripPort, useCanonicalHostname))
                .register(AuthSchemes.KERBEROS, new KerberosSchemeFactory(stripPort, useCanonicalHostname))
                .build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code Negotiate} only. Apache HttpClient's default order already tries it first, so this
     * changes nothing about which scheme wins a Negotiate challenge; what it does is stop any other
     * challenge on the same host from being attempted with a credential that is not registered for
     * it.
     */
    @Override
    public List<String> getPreferredAuthSchemes() {
        return List.of(AuthSchemes.SPNEGO);
    }

    /**
     * Points {@code java.security.krb5.conf} at the configured krb5.conf, but only when nothing has
     * set it yet.
     *
     * <p>The property is JVM-global and one crawler JVM runs every data config of a crawl job, so
     * overwriting a value another data config - or {@code jvm.crawler.options} - already set would
     * change the Kerberos configuration of every other crawl in that process.
     */
    private void applyKrb5Conf() {
        if (StringUtils.isBlank(krb5Conf)) {
            return;
        }
        final String current = System.getProperty(KRB5_CONF_PROPERTY);
        if (StringUtils.isNotBlank(current)) {
            if (!current.equals(krb5Conf)) {
                logger.warn(
                        "auth.kerberos.krb5_conf is \"{}\", but {} is already set to \"{}\" and is left as it is. One crawler JVM runs"
                                + " every data config of a crawl job, so overwriting it would change the Kerberos configuration of the"
                                + " others. Set it once with -D{} in jvm.crawler.options instead.",
                        krb5Conf, KRB5_CONF_PROPERTY, current, KRB5_CONF_PROPERTY);
            }
            return;
        }
        System.setProperty(KRB5_CONF_PROPERTY, krb5Conf);
    }

    /**
     * Runs the {@code Krb5LoginModule} login and returns the authenticated subject.
     *
     * <p>The configuration is passed to the four-argument {@link LoginContext} constructor rather
     * than installed with {@code Configuration.setConfiguration}, so it applies to this login only.
     *
     * @return the subject holding the ticket-granting ticket
     * @throws SharePointClientException if the login fails
     */
    private Subject login() {
        final Map<String, String> options = new HashMap<>();
        options.put("principal", principal);
        // Re-reads krb5.conf, so a value applyKrb5Conf just set is picked up rather than whatever
        // the Config singleton happened to load first.
        options.put("refreshKrb5Config", "true");
        options.put("storeKey", "true");
        options.put("isInitiator", "true");
        options.put("debug", Boolean.toString(debug));
        if (StringUtils.isNotBlank(keytab)) {
            options.put("useKeyTab", "true");
            options.put("keyTab", keytab);
            // No password is involved, so nothing can prompt.
            options.put("doNotPrompt", "true");
        } else {
            options.put("useKeyTab", "false");
            // A password login cannot set doNotPrompt=true: Krb5LoginModule#validateConfiguration
            // rejects that combination up front - "either doNotPrompt should be false or at least
            // one of useTicketCache, useKeyTab, tryFirstPass and useFirstPass should be true" -
            // before login() does anything at all, and storeKey=true trips a second check saying
            // the same. So it is not a matter of whether a callback handler would be consulted;
            // the configuration is refused. Standard input stays unreachable regardless: the
            // handler below answers the name and password callbacks from the configured values
            // and rejects every other callback, and useTicketCache is left off so no ambient
            // cache is consulted either.
            options.put("doNotPrompt", "false");
        }
        try {
            final LoginContext loginContext = new LoginContext(LOGIN_CONTEXT_NAME, new Subject(), new ConfiguredCallbackHandler(),
                    new StaticJaasConfiguration(options));
            loginContext.login();
            return loginContext.getSubject();
        } catch (final LoginException e) {
            throw new SharePointClientException("Failed to log in to Kerberos as " + principal + ".", e);
        }
    }

    /**
     * Acquires the SPNEGO initiator credential from the logged-in subject.
     *
     * <p>Only this call is wrapped in {@link Subject#callAs}: JGSS resolves the ticket through
     * {@code Subject.current()}, so the subject has to be current while the credential is created.
     * The credential is then explicit, so neither HttpClient's {@code execute()} nor
     * {@code javax.security.auth.useSubjectCredsOnly=false} - a JVM-global property - is needed
     * afterwards.
     *
     * @param subject the subject returned by {@link #login()}
     * @return the initiator credential
     * @throws SharePointClientException if the credential cannot be created
     */
    private GSSCredential createGssCredential(final Subject subject) {
        try {
            final Oid spnegoOid = new Oid(SPNEGO_OID);
            final GSSManager manager = GSSManager.getInstance();
            // A null GSSName means "the default initiator in this subject", which is the principal
            // just logged in.
            return Subject.callAs(subject,
                    () -> manager.createCredential(null, GSSCredential.DEFAULT_LIFETIME, spnegoOid, GSSCredential.INITIATE_ONLY));
        } catch (final GSSException e) {
            throw new SharePointClientException("Failed to create a Kerberos credential for " + principal + ".", e);
        } catch (final CompletionException e) {
            throw new SharePointClientException("Failed to create a Kerberos credential for " + principal + ".", e.getCause());
        }
    }

    /**
     * Answers {@code Krb5LoginModule}'s name and password callbacks from the configured values.
     *
     * <p>Anything else is rejected with {@link UnsupportedCallbackException}, which surfaces as a
     * {@link LoginException}. Nothing here can read standard input.
     */
    private class ConfiguredCallbackHandler implements CallbackHandler {
        @Override
        public void handle(final Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            for (final Callback callback : callbacks) {
                if (callback instanceof final NameCallback nameCallback) {
                    nameCallback.setName(principal);
                } else if (callback instanceof final PasswordCallback passwordCallback) {
                    passwordCallback.setPassword(password == null ? new char[0] : password.toCharArray());
                } else {
                    throw new UnsupportedCallbackException(callback, "Unsupported callback for a Kerberos login.");
                }
            }
        }
    }
}
