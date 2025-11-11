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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.codelibs.fess.util.ComponentUtil;
import org.dbflute.utflute.lastaflute.LastaFluteTestCase;
import org.junit.Test;

public class SharePointClientExceptionTest extends LastaFluteTestCase {

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
    public void test_constructor_noArgs() {
        final SharePointClientException exception = new SharePointClientException();
        assertNotNull(exception);
        assertNull(exception.getMessage());
    }

    @Test
    public void test_constructor_withMessage() {
        final String message = "Test error message";
        final SharePointClientException exception = new SharePointClientException(message);

        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
    }

    @Test
    public void test_constructor_withMessageAndCause() {
        final String message = "Test error message";
        final Throwable cause = new RuntimeException("Cause exception");
        final SharePointClientException exception = new SharePointClientException(message, cause);

        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    public void test_constructor_withCause() {
        final Throwable cause = new RuntimeException("Cause exception");
        final SharePointClientException exception = new SharePointClientException(cause);

        assertNotNull(exception);
        assertEquals(cause, exception.getCause());
    }

    @Test
    public void test_constructor_withAllParameters() {
        final String message = "Test error message";
        final Throwable cause = new RuntimeException("Cause exception");
        final boolean enableSuppression = true;
        final boolean writableStackTrace = true;

        final SharePointClientException exception = new SharePointClientException(message, cause, enableSuppression, writableStackTrace);

        assertNotNull(exception);
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    public void test_isRuntimeException() {
        final SharePointClientException exception = new SharePointClientException("Test");
        assertTrue(exception instanceof RuntimeException);
    }

    @Test
    public void test_throwAndCatch() {
        try {
            throw new SharePointClientException("Test exception");
        } catch (final SharePointClientException e) {
            assertEquals("Test exception", e.getMessage());
        }
    }

    @Test
    public void test_exceptionChaining() {
        final RuntimeException cause = new RuntimeException("Root cause");
        final SharePointClientException exception = new SharePointClientException("Wrapped exception", cause);

        assertEquals("Wrapped exception", exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertEquals("Root cause", exception.getCause().getMessage());
    }
}
