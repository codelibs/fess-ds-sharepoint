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

public class SharePointClientException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SharePointClientException() {
    }

    public SharePointClientException(final String message) {
        super(message);
    }

    public SharePointClientException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public SharePointClientException(final Throwable cause) {
        super(cause);
    }

    public SharePointClientException(final String message, final Throwable cause, final boolean enableSuppression,
            final boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
