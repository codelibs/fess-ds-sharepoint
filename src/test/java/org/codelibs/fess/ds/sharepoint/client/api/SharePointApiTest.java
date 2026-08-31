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
package org.codelibs.fess.ds.sharepoint.client.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.TestInfo;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.codelibs.fess.ds.sharepoint.client.backoff.SharePointBackoff;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointResponseFormatException;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointServerException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;
import org.codelibs.fess.ds.sharepoint.util.SharePointMockServer;
import org.codelibs.fess.util.ComponentUtil;
import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;

public class SharePointApiTest extends UnitDsTestCase {
    @Override
    protected String prepareConfigFile() {
        return "test_app.xml";
    }

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Override
    public void setUp(TestInfo testInfo) throws Exception {
        super.setUp(testInfo);
    }

    @Override
    public void tearDown(TestInfo testInfo) throws Exception {
        ComponentUtil.setFessConfig(null);
        super.tearDown(testInfo);
    }

    @Test
    public void test_encodeRelativeUrl() throws Exception {
        SharePointApi<SharePointApiResponse> sharePointApi = new SharePointApi<SharePointApiResponse>(null, null, null) {
            @Override
            public SharePointApiResponse execute() {
                return null;
            }
        };

        assertNull(sharePointApi.encodeRelativeUrl(null));
        assertEquals("", sharePointApi.encodeRelativeUrl(""));
        assertEquals("%20", sharePointApi.encodeRelativeUrl(" "));
        assertEquals("abc123%21.txt", sharePointApi.encodeRelativeUrl("abc123!.txt"));
        assertEquals("a%20b%20c/a%20b%20c", sharePointApi.encodeRelativeUrl("a b c/a b c"));
        assertEquals("%E3%83%86%E3%82%B9%E3%83%88.txt", sharePointApi.encodeRelativeUrl("テスト.txt"));
        assertEquals("%E3%83%86%E3%82%B9%E3%83%88//%E3%83%86%E3%82%B9%E3%83%88", sharePointApi.encodeRelativeUrl("テスト//テスト"));
        assertEquals("///%E3%83%86%E3%82%B9%E3%83%88.txt///", sharePointApi.encodeRelativeUrl("///テスト.txt///"));
        assertEquals("/%E3%83%86%E3%82%B9%E3%83%88/%E3%83%86%E3%82%B9%E3%83%88.txt", sharePointApi.encodeRelativeUrl("/テスト/テスト.txt"));
        assertEquals("/%E3%83%86%E3%82%B9%E3%83%88/%E3%83%86%E3%82%B9%E3%83%88%E3%81%A6%E3%81%99%E3%81%A8.txt",
                sharePointApi.encodeRelativeUrl("/テスト/テストてすと.txt"));
    }

    @Test
    public void test_encodeRelativeUrl_withSpecialCharacters() throws Exception {
        SharePointApi<SharePointApiResponse> sharePointApi = new SharePointApi<SharePointApiResponse>(null, null, null) {
            @Override
            public SharePointApiResponse execute() {
                return null;
            }
        };

        // Test special characters
        assertEquals("file%23name.txt", sharePointApi.encodeRelativeUrl("file#name.txt"));
        assertEquals("file%26name.txt", sharePointApi.encodeRelativeUrl("file&name.txt"));
        assertEquals("file%3Dname.txt", sharePointApi.encodeRelativeUrl("file=name.txt"));
        assertEquals("file%2Bname.txt", sharePointApi.encodeRelativeUrl("file+name.txt"));
        // The other half of what issue #4 reports, and the one this method must not encode twice:
        // every caller places the result in a decodedUrl='...' literal, which the server reads as
        // the already-decoded path, so a single %25 is what makes a literal % arrive as itself.
        assertEquals("a literal percent must be encoded exactly once", "file%25name.txt", sharePointApi.encodeRelativeUrl("file%name.txt"));
        assertEquals("a value that already looks encoded is still just a name", "file%2520name.txt",
                sharePointApi.encodeRelativeUrl("file%20name.txt"));
    }

    @Test
    public void test_encodeRelativeUrl_withMultiplePaths() throws Exception {
        SharePointApi<SharePointApiResponse> sharePointApi = new SharePointApi<SharePointApiResponse>(null, null, null) {
            @Override
            public SharePointApiResponse execute() {
                return null;
            }
        };

        // Test multiple path segments
        assertEquals("folder1/folder2/file.txt", sharePointApi.encodeRelativeUrl("folder1/folder2/file.txt"));
        assertEquals("folder%201/folder%202/file%20name.txt", sharePointApi.encodeRelativeUrl("folder 1/folder 2/file name.txt"));
    }

    @Test
    public void test_encodeRelativeUrl_doublesApostrophesForTheODataLiteral() {
        final SharePointApi<SharePointApiResponse> sharePointApi = new SharePointApi<>(null, null, null) {
            @Override
            public SharePointApiResponse execute() {
                return null;
            }
        };
        // The value is interpolated into an OData string literal, so a single apostrophe closes
        // the literal early and the server answers 400. It has to be doubled before it is
        // percent-encoded, which puts %27%27 on the wire.
        assertEquals("an apostrophe must be doubled and then encoded", "O%27%27Brien.docx",
                sharePointApi.encodeRelativeUrl("O'Brien.docx"));
        assertEquals("apostrophes must be doubled in every path segment", "/sites/O%27%27Brien/Docs/it%27%27s.txt",
                sharePointApi.encodeRelativeUrl("/sites/O'Brien/Docs/it's.txt"));
        assertEquals("a value with no apostrophe must be unchanged", "plain.txt", sharePointApi.encodeRelativeUrl("plain.txt"));
    }

    @Test
    public void test_requireGuidLiteral_acceptsBackwardCompatibleGuidForms() {
        // requireGuidLiteral either throws or returns its argument by identity, so there is
        // nothing meaningful to compare the return value against - the only thing each of these
        // forms can actually demonstrate is that it does not throw. They have to keep being
        // accepted, not just the plain lowercase dashed form used elsewhere in this file's
        // tests, or validating the value at all would be a backward-compatibility break.
        assertDoesNotThrow(() -> SharePointApi.requireGuidLiteral("a1234567-89ab-cdef-0123-456789abcdef", "listId"),
                "a plain lowercase GUID must be accepted");
        assertDoesNotThrow(() -> SharePointApi.requireGuidLiteral("A1234567-89AB-CDEF-0123-456789ABCDEF", "listId"),
                "uppercase hex digits must be accepted");
        assertDoesNotThrow(() -> SharePointApi.requireGuidLiteral("{a1234567-89ab-cdef-0123-456789abcdef}", "listId"),
                "a GUID wrapped in plain braces must be accepted");
        // The form you get by copying a classic SharePoint "List=" query-string value verbatim.
        // It previously round-tripped through the server's own percent-decode and plausibly
        // worked, so validation must not start rejecting it.
        assertDoesNotThrow(() -> SharePointApi.requireGuidLiteral("%7Ba1234567-89ab-cdef-0123-456789abcdef%7D", "listId"),
                "percent-encoded braces (%7B/%7D) must be accepted");
        assertDoesNotThrow(() -> SharePointApi.requireGuidLiteral("%7ba1234567-89ab-cdef-0123-456789abcdef%7d", "listId"),
                "lowercase percent-encoded braces (%7b/%7d) must be accepted");
    }

    @Test
    public void test_XmlResponse_parseXml_static() throws Exception {
        final String xml = "<root><item>value1</item><item>value2</item></root>";
        final java.util.List<String> items = new java.util.ArrayList<>();

        final org.xml.sax.helpers.DefaultHandler handler = new org.xml.sax.helpers.DefaultHandler() {
            private boolean inItem = false;

            @Override
            public void startElement(String uri, String localName, String qName, org.xml.sax.Attributes attributes) {
                if ("item".equals(qName)) {
                    inItem = true;
                }
            }

            @Override
            public void characters(char[] ch, int start, int length) {
                if (inItem) {
                    items.add(new String(ch, start, length));
                }
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                if ("item".equals(qName)) {
                    inItem = false;
                }
            }
        };

        SharePointApi.XmlResponse.parseXml(xml, handler);
        assertEquals(2, items.size());
        assertEquals("value1", items.get(0));
        assertEquals("value2", items.get(1));
    }

    /**
     * doJsonRequest is the shared path nearly every API (other than GetFile/GetFile2013, which
     * bypass it) goes through, so this is the one call site that has to see a busy-server wait for
     * all of them at once.
     */
    @Test
    @Timeout(value = 15, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_doJsonRequest_backsOffWhenTheServerReportsItselfBusy() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus("/probe", 200, "application/json", "{}");
            server.withHeader("X-SharePointHealthScore", "9");
            server.start();

            final List<Long> recordedSleeps = new ArrayList<>();
            final SharePointBackoff backoff = new SharePointBackoff(2000L, 30000L, () -> 0.5d, recordedSleeps::add);
            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final SharePointApi<SharePointApiResponse> sharePointApi =
                        new SharePointApi<SharePointApiResponse>(httpClient, server.getBaseUrl(), null, backoff) {
                            @Override
                            public SharePointApiResponse execute() {
                                doJsonRequest(new HttpGet(server.getBaseUrl() + "probe"));
                                return null;
                            }
                        };

                sharePointApi.execute();

                assertEquals("a health score of 9 (attempt 0: 9 - threshold 8 - 1) must back off once", 1, recordedSleeps.size());
                assertEquals("attempt 0 must be the unjittered-at-the-midpoint initial delay, not already doubled", 2000L,
                        recordedSleeps.get(0).longValue());
            }
        }
    }

    /**
     * A JSON-Light-disabled on-premises server answers a JSON request with Atom XML instead, 200
     * and all. Against the unfixed code this response reaches objectMapper.readValue, which throws
     * JsonParseException on the leading '&lt;', wrapped into a SharePointClientException with a
     * message that says nothing about the actual cause.
     */
    @Test
    @Timeout(value = 15, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_doJsonRequest_rejectsAnAtomXmlResponseWithAClearMessage() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus("/probe", 200, "application/atom+xml", "<feed><entry>not json</entry></feed>");
            server.start();

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final SharePointApi<SharePointApiResponse> sharePointApi =
                        new SharePointApi<SharePointApiResponse>(httpClient, server.getBaseUrl(), null) {
                            @Override
                            public SharePointApiResponse execute() {
                                doJsonRequest(new HttpGet(server.getBaseUrl() + "probe"));
                                return null;
                            }
                        };

                final SharePointResponseFormatException e = assertThrows(SharePointResponseFormatException.class, sharePointApi::execute,
                        "an Atom XML response to a JSON request must be rejected, not parsed as JSON");

                assertTrue("the message must name the actual Content-Type", e.getMessage().contains("application/atom+xml"));
                assertTrue("the message must explain the likely cause", e.getMessage().contains("JSON Light"));
            }
        }
    }

    /**
     * Pins the outgoing Accept header to the bare media type. Every JSON parser in this plugin
     * (GetLists, GetFoldersResponse, GetFilesResponse, GetFormsResponse,
     * GetListItemAttachmentsResponse, GetListItemRole, GetListItems, GetDoclibListItem, ...) reads
     * the JSON Light shape ("value", "odata.nextLink", "odata.editLink"), which is what a bare
     * "application/json" already yields on the SharePoint versions this plugin targets. An
     * explicit "odata=verbose" would switch every one of those responses to the pre-JSON-Light
     * {"d":{"results":[...]}} shape instead and break them all - a regression the mock server
     * cannot catch on its own, since it ignores Accept and always serves JSON-Light-shaped
     * fixtures regardless of what was requested. This test exists to catch that class of
     * regression by pinning the header the server actually receives.
     */
    @Test
    @Timeout(value = 15, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_doJsonRequest_sendsTheBareJsonAcceptHeader() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus("/probe", 200, "application/json", "{}");
            server.start();

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final SharePointApi<SharePointApiResponse> sharePointApi =
                        new SharePointApi<SharePointApiResponse>(httpClient, server.getBaseUrl(), null) {
                            @Override
                            public SharePointApiResponse execute() {
                                doJsonRequest(new HttpGet(server.getBaseUrl() + "probe"));
                                return null;
                            }
                        };

                sharePointApi.execute();

                assertEquals(1, server.getRecordedRequests().size());
                assertEquals(
                        "the Accept header must stay the bare media type - an explicit metadata level such as"
                                + " odata=verbose would change the response shape every JSON parser in this plugin expects",
                        "application/json", server.getRecordedRequests().get(0).getHeader("Accept"));
            }
        }
    }

    /**
     * SharePointResponseFormatException must not be mistaken for one of the two types
     * SharePointCrawler#doCrawl's retry loop retries. instanceof cannot even be written between it
     * and either sibling - they are unrelated concrete classes - which is itself evidence neither
     * relationship exists; this instead confirms the actual superclass and non-assignability
     * directly.
     */
    @Test
    public void test_SharePointResponseFormatException_isNeitherServerNorClientException() {
        assertEquals("must extend RuntimeException directly, not SharePointServerException or SharePointClientException",
                RuntimeException.class, SharePointResponseFormatException.class.getSuperclass());
        assertFalse("SharePointServerException must not be assignable from it",
                SharePointServerException.class.isAssignableFrom(SharePointResponseFormatException.class));
        assertFalse("SharePointClientException must not be assignable from it",
                SharePointClientException.class.isAssignableFrom(SharePointResponseFormatException.class));
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
     * Against the unfixed code, doJsonRequest applies the token once, up front, and never checks
     * the response status for 401 at all - the request is answered 401 and reported as a
     * SharePointServerException, with no refresh and no retry.
     */
    @Test
    @Timeout(value = 15, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_doJsonRequest_refreshesTheTokenAndRetriesOnceOn401() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathOnce("/probe", 401, "application/json", "{}");
            server.onPathStatus("/probe", 200, "application/json", "{}");
            server.start();

            final AtomicInteger refreshCount = new AtomicInteger();
            final OAuth oAuth = recordingOAuth(refreshCount);
            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final SharePointApi<SharePointApiResponse> sharePointApi =
                        new SharePointApi<SharePointApiResponse>(httpClient, server.getBaseUrl(), oAuth) {
                            @Override
                            public SharePointApiResponse execute() {
                                doJsonRequest(new HttpGet(server.getBaseUrl() + "probe"));
                                return null;
                            }
                        };

                sharePointApi.execute();

                assertEquals("a 401 must refresh the token exactly once", 1, refreshCount.get());
                assertEquals("a 401 must be retried exactly once", 2, server.getRecordedRequests().size());
                assertEquals("the first attempt must carry the original token", "Bearer token-0",
                        server.getRecordedRequests().get(0).getHeader("Authorization"));
                assertEquals("the retry must carry the refreshed token, not the stale one", "Bearer token-1",
                        server.getRecordedRequests().get(1).getHeader("Authorization"));
            }
        }
    }

    /**
     * A second 401 - the refreshed token is also rejected - must not loop forever; it must be
     * reported like any other 401.
     */
    @Test
    @Timeout(value = 15, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_doJsonRequest_doesNotRetryASecond401() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus("/probe", 401, "application/json", "{}");
            server.start();

            final AtomicInteger refreshCount = new AtomicInteger();
            final OAuth oAuth = recordingOAuth(refreshCount);
            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final SharePointApi<SharePointApiResponse> sharePointApi =
                        new SharePointApi<SharePointApiResponse>(httpClient, server.getBaseUrl(), oAuth) {
                            @Override
                            public SharePointApiResponse execute() {
                                doJsonRequest(new HttpGet(server.getBaseUrl() + "probe"));
                                return null;
                            }
                        };

                assertThrows(SharePointServerException.class, sharePointApi::execute,
                        "a 401 that survives the one retry must still be reported as an error");

                assertEquals("a persistent 401 must refresh the token exactly once, not loop", 1, refreshCount.get());
                assertEquals("a persistent 401 must be requested exactly twice: the original attempt plus one retry", 2,
                        server.getRecordedRequests().size());
            }
        }
    }
}
