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

import org.junit.jupiter.api.TestInfo;

import org.apache.http.auth.Credentials;
import org.apache.http.auth.NTCredentials;
import org.codelibs.fess.util.ComponentUtil;
import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.junit.jupiter.api.Test;

public class NtlmCredentialTest extends UnitDsTestCase {

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

    /**
     * The third and fourth constructor arguments are workstation and domain, in that order, which
     * is the order {@link NTCredentials} itself takes them in. Swapping them compiles, passes any
     * test that only checks the username, and fails only against a real domain controller.
     */
    @Test
    public void test_getCredential_workstationAndDomainLandInTheRightPositions() {
        final NTCredentials credentials =
                (NTCredentials) new NtlmCredential("fess", "password", "CRAWLER01", "example.com").getCredential();

        // NTCredentials uppercases both.
        assertEquals("the fourth argument is the domain", "EXAMPLE.COM", credentials.getDomain());
        assertEquals("the third argument is the workstation", "CRAWLER01", credentials.getWorkstation());
        assertEquals("and the user name is left alone", "fess", credentials.getUserName());
    }

    /**
     * What every installation that has never set {@code auth.ntlm.domain} or
     * {@code auth.ntlm.workstation} gets, which must be exactly what it got before those
     * parameters existed.
     */
    @Test
    public void test_getCredential_nullDomainAndWorkstationAreTheUnchangedDefault() {
        final NTCredentials credentials = (NTCredentials) new NtlmCredential("EXAMPLE\\fess", "password", null, null).getCredential();

        assertNull("no domain is sent", credentials.getDomain());
        assertNull("no workstation is sent", credentials.getWorkstation());
        // NTCredentials does not split this; the whole string goes out as the NTLM user name,
        // exactly as it always has.
        assertEquals("a DOMAIN\\user username is passed through unchanged", "EXAMPLE\\fess", credentials.getUserName());
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
