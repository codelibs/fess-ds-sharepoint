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
package org.codelibs.fess.ds.sharepoint.client.oauth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.apache.http.client.methods.HttpGet;
import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.junit.jupiter.api.Test;

public class OAuthTest extends UnitDsTestCase {

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    /**
     * Construction now also logs a deprecation warning about Azure ACS; this pins that the
     * warning is a side effect only and does not change what the constructor accepts.
     */
    @Test
    public void test_constructor_doesNotThrow() {
        assertDoesNotThrow(() -> new OAuth("clientId", "clientSecret", "tenant", "realm"),
                "logging the ACS deprecation warning must not affect construction");
    }

    @Test
    public void test_apply_addsABearerAuthorizationHeader() {
        final OAuth oAuth = new OAuth("clientId", "clientSecret", "tenant", "realm");
        final HttpGet httpGet = new HttpGet("http://example.com/");

        oAuth.apply(httpGet);

        assertNotNull("apply must add an Authorization header", httpGet.getFirstHeader("Authorization"));
        assertTrue("the header must be a Bearer token", httpGet.getFirstHeader("Authorization").getValue().startsWith("Bearer "));
    }
}
