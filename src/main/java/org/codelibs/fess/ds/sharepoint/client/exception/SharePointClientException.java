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
 * General exception thrown by SharePoint client operations.
 * This exception is used to wrap various errors that can occur during
 * SharePoint API interactions, including network issues, authentication failures,
 * and API response parsing errors.
 *
 * <p>This is a runtime exception that indicates a problem with SharePoint
 * client operations that typically cannot be recovered from at runtime.</p>
 */
public class SharePointClientException extends RuntimeException {
    /** Serial version UID for serialization */
    private static final long serialVersionUID = 1L;

    /**
     * The HTTP status code that produced this exception, or -1 if none is known - either because
     * this exception did not originate from an HTTP response at all (a network failure, a parsing
     * error) or because the constructor used did not carry one.
     */
    private final int statusCode;

    /**
     * Constructs a new SharePointClientException with no detail message.
     */
    public SharePointClientException() {
        this.statusCode = -1;
    }

    /**
     * Constructs a new SharePointClientException with the specified detail message.
     *
     * @param message the detail message explaining the cause of the exception
     */
    public SharePointClientException(final String message) {
        super(message);
        this.statusCode = -1;
    }

    /**
     * Constructs a new SharePointClientException for an HTTP error response, carrying its status
     * code.
     *
     * <p>{@code GetFile}/{@code GetFile2013} report every HTTP error response this way (unlike the
     * shared {@code SharePointApi#doJsonRequest}/{@code doXmlRequest} path, which reports one as a
     * {@code SharePointServerException} instead) so {@code SharePointCrawler#doCrawl}'s retry loop
     * can still recognize a 503 from a file download and back off before retrying it, the same way
     * it already does for a 503 from any other API call.
     *
     * @param message the detail message explaining the cause of the exception
     * @param statusCode the HTTP status code returned by the server
     */
    public SharePointClientException(final String message, final int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    /**
     * Constructs a new SharePointClientException with the specified detail message and cause.
     *
     * @param message the detail message explaining the cause of the exception
     * @param cause the underlying cause of this exception
     */
    public SharePointClientException(final String message, final Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    /**
     * Constructs a new SharePointClientException with the specified cause.
     *
     * @param cause the underlying cause of this exception
     */
    public SharePointClientException(final Throwable cause) {
        super(cause);
        this.statusCode = -1;
    }

    /**
     * Constructs a new SharePointClientException with the specified parameters.
     *
     * @param message the detail message explaining the cause of the exception
     * @param cause the underlying cause of this exception
     * @param enableSuppression whether suppression should be enabled
     * @param writableStackTrace whether the stack trace should be writable
     */
    public SharePointClientException(final String message, final Throwable cause, final boolean enableSuppression,
            final boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.statusCode = -1;
    }

    /**
     * Returns the HTTP status code that produced this exception.
     *
     * @return the HTTP status code, or -1 if none is known
     */
    public int getStatusCode() {
        return statusCode;
    }
}
