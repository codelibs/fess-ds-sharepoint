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
}
