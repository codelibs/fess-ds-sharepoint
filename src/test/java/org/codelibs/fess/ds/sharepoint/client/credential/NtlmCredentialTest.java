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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.apache.http.auth.Credentials;
import org.apache.http.auth.NTCredentials;
import org.codelibs.fess.util.ComponentUtil;
import org.dbflute.utflute.lastaflute.LastaFluteTestCase;
import org.junit.Test;

public class NtlmCredentialTest extends LastaFluteTestCase {

    @Override
    protected String prepareConfigFile() {
        return "test_app.xml";
    }

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Override
    public void tearDown() throws Exception {
        ComponentUtil.setFessConfig(null);
        super.tearDown();
    }

    @Test
    public void test_getCredential() {
        final NtlmCredential ntlmCredential = new NtlmCredential("testuser", "testpassword", "hostname", "domain");
        final Credentials credential = ntlmCredential.getCredential();

        assertNotNull(credential);
        assertTrue(credential instanceof NTCredentials);
    }

    @Test
    public void test_getCredential_withNTCredentials() {
        final NtlmCredential ntlmCredential = new NtlmCredential("user1", "pass1", "host1", "domain1");
        final Credentials credential = ntlmCredential.getCredential();

        final NTCredentials ntCredentials = (NTCredentials) credential;
        assertEquals("user1", ntCredentials.getUserName());
        assertEquals("pass1", ntCredentials.getPassword());
    }

    @Test
    public void test_getCredential_multipleInstances() {
        final NtlmCredential credential1 = new NtlmCredential("user1", "pass1", "host1", "domain1");
        final NtlmCredential credential2 = new NtlmCredential("user2", "pass2", "host2", "domain2");

        final Credentials cred1 = credential1.getCredential();
        final Credentials cred2 = credential2.getCredential();

        assertNotNull(cred1);
        assertNotNull(cred2);

        final NTCredentials ntCred1 = (NTCredentials) cred1;
        final NTCredentials ntCred2 = (NTCredentials) cred2;

        assertEquals("user1", ntCred1.getUserName());
        assertEquals("user2", ntCred2.getUserName());
    }

    @Test
    public void test_implementsSharePointCredential() {
        final NtlmCredential ntlmCredential = new NtlmCredential("user", "password", "hostname", "domain");
        assertTrue(ntlmCredential instanceof SharePointCredential);
    }

    @Test
    public void test_getCredential_withEmptyValues() {
        final NtlmCredential ntlmCredential = new NtlmCredential("", "", "", "");
        final Credentials credential = ntlmCredential.getCredential();

        assertNotNull(credential);
        assertTrue(credential instanceof NTCredentials);
    }

    @Test
    public void test_getCredential_withSpecialCharacters() {
        final NtlmCredential ntlmCredential = new NtlmCredential("user@domain.com", "p@ssw0rd!", "host-name", "test.domain");
        final Credentials credential = ntlmCredential.getCredential();

        assertNotNull(credential);
        assertTrue(credential instanceof NTCredentials);
    }
}
