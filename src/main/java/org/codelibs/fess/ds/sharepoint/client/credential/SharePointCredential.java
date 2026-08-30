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

import java.util.List;

import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.config.Lookup;

/**
 * Interface for SharePoint authentication credentials.
 * Implementations of this interface provide different authentication mechanisms
 * for connecting to SharePoint services (e.g., NTLM, OAuth).
 *
 * @see NtlmCredential
 * @see KerberosCredential
 */
public interface SharePointCredential {
    /**
     * Gets the Apache HttpClient credentials object for SharePoint authentication.
     *
     * @return credentials object suitable for HTTP authentication
     */
    Credentials getCredential();

    /**
     * The scope {@link #getCredential()} is registered under.
     *
     * <p>Defaults to {@link AuthScope#ANY}, which is what every credential registered by this
     * connector used before a scheme-specific one existed. That default is deliberately kept:
     * credentials registered at {@code ANY} answer a {@code Basic} or {@code Digest} challenge as
     * well as the scheme they were meant for, and an installation pointing {@code auth.ntlm.user}
     * at a SharePoint fronted by basic authentication depends on exactly that. Narrowing it would
     * stop such an installation working with no error to explain why.
     *
     * @return the scope these credentials answer
     */
    default AuthScope getAuthScope() {
        return AuthScope.ANY;
    }

    /**
     * The authentication scheme registry the HTTP client is built with, replacing Apache
     * HttpClient's default one, or {@code null} to keep that default.
     *
     * @return the scheme registry to install, or {@code null}
     */
    default Lookup<AuthSchemeProvider> getAuthSchemeRegistry() {
        return null;
    }

    /**
     * The authentication schemes to prefer for the target host, in order, or {@code null} to keep
     * Apache HttpClient's default order.
     *
     * @return the preferred scheme names, or {@code null}
     */
    default List<String> getPreferredAuthSchemes() {
        return null;
    }
}
