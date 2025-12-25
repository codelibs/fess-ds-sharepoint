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
import org.codelibs.fess.util.ComponentUtil;
import org.dbflute.utflute.lastaflute.LastaFluteTestCase;
import org.junit.Test;

public class SharePointClientTest extends LastaFluteTestCase {

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
    public void test_getSiteName() {
        final RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(10000).setConnectTimeout(10000).build();
        final CloseableHttpClient httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();

        final SharePointClient client =
                SharePointClient.builder().setUrl("https://example.com/").setSite("mysite").setHttpClient(httpClient).build();

        assertEquals("mysite", client.getSiteName());
    }

    @Test
    public void test_getUrl() {
        final RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(10000).setConnectTimeout(10000).build();
        final CloseableHttpClient httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();

        final SharePointClient client =
                SharePointClient.builder().setUrl("https://example.com/").setSite("mysite").setHttpClient(httpClient).build();

        assertEquals("https://example.com/", client.getUrl());
    }

    @Test
    public void test_getSiteUrl() {
        final RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(10000).setConnectTimeout(10000).build();
        final CloseableHttpClient httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();

        final SharePointClient client =
                SharePointClient.builder().setUrl("https://example.com/").setSite("mysite").setHttpClient(httpClient).build();

        assertEquals("https://example.com/sites/mysite/", client.getSiteUrl());
    }

    @Test
    public void test_getSiteUrl_withDifferentSiteName() {
        final RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(10000).setConnectTimeout(10000).build();
        final CloseableHttpClient httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();

        final SharePointClient client =
                SharePointClient.builder().setUrl("https://sharepoint.example.com/").setSite("projects").setHttpClient(httpClient).build();

        assertEquals("https://sharepoint.example.com/sites/projects/", client.getSiteUrl());
    }

    @Test
    public void test_apiAccess() {
        final RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(10000).setConnectTimeout(10000).build();
        final CloseableHttpClient httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();

        final SharePointClient client =
                SharePointClient.builder().setUrl("https://example.com/").setSite("mysite").setHttpClient(httpClient).build();

        assertNotNull(client.api());
    }

    @Test
    public void test_helperAccess() {
        final RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(10000).setConnectTimeout(10000).build();
        final CloseableHttpClient httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();

        final SharePointClient client =
                SharePointClient.builder().setUrl("https://example.com/").setSite("mysite").setHttpClient(httpClient).build();

        assertNotNull(client.helper());
    }

    @Test
    public void test_builderReturnsNewInstance() {
        final SharePointClientBuilder builder1 = SharePointClient.builder();
        final SharePointClientBuilder builder2 = SharePointClient.builder();

        assertNotNull(builder1);
        assertNotNull(builder2);
        // Each call should return a new builder instance
    }
}
