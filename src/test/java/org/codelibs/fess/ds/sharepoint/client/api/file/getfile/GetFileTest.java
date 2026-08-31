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
package org.codelibs.fess.ds.sharepoint.client.api.file.getfile;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.codelibs.fess.ds.sharepoint.client.backoff.SharePointBackoff;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;
import org.codelibs.fess.ds.sharepoint.util.SharePointMockServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;

public class GetFileTest extends UnitDsTestCase {

    private static final String FILE_URL = "/sites/test/docs/a.txt";

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Test
    public void test_anErrorResponseReportsItsStatusAndBody() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus("/sites/test/_api/web/GetFileByServerRelativePath(decodedUrl='" + FILE_URL + "')/$value", 403,
                    "application/json", "{\"error\":{\"message\":\"Access denied.\"}}");
            server.start();

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final GetFile getFile = new GetFile(httpClient, server.getBaseUrl() + "sites/test", null).setServerRelativeUrl(FILE_URL);

                // The exception describing the failure used to be caught by this method's own
                // generic handler one line later and wrapped in a second one, which left the
                // caller with nothing but "GetFile Request failure.".
                final SharePointClientException e =
                        assertThrows(SharePointClientException.class, getFile::execute, "an error response must fail the request");

                assertTrue("the status code must survive to the caller", e.getMessage().contains("status:403"));
                assertTrue("the server's explanation must survive to the caller", e.getMessage().contains("Access denied."));
            }
        }
    }

    /**
     * GetFile calls {@code client.execute()} directly instead of going through
     * {@code SharePointApi#doJsonRequest}/{@code doXmlRequest}, so a busy-server wait added only
     * to that shared path would never see a file download. Against the unfixed code this test
     * fails because nothing ever reads {@code X-SharePointHealthScore} here at all.
     */
    @Test
    @Timeout(value = 15, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_execute_backsOffWhenTheServerReportsItselfBusy() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            final String path = "/sites/test/_api/web/GetFileByServerRelativePath(decodedUrl='" + FILE_URL + "')/$value";
            server.onPathStatus(path, 200, "text/plain", "file content");
            server.withHeader("X-SharePointHealthScore", "10");
            server.start();

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final List<Long> recordedSleeps = new ArrayList<>();
                final SharePointBackoff backoff = new SharePointBackoff(2000L, 30000L, () -> 0.5d, recordedSleeps::add);
                final GetFile getFile =
                        new GetFile(httpClient, server.getBaseUrl() + "sites/test", null, backoff).setServerRelativeUrl(FILE_URL);

                getFile.execute().close();

                assertEquals("a health score of 10 (attempt 1: 10 - threshold 8 - 1) must back off once", 1, recordedSleeps.size());
                assertEquals("attempt 1 must double the initial delay once", 4000L, recordedSleeps.get(0).longValue());
            }
        }
    }

    /**
     * A health score at or below the busy threshold is normal load, not a signal to slow down.
     */
    @Test
    @Timeout(value = 15, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_execute_doesNotBackOffWhenTheHealthScoreIsAtTheThreshold() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            final String path = "/sites/test/_api/web/GetFileByServerRelativePath(decodedUrl='" + FILE_URL + "')/$value";
            server.onPathStatus(path, 200, "text/plain", "file content");
            server.withHeader("X-SharePointHealthScore", "8");
            server.start();

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final List<Long> recordedSleeps = new ArrayList<>();
                final SharePointBackoff backoff = new SharePointBackoff(2000L, 30000L, () -> 0.5d, recordedSleeps::add);
                final GetFile getFile =
                        new GetFile(httpClient, server.getBaseUrl() + "sites/test", null, backoff).setServerRelativeUrl(FILE_URL);

                getFile.execute().close();

                assertTrue("a health score at the threshold must not trigger a wait", recordedSleeps.isEmpty());
            }
        }
    }

    /**
     * GetFile itself does not back off on a 503 - it has no visibility into whether
     * SharePointCrawler#doCrawl's retry loop will actually retry this target again, and waiting
     * unconditionally would waste a stall on a final, abandoned attempt. Instead, it carries the
     * status code on the exception it throws, so that retry loop can back off itself, exactly as
     * it already does for a 503 from any other API call - see SharePointCrawlerTest for that.
     */
    @Test
    @Timeout(value = 15, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_execute_reportsA503WithItsStatusCodeAndDoesNotBackOffItself() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            final String path = "/sites/test/_api/web/GetFileByServerRelativePath(decodedUrl='" + FILE_URL + "')/$value";
            server.onPathStatus(path, 503, "text/plain", "throttled");
            server.start();

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final List<Long> recordedSleeps = new ArrayList<>();
                final SharePointBackoff backoff = new SharePointBackoff(2000L, 30000L, () -> 0.5d, recordedSleeps::add);
                final GetFile getFile =
                        new GetFile(httpClient, server.getBaseUrl() + "sites/test", null, backoff).setServerRelativeUrl(FILE_URL);

                final SharePointClientException e =
                        assertThrows(SharePointClientException.class, getFile::execute, "a 503 must still fail the request");

                assertEquals("the status code must be preserved on the exception, for the retry loop to key a backoff decision on", 503,
                        e.getStatusCode());
                assertTrue("GetFile itself must not back off - only the retry loop knows whether a retry will follow",
                        recordedSleeps.isEmpty());
            }
        }
    }

    /**
     * Pins the wire form of a file name containing the two characters
     * <a href="https://github.com/codelibs/fess-ds-sharepoint/issues/4">issue #4</a> is about.
     *
     * <p>{@code GetFileByServerRelativeUrl('...')} treats its argument as an <em>encoded</em>
     * URL and decodes it once more itself, so a name holding a literal {@code %} arrives as a
     * broken escape sequence and one holding a {@code #} loses everything after it.
     * {@code GetFileByServerRelativePath(decodedUrl='...')} takes the decoded path instead, so
     * the value only has to survive the one percent-decode every HTTP server does before the
     * OData expression is parsed - which is exactly what {@code encodeRelativeUrl} produces.
     *
     * <p>This asserts the bytes on the wire rather than the endpoint name alone, because the
     * endpoint is only half of the contract: pairing {@code decodedUrl} with a doubly-encoded
     * value would name the right API and still resolve to the wrong file.
     */
    @Test
    @Timeout(value = 15, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_execute_singleEncodesAPercentAndAHashForTheResourcePathEndpoint() throws Exception {
        final String fileUrl = "/sites/test/docs/50% #1.docx";
        final String expectedPath =
                "/sites/test/_api/web/GetFileByServerRelativePath(decodedUrl='/sites/test/docs/50%25%20%231.docx')" + "/$value";
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(expectedPath, 200, "text/plain", "file content");
            server.start();

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                new GetFile(httpClient, server.getBaseUrl() + "sites/test", null).setServerRelativeUrl(fileUrl).execute().close();

                assertEquals(1, server.getRecordedRequests().size());
                assertEquals("a % must reach the server as %25 and a # as %23 - once each, not twice", expectedPath,
                        server.getRecordedRequests().get(0).getPath());
            }
        }
    }

    /** An OAuth double that never makes a real ACS call, so this stays a self-contained unit test. */
    private static OAuth recordingOAuth(final AtomicInteger refreshCount) {
        return new OAuth("id", "secret", "tenant", "realm") {
            @Override
            public void updateAccessToken(final CloseableHttpClient httpClient) {
                refreshCount.incrementAndGet();
            }

            @Override
            public void apply(final HttpRequestBase httpRequest) {
                httpRequest.addHeader("Authorization", "Bearer token-" + refreshCount.get());
            }
        };
    }

    /**
     * GetFile applies the OAuth token itself, bypassing SharePointApi#doJsonRequest, so it needs
     * its own refresh-and-retry - a fix added only to the shared path would never see a file
     * download at all.
     */
    @Test
    @Timeout(value = 15, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_execute_refreshesTheTokenAndRetriesOnceOn401() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            final String path = "/sites/test/_api/web/GetFileByServerRelativePath(decodedUrl='" + FILE_URL + "')/$value";
            server.onPathOnce(path, 401, "application/json", "{}");
            server.onPathStatus(path, 200, "text/plain", "file content");
            server.start();

            final AtomicInteger refreshCount = new AtomicInteger();
            final OAuth oAuth = recordingOAuth(refreshCount);
            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final GetFile getFile = new GetFile(httpClient, server.getBaseUrl() + "sites/test", oAuth).setServerRelativeUrl(FILE_URL);

                getFile.execute().close();

                assertEquals("a 401 must refresh the token exactly once", 1, refreshCount.get());
                assertEquals("a 401 must be retried exactly once", 2, server.getRecordedRequests().size());
                assertEquals("the first attempt must carry the original token", "Bearer token-0",
                        server.getRecordedRequests().get(0).getHeader("Authorization"));
                assertEquals("the retry must carry the refreshed token, not the stale one", "Bearer token-1",
                        server.getRecordedRequests().get(1).getHeader("Authorization"));
            }
        }
    }
}
