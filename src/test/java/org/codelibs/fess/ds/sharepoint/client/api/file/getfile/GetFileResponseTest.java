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

import java.io.InputStream;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.codelibs.fess.crawler.exception.MaxLengthExceededException;
import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.codelibs.fess.ds.sharepoint.util.SharePointMockServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;

/**
 * Verifies that {@link GetFileResponse#getContentLength()} can be read before
 * {@link GetFileResponse#getFileContent(long)} downloads the body - the low-cost precheck
 * {@code max_content_length} relies on, since {@code GetFiles} carries no size for a listed file -
 * and that {@link GetFileResponse#getFileContent(long)} itself bounds the download by counting
 * bytes, the backstop for a response that never declares (or lies about) its length.
 */
public class GetFileResponseTest extends UnitDsTestCase {

    private static final String FILE_URL = "/sites/test/docs/a.txt";
    private static final String FILE_API = "/sites/test/_api/web/GetFileByServerRelativePath(decodedUrl='" + FILE_URL + "')/$value";

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_getContentLengthReflectsTheDeclaredSizeBeforeDownloading() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(FILE_API, 200, "text/plain", "hello world");
            server.start();

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final GetFile getFile = new GetFile(httpClient, server.getBaseUrl() + "sites/test", null).setServerRelativeUrl(FILE_URL);

                try (GetFileResponse response = getFile.execute()) {
                    assertEquals("the content length must be readable before the body is downloaded", 11L, response.getContentLength());

                    // Reading the content length above must not have consumed the entity: the
                    // actual content must still be downloadable afterward.
                    try (InputStream is = response.getFileContent()) {
                        final String content = new String(is.readAllBytes());
                        assertEquals("the body must still be downloadable after checking its length", "hello world", content);
                    }
                }
            }
        }
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_getFileContentBoundedRejectsABodyOverTheLimitByCountingBytes() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(FILE_API, 200, "text/plain", "hello world");
            server.start();

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final GetFile getFile = new GetFile(httpClient, server.getBaseUrl() + "sites/test", null).setServerRelativeUrl(FILE_URL);

                try (GetFileResponse response = getFile.execute()) {
                    // Called directly, bypassing any header-based precheck a caller might do
                    // first: the bound here comes from counting bytes as they are copied, not
                    // from Content-Length, so it holds even for a response whose declared length
                    // (if any) cannot be trusted - the gzip/chunked case getContentLength()'s own
                    // javadoc describes.
                    assertThrows(MaxLengthExceededException.class, () -> response.getFileContent(5),
                            "a body over the limit must be rejected while it is being copied, not only from its header");
                }
            }
        }
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_getFileContentBoundedAllowsABodyWithinTheLimit() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(FILE_API, 200, "text/plain", "hello world");
            server.start();

            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                final GetFile getFile = new GetFile(httpClient, server.getBaseUrl() + "sites/test", null).setServerRelativeUrl(FILE_URL);

                try (GetFileResponse response = getFile.execute()) {
                    try (InputStream is = response.getFileContent(1000)) {
                        final String content = new String(is.readAllBytes());
                        assertEquals("a body within the limit must still be returned in full", "hello world", content);
                    }
                }
            }
        }
    }
}
