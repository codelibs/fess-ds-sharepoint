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

public class NtlmCredential implements SharePointCredential {
    private final String user;
    private final String password;
    private final String hostName;
    private final String domain;

    public NtlmCredential(final String user, final String password, final String hostName, final String domain) {
        this.user = user;
        this.password = password;
        this.hostName = hostName;
        this.domain = domain;
    }

    @Override
    public Credentials getCredential() {
        return new NTCredentials(user, password, hostName, domain);
    }

}
