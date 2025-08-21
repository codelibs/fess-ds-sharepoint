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

/**
 * Exception thrown when SharePoint server returns an HTTP error status.
 * This exception captures both the error message and the HTTP status code
 * returned by the SharePoint server to help with debugging and error handling.
 *
 * <p>This exception typically indicates server-side issues such as
 * authentication failures, permission denied, resource not found, or
 * internal server errors.</p>
 */
public class SharePointServerException extends RuntimeException {
    /** Serial version UID for serialization */
    private static final long serialVersionUID = 1L;

    /** HTTP status code returned by the SharePoint server */
    private final int statusCode;

    /**
     * Constructs a new SharePointServerException with the specified message and status code.
     *
     * @param message the detail message explaining the server error
     * @param statusCode the HTTP status code returned by the SharePoint server
     */
    public SharePointServerException(final String message, final int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    /**
     * Gets the HTTP status code returned by the SharePoint server.
     *
     * @return the HTTP status code
     */
    public int getStatusCode() {
        return statusCode;
    }
}
