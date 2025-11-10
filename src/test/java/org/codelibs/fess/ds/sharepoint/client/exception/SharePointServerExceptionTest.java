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
package org.codelibs.fess.ds.sharepoint.client.exception;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.codelibs.fess.util.ComponentUtil;
import org.dbflute.utflute.lastaflute.LastaFluteTestCase;
import org.junit.Test;

public class SharePointServerExceptionTest extends LastaFluteTestCase {

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
    public void test_constructor_withMessageAndStatusCode() {
        final String message = "Server error occurred";
        final int statusCode = 500;
        final SharePointServerException exception = new SharePointServerException(message, statusCode);

        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
        assertEquals(statusCode, exception.getStatusCode());
    }

    @Test
    public void test_getStatusCode_400() {
        final SharePointServerException exception = new SharePointServerException("Bad Request", 400);
        assertEquals(400, exception.getStatusCode());
    }

    @Test
    public void test_getStatusCode_401() {
        final SharePointServerException exception = new SharePointServerException("Unauthorized", 401);
        assertEquals(401, exception.getStatusCode());
    }

    @Test
    public void test_getStatusCode_403() {
        final SharePointServerException exception = new SharePointServerException("Forbidden", 403);
        assertEquals(403, exception.getStatusCode());
    }

    @Test
    public void test_getStatusCode_404() {
        final SharePointServerException exception = new SharePointServerException("Not Found", 404);
        assertEquals(404, exception.getStatusCode());
    }

    @Test
    public void test_getStatusCode_500() {
        final SharePointServerException exception = new SharePointServerException("Internal Server Error", 500);
        assertEquals(500, exception.getStatusCode());
    }

    @Test
    public void test_getStatusCode_503() {
        final SharePointServerException exception = new SharePointServerException("Service Unavailable", 503);
        assertEquals(503, exception.getStatusCode());
    }

    @Test
    public void test_isRuntimeException() {
        final SharePointServerException exception = new SharePointServerException("Test", 500);
        assertTrue(exception instanceof RuntimeException);
    }

    @Test
    public void test_throwAndCatch() {
        try {
            throw new SharePointServerException("Server error", 500);
        } catch (final SharePointServerException e) {
            assertEquals("Server error", e.getMessage());
            assertEquals(500, e.getStatusCode());
        }
    }

    @Test
    public void test_multipleInstances() {
        final SharePointServerException exception1 = new SharePointServerException("Error 1", 400);
        final SharePointServerException exception2 = new SharePointServerException("Error 2", 500);

        assertEquals(400, exception1.getStatusCode());
        assertEquals(500, exception2.getStatusCode());
        assertEquals("Error 1", exception1.getMessage());
        assertEquals("Error 2", exception2.getMessage());
    }

    @Test
    public void test_statusCodePreservedAcrossThrow() {
        int caughtStatusCode = 0;
        try {
            throw new SharePointServerException("Test error", 404);
        } catch (final SharePointServerException e) {
            caughtStatusCode = e.getStatusCode();
        }
        assertEquals(404, caughtStatusCode);
    }
}
