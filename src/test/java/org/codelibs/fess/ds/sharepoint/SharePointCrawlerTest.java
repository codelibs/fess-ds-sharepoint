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
package org.codelibs.fess.ds.sharepoint;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.codelibs.fess.ds.sharepoint.SharePointCrawler.CrawlerConfig;
import org.codelibs.fess.ds.sharepoint.crawl.file.FileCrawl;
import org.junit.jupiter.api.Test;

import jakarta.validation.ValidationException;

public class SharePointCrawlerTest extends UnitDsTestCase {

    @Override
    protected String prepareConfigFile() {
        return "test_app.xml";
    }

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    private static CrawlerConfig baseConfig() {
        final CrawlerConfig config = new CrawlerConfig();
        config.setUrl("http://localhost/");
        config.setSiteName("test");
        return config;
    }

    @Test
    public void test_construction_rejectsAMalformedSiteListId() {
        final CrawlerConfig config = baseConfig();
        config.setInitialListId("11111111-1111-1111-1111-111111111111' or 1=1--");

        // This has to throw from the constructor itself - before any crawl target exists and
        // before SharePointCrawler#doCrawl's generic exception handling is even reachable - so
        // the failure escapes uncaught to SharePointDataStore#createCrawler's caller instead of
        // being caught once there, downgraded to a warning, and reported as a zero-document
        // "success". See SharePointDataStoreTest for the same guarantee verified through the
        // full storeData path.
        assertThrows(ValidationException.class, () -> new SharePointCrawler(config),
                "a malformed site.list_id must fail crawler construction, not just a later request");
    }

    @Test
    public void test_construction_acceptsAWellFormedSiteListId() {
        final CrawlerConfig config = baseConfig();
        config.setInitialListId("11111111-1111-1111-1111-111111111111");

        assertDoesNotThrow(() -> new SharePointCrawler(config), "a well-formed GUID list id must not be rejected");
    }

    @Test
    public void test_setSupportedMimeTypes_blankValueFallsBackToTheDefault() {
        final CrawlerConfig config = new CrawlerConfig();

        config.setSupportedMimeTypes("");

        // A blank value split on "," used to become {""}, a pattern that matches no MIME type at
        // all - supported_mimetypes="" silently excluded every file rather than leaving the
        // parameter unset.
        assertEquals("a blank supported_mimetypes must fall back to the default pattern, not {\"\"}",
                Arrays.toString(FileCrawl.DEFAULT_SUPPORTED_MIMETYPES), Arrays.toString(config.getSupportedMimeTypes()));
    }

    @Test
    public void test_setSupportedMimeTypes_blankAfterTrimFallsBackToTheDefault() {
        final CrawlerConfig config = new CrawlerConfig();

        config.setSupportedMimeTypes("   ");

        assertEquals("whitespace-only supported_mimetypes must also fall back to the default",
                Arrays.toString(FileCrawl.DEFAULT_SUPPORTED_MIMETYPES), Arrays.toString(config.getSupportedMimeTypes()));
    }
}
