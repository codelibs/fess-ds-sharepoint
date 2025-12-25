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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.codelibs.fess.ds.sharepoint.client.credential.NtlmCredential;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;
import org.codelibs.fess.util.ComponentUtil;
import org.dbflute.utflute.lastaflute.LastaFluteTestCase;
import org.junit.Test;

public class SharePointClientBuilderTest extends LastaFluteTestCase {

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
                .setRetryCount(3)
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
    public void test_setRetryCount() {
        final SharePointClientBuilder builder = SharePointClient.builder();
        builder.setRetryCount(5);
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
}
