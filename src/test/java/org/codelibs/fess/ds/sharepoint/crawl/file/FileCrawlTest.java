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
package org.codelibs.fess.ds.sharepoint.crawl.file;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.codelibs.fess.crawler.container.CrawlerContainer;
import org.codelibs.fess.crawler.entity.ExtractData;
import org.codelibs.fess.crawler.exception.MaxLengthExceededException;
import org.codelibs.fess.crawler.extractor.Extractor;
import org.codelibs.fess.crawler.extractor.ExtractorFactory;
import org.codelibs.fess.crawler.helper.MimeTypeHelper;
import org.codelibs.fess.crawler.helper.impl.MimeTypeHelperImpl;
import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.crawl.SharePointCrawl;
import org.codelibs.fess.ds.sharepoint.util.SharePointMockServer;
import org.codelibs.fess.exception.DataStoreCrawlingException;
import org.codelibs.fess.helper.FileTypeHelper;
import org.codelibs.fess.mylasta.direction.FessConfig;
import org.codelibs.fess.util.ComponentUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises the file-level operability parameters: {@code ignore_error}, {@code extractor_name},
 * {@code supported_mimetypes} and {@code max_content_length}.
 *
 * <p>Most tests below rely on a plain, disconnected {@link ExtractorFactory} - one built with
 * {@code new ExtractorFactory()} rather than DI-injected, so its {@code crawlerContainer} field is
 * {@code null} - that {@link #setUp} registers fresh under {@code "extractorFactory"} before every
 * test. {@code FileCrawl#getContent}'s call to {@code ComponentUtil.getExtractorFactory().builder(
 * ...).extract()} then reaches {@code ExtractorBuilder#extract}'s first line,
 * {@code crawlerContainer.getComponent("extractorFactory")}, and throws a
 * {@code NullPointerException} - a real failure this code already has to handle, reached without
 * standing up Tika. Registering it fresh in {@code setUp} (the same shape as {@code mimeTypeHelper}
 * and {@code fileTypeHelper} just below it) matters because {@code ComponentUtil}'s component
 * override map has no per-key removal and is one JVM-wide map shared by every test class in a run:
 * without this, a test method left registering a working stub here (see the {@code extractor_name}
 * tests below) would otherwise still be in effect when a later test expecting extraction to fail
 * starts. The {@code extractor_name} tests are the exception: they register a minimal
 * {@code ExtractorFactory} of their own (see {@link #registerCapturingExtractorFactory(List)}),
 * after {@link #setUp} has already run, to observe which component name actually reaches the
 * extractor builder's crawler-container lookup.
 */
public class FileCrawlTest extends UnitDsTestCase {

    private static final String FILE_URL = "/sites/test/docs/a.txt";
    private static final String FILE_API = "/sites/test/_api/web/GetFileByServerRelativePath(decodedUrl='" + FILE_URL + "')/$value";

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Override
    public void setUp(final TestInfo testInfo) throws Exception {
        super.setUp(testInfo);
        ComponentUtil.register(new MimeTypeHelperImpl(), MimeTypeHelper.class.getCanonicalName());
        final FileTypeHelper fileTypeHelper = new FileTypeHelper();
        fileTypeHelper.init();
        ComponentUtil.register(fileTypeHelper, "fileTypeHelper");
        // See the class javadoc: a fresh, disconnected ExtractorFactory every test, so extraction
        // deterministically fails with a NullPointerException regardless of what a previous test
        // method (in this class or an earlier one in the same JVM) left registered under this name.
        ComponentUtil.register(new ExtractorFactory(), "extractorFactory");
    }

    private static FileCrawl fileCrawl(final SharePointClient client, final boolean ignoreError) {
        return fileCrawl(client, ignoreError, FileCrawl.DEFAULT_EXTRACTOR_NAME);
    }

    private static FileCrawl fileCrawl(final SharePointClient client, final boolean ignoreError, final String extractorName) {
        return fileCrawl(client, ignoreError, extractorName, FileCrawl.DEFAULT_SUPPORTED_MIMETYPES);
    }

    private static FileCrawl fileCrawl(final SharePointClient client, final boolean ignoreError, final String extractorName,
            final String[] supportedMimeTypes) {
        return fileCrawl(client, ignoreError, extractorName, supportedMimeTypes, FileCrawl.DEFAULT_MAX_CONTENT_LENGTH);
    }

    private static FileCrawl fileCrawl(final SharePointClient client, final boolean ignoreError, final String extractorName,
            final String[] supportedMimeTypes, final long maxContentLength) {
        return new FileCrawl(client, "a.txt", "http://example.com/a.txt", FILE_URL, new Date(), new Date(), List.of(), Map.of(), null,
                ignoreError, extractorName, supportedMimeTypes, maxContentLength);
    }

    /**
     * Registers an {@link ExtractorFactory} whose {@code crawlerContainer} records every component
     * name it is asked for, so a test can prove {@code extractor_name} reaches
     * {@code ExtractorBuilder}'s fallback lookup rather than only round-tripping through a getter.
     *
     * <p>{@code ExtractorFactory.crawlerContainer} is {@code protected}, so an anonymous subclass -
     * in the same instance initializer that constructs it - can set it directly; nothing in this
     * plugin otherwise builds a {@link CrawlerContainer}.
     *
     * @param requestedComponentNames every name passed to {@code crawlerContainer.getComponent} is
     *            appended here
     */
    private static void registerCapturingExtractorFactory(final List<String> requestedComponentNames) {
        final ExtractorFactory extractorFactory = new ExtractorFactory() {
            {
                this.crawlerContainer = new CrawlerContainer() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public <T> T getComponent(final String name) {
                        requestedComponentNames.add(name);
                        if ("mimeTypeHelper".equals(name)) {
                            return (T) new MimeTypeHelperImpl();
                        }
                        if ("extractorFactory".equals(name)) {
                            // ExtractorBuilder#extract looks its own ExtractorFactory reference up
                            // through the crawler container rather than reusing the one that built
                            // it - a fresh, empty-mapped instance is enough to make it fall through
                            // to the extractorName lookup below, which is the call this test cares
                            // about.
                            return (T) new ExtractorFactory();
                        }
                        return (T) (Extractor) (in, params) -> new ExtractData("stub content");
                    }

                    @Override
                    public boolean available() {
                        return true;
                    }

                    @Override
                    public void destroy() {
                        // nothing to release
                    }
                };
            }
        };
        ComponentUtil.register(extractorFactory, "extractorFactory");
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_extractorNameDefaultsToTikaExtractor() throws Exception {
        final List<String> requestedComponentNames = new ArrayList<>();
        registerCapturingExtractorFactory(requestedComponentNames);

        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(FILE_API, 200, "text/plain", "hello world");
            server.start();

            try (SharePointClient client = SharePointClient.builder().setUrl(server.getBaseUrl()).setSite("test").build()) {
                final FileCrawl fileCrawl = fileCrawl(client, true);
                fileCrawl.doCrawl(null, new ConcurrentLinkedQueue<>());

                assertTrue("with extractor_name unset, the default tikaExtractor must be requested from the crawler container",
                        requestedComponentNames.contains("tikaExtractor"));
            }
        }
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_extractorNameReachesTheExtractorBuilder() throws Exception {
        final List<String> requestedComponentNames = new ArrayList<>();
        registerCapturingExtractorFactory(requestedComponentNames);

        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(FILE_API, 200, "text/plain", "hello world");
            server.start();

            try (SharePointClient client = SharePointClient.builder().setUrl(server.getBaseUrl()).setSite("test").build()) {
                final FileCrawl fileCrawl = fileCrawl(client, true, "myCustomExtractor");
                fileCrawl.doCrawl(null, new ConcurrentLinkedQueue<>());

                assertTrue("extractor_name must reach the extractor builder's crawler-container lookup, not stop at a constructor field",
                        requestedComponentNames.contains("myCustomExtractor"));
            }
        }
    }

    /**
     * Returns a {@link FessConfig} that delegates every method to the real one already registered
     * by {@link UnitDsTestCase}, except {@code isCrawlerIgnoreContentException}, which it forces
     * to {@code false}.
     *
     * <p>{@code FessConfig.SimpleImpl} cannot be built standalone in a test - every getter,
     * including the ones {@code FileCrawl#buildDataMap} needs for field names, reads through an
     * {@code ObjectiveConfig} that is only populated by the real property-loading machinery
     * {@link UnitDsTestCase} already went through. A delegating proxy keeps that machinery intact
     * and overrides only the one method this test cares about.
     */
    private static FessConfig configForcingHardFailure() {
        final FessConfig realConfig = ComponentUtil.getFessConfig();
        return (FessConfig) Proxy.newProxyInstance(FessConfig.class.getClassLoader(), new Class<?>[] { FessConfig.class }, (proxy, method,
                args) -> "isCrawlerIgnoreContentException".equals(method.getName()) ? Boolean.FALSE : method.invoke(realConfig, args));
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_extractionFailureIsSuppressedWhenIgnoreErrorIsTrue() throws Exception {
        // The global core setting is forced to false, so only this plugin's own ignore_error=true
        // can be the reason the failure below is suppressed rather than thrown.
        ComponentUtil.setFessConfig(configForcingHardFailure());
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(FILE_API, 200, "text/plain", "hello world");
            server.start();

            try (SharePointClient client = SharePointClient.builder().setUrl(server.getBaseUrl()).setSite("test").build()) {
                final FileCrawl fileCrawl = fileCrawl(client, true);
                final Queue<SharePointCrawl> crawlingQueue = new ConcurrentLinkedQueue<>();

                final Map<String, Object> dataMap = fileCrawl.doCrawl(null, crawlingQueue);

                assertNotNull("ignore_error=true must still produce a document for the file", dataMap);
            }
        } finally {
            ComponentUtil.setFessConfig(null);
        }
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_extractionFailurePropagatesWhenIgnoreErrorIsFalse() throws Exception {
        ComponentUtil.setFessConfig(configForcingHardFailure());
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(FILE_API, 200, "text/plain", "hello world");
            server.start();

            try (SharePointClient client = SharePointClient.builder().setUrl(server.getBaseUrl()).setSite("test").build()) {
                final FileCrawl fileCrawl = fileCrawl(client, false);
                final Queue<SharePointCrawl> crawlingQueue = new ConcurrentLinkedQueue<>();

                assertThrows(DataStoreCrawlingException.class, () -> fileCrawl.doCrawl(null, crawlingQueue),
                        "ignore_error=false must let the extraction failure fail the crawl target");
            }
        } finally {
            ComponentUtil.setFessConfig(null);
        }
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_supportedMimeTypesSkipsAnUnmatchedFileWithoutDownloadingIt() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(FILE_API, 200, "text/plain", "hello world");
            server.start();

            try (SharePointClient client = SharePointClient.builder().setUrl(server.getBaseUrl()).setSite("test").build()) {
                // a.txt resolves to text/plain by name alone; restricting to PDF must exclude it.
                final FileCrawl fileCrawl = fileCrawl(client, true, FileCrawl.DEFAULT_EXTRACTOR_NAME, new String[] { "application/pdf" });
                final Queue<SharePointCrawl> crawlingQueue = new ConcurrentLinkedQueue<>();

                final Map<String, Object> dataMap = fileCrawl.doCrawl(null, crawlingQueue);

                assertNull("a file whose MIME type matches no supported_mimetypes pattern must be skipped", dataMap);
                assertTrue("the file content must never have been requested", server.getRecordedRequests().isEmpty());
            }
        }
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_supportedMimeTypesCrawlsAMatchedFile() throws Exception {
        ComponentUtil.setFessConfig(configForcingHardFailure());
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(FILE_API, 200, "text/plain", "hello world");
            server.start();

            try (SharePointClient client = SharePointClient.builder().setUrl(server.getBaseUrl()).setSite("test").build()) {
                final FileCrawl fileCrawl = fileCrawl(client, true, FileCrawl.DEFAULT_EXTRACTOR_NAME, new String[] { "text/.*" });
                final Queue<SharePointCrawl> crawlingQueue = new ConcurrentLinkedQueue<>();

                final Map<String, Object> dataMap = fileCrawl.doCrawl(null, crawlingQueue);

                assertNotNull("a file matching a supported_mimetypes pattern must still be crawled", dataMap);
                assertFalse("a matched file must have its content requested", server.getRecordedRequests().isEmpty());
            }
        } finally {
            ComponentUtil.setFessConfig(null);
        }
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_maxContentLengthSkipsAFileOverTheLimitFromItsContentLengthHeader() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            // "hello world" is 11 bytes; the mock server's Content-Length header reflects that
            // exactly, since the whole body is written in one call with its length known upfront.
            server.onPathStatus(FILE_API, 200, "text/plain", "hello world");
            server.start();

            try (SharePointClient client = SharePointClient.builder().setUrl(server.getBaseUrl()).setSite("test").build()) {
                final FileCrawl fileCrawl =
                        fileCrawl(client, true, FileCrawl.DEFAULT_EXTRACTOR_NAME, FileCrawl.DEFAULT_SUPPORTED_MIMETYPES, 5L);
                final Queue<SharePointCrawl> crawlingQueue = new ConcurrentLinkedQueue<>();

                // A deliberate policy exclusion, not a crawl failure: doCrawl catches
                // MaxLengthExceededException and returns null instead of letting it escape, so
                // this does not count as a lost target and does not suppress delete_old_docs for
                // the run. Not gated by ignore_error=true either: the precheck is a hard stop,
                // independent of the content-extraction guard, matching fess-ds-microsoft365's
                // OneDriveDataStore.
                final Map<String, Object> dataMap = fileCrawl.doCrawl(null, crawlingQueue);

                assertNull("a file over max_content_length must be skipped, not indexed", dataMap);
            }
        }
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_maxContentLengthAllowsAFileWithinTheLimit() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(FILE_API, 200, "text/plain", "hello world");
            server.start();

            try (SharePointClient client = SharePointClient.builder().setUrl(server.getBaseUrl()).setSite("test").build()) {
                final FileCrawl fileCrawl =
                        fileCrawl(client, true, FileCrawl.DEFAULT_EXTRACTOR_NAME, FileCrawl.DEFAULT_SUPPORTED_MIMETYPES, 1000L);
                final Queue<SharePointCrawl> crawlingQueue = new ConcurrentLinkedQueue<>();

                final Map<String, Object> dataMap = fileCrawl.doCrawl(null, crawlingQueue);

                assertNotNull("a file within max_content_length must still be crawled", dataMap);
            }
        }
    }
}
