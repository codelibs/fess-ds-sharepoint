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

import org.codelibs.fess.util.ComponentUtil;
import org.dbflute.utflute.lastaflute.LastaFluteTestCase;
import org.junit.Test;

public class SharePointApiTest extends LastaFluteTestCase {
    @Override
    protected String prepareConfigFile() {
        return "test_app.xml";
    }

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    public void tearDown() throws Exception {
        ComponentUtil.setFessConfig(null);
        super.tearDown();
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
