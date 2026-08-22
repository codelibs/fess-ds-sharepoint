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
package org.codelibs.fess.ds.sharepoint.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.junit.jupiter.api.Test;

public class SharePointMockServerTest extends UnitDsTestCase {

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Test
    public void test_servesFixtureAndRecordsRequest() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPath("/sites/test/_api/lists", "application/json", "fixtures/modern/lists.json");
            server.withHeader("X-SharePointHealthScore", "0");
            server.start();

            final HttpClient httpClient = HttpClient.newHttpClient();
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(server.getBaseUrl() + "sites/test/_api/lists?%24top=10"))
                    .header("Accept", "application/json")
                    .build();
            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertEquals("0", response.headers().firstValue("X-SharePointHealthScore").orElse(null));
            assertTrue("body should contain the list title", response.body().contains("Shared Documents"));

            assertEquals(1, server.getRecordedRequests().size());
            final SharePointMockServer.RecordedRequest recorded = server.getRecordedRequests().get(0);
            assertEquals("GET", recorded.getMethod());
            assertEquals("/sites/test/_api/lists", recorded.getPath());
            assertEquals("%24top=10", recorded.getQuery());
            assertEquals("application/json", recorded.getHeader("Accept"));
        }
    }

    @Test
    public void test_unregisteredPathReturns404() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.start();

            final HttpClient httpClient = HttpClient.newHttpClient();
            final HttpRequest request = HttpRequest.newBuilder().uri(URI.create(server.getBaseUrl() + "sites/test/_api/nope")).build();
            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(404, response.statusCode());
        }
    }

    @Test
    public void test_onPathStatusReturnsGivenStatus() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus("/sites/test/_api/lists", 503, "text/plain", "throttled");
            server.start();

            final HttpClient httpClient = HttpClient.newHttpClient();
            final HttpRequest request = HttpRequest.newBuilder().uri(URI.create(server.getBaseUrl() + "sites/test/_api/lists")).build();
            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(503, response.statusCode());
            assertEquals("throttled", response.body());
        }
    }
}
