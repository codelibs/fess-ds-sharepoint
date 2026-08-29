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

import org.junit.jupiter.api.TestInfo;

import org.codelibs.fess.util.ComponentUtil;
import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.junit.jupiter.api.Test;

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
}
