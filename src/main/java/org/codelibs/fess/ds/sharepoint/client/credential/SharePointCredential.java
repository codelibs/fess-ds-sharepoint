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

/**
 * Interface for SharePoint authentication credentials.
 * Implementations of this interface provide different authentication mechanisms
 * for connecting to SharePoint services (e.g., NTLM, OAuth).
 *
 * @see NtlmCredential
 */
public interface SharePointCredential {
    /**
     * Gets the Apache HttpClient credentials object for SharePoint authentication.
     *
     * @return credentials object suitable for HTTP authentication
     */
    Credentials getCredential();
}
