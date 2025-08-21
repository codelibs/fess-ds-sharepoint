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

import org.apache.http.auth.Credentials;
import org.apache.http.auth.NTCredentials;

/**
 * NTLM authentication credential implementation for SharePoint authentication.
 * This class encapsulates NTLM authentication parameters including username,
 * password, hostname, and domain information required for Windows authentication.
 *
 * @see SharePointCredential
 * @see NTCredentials
 */
public class NtlmCredential implements SharePointCredential {
    /** The username for NTLM authentication */
    private final String user;
    /** The password for NTLM authentication */
    private final String password;
    /** The hostname for NTLM authentication */
    private final String hostName;
    /** The domain for NTLM authentication */
    private final String domain;

    /**
     * Constructs a new NtlmCredential instance.
     *
     * @param user the username for NTLM authentication
     * @param password the password for NTLM authentication
     * @param hostName the hostname for NTLM authentication
     * @param domain the domain for NTLM authentication
     */
    public NtlmCredential(final String user, final String password, final String hostName, final String domain) {
        this.user = user;
        this.password = password;
        this.hostName = hostName;
        this.domain = domain;
    }

    /**
     * Gets the NTLM credentials for SharePoint authentication.
     *
     * @return NTCredentials instance configured with the provided authentication parameters
     */
    @Override
    public Credentials getCredential() {
        return new NTCredentials(user, password, hostName, domain);
    }

}
