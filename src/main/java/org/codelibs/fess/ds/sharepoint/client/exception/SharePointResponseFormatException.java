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
 * Thrown when a SharePoint response's {@code Content-Type} does not match what the request's
 * {@code Accept} header asked for - most commonly a server with JSON Light OData support disabled
 * answering a JSON request with Atom XML instead.
 *
 * <p>Deliberately does not extend {@link SharePointServerException} or
 * {@link SharePointClientException}: {@code SharePointCrawler#doCrawl}'s retry loop only retries
 * those two types. A {@code Content-Type} mismatch is a deterministic configuration problem, not a
 * transient one - retrying it wastes the whole retry budget on a request that will fail the same
 * way every time, arriving at a message ("Unexpected character") that says nothing about the
 * actual cause. Every other exception, this one included, falls into that retry loop's generic
 * {@code catch (Exception e)}, which fails the crawl target immediately with whatever message this
 * exception carries instead.
 *
 * <p>This is a runtime exception; it is not meant to be recovered from at runtime.</p>
 */
public class SharePointResponseFormatException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new SharePointResponseFormatException with the specified detail message.
     *
     * @param message the detail message explaining the mismatch
     */
    public SharePointResponseFormatException(final String message) {
        super(message);
    }
}
