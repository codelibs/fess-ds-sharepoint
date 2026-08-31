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
package org.codelibs.fess.ds.sharepoint.client.api.file.getfile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.io.output.DeferredFileOutputStream;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fess.crawler.exception.MaxLengthExceededException;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;

/**
 * Response wrapper for SharePoint file download operations.
 * This class handles the HTTP response from SharePoint file download requests
 * and provides access to the file content as an InputStream. It manages
 * memory-efficient streaming by using deferred file output for large files.
 */
public class GetFileResponse implements SharePointApiResponse {
    /** Logger for this class. */
    private static final Logger logger = LogManager.getLogger(GetFileResponse.class);

    /** The underlying HTTP response from SharePoint. */
    private final CloseableHttpResponse httpResponse;

    /** Size threshold (1MB) for caching file content in memory vs. temporary file. */
    private final int cacheFileSize = 1_000_000;

    /** Cached response data for small files (under cacheFileSize). */
    private byte[] responseData;

    /** Temporary file for large files (over cacheFileSize). */
    private File responseFile;

    /**
     * Constructs a new GetFileResponse.
     *
     * @param httpResponse the HTTP response from the SharePoint file download request
     */
    public GetFileResponse(final CloseableHttpResponse httpResponse) {
        this.httpResponse = httpResponse;
    }

    /**
     * Returns the response's {@code Content-Length}, read from the HTTP entity without consuming
     * it - safe to call before {@link #getFileContent(long)}, which is the only method that
     * downloads the body.
     *
     * <p>{@code GetFiles} carries no size for a listed file and {@code GetFilesResponse.DocLibFile}
     * has no size field, so this is the only low-cost way to bound a file's size before spending
     * the request its download would otherwise cost. This returns {@code -1} whenever the entity
     * does not declare an upfront length - not only for chunked transfer encoding, but for any
     * response {@code HttpClientBuilder} has wrapped in a decompressing entity: this plugin's own
     * client never calls {@code disableContentCompression()}, so a gzip-encoded response (nothing
     * unusual for a text-heavy SharePoint document) also makes this return -1, per
     * {@code DecompressingEntity#getContentLength()}'s own contract. The caller has no pre-download
     * signal to check in that case; see {@link #getFileContent(long)} for the bound that still
     * applies then.
     *
     * @return the content length in bytes, or -1 if the response does not declare one
     */
    public long getContentLength() {
        final HttpEntity entity = httpResponse.getEntity();
        return entity == null ? -1 : entity.getContentLength();
    }

    /**
     * Gets the file content as an InputStream, downloading it on the first call and caching it
     * (in memory below the cache threshold, otherwise in a temporary file) for any later call.
     *
     * @return an InputStream containing the file content
     * @throws IOException if an error occurs while reading the file content
     */
    public InputStream getFileContent() throws IOException {
        return getFileContent(-1);
    }

    /**
     * Gets the file content as an InputStream, bounding the number of bytes copied out of the
     * response regardless of what (if anything) {@link #getContentLength()} reported.
     *
     * <p>{@link #getContentLength()} is a precheck against a declared length, which - as its own
     * javadoc explains - a gzip-encoded or chunked response never declares. This is the backstop
     * for exactly that case: it counts bytes as they are copied and stops before buffering more
     * than {@code maxContentLength}, so a file that lied about (or never declared) its size still
     * cannot be downloaded in full before it is rejected.
     *
     * @param maxContentLength the maximum number of bytes to copy, or a negative number for no
     *            limit
     * @return an InputStream containing the file content
     * @throws IOException if an error occurs while reading the file content
     * @throws MaxLengthExceededException if the body is larger than {@code maxContentLength}
     */
    public InputStream getFileContent(final long maxContentLength) throws IOException {
        if (responseData == null && responseFile == null) {
            HttpEntity entity = null;
            try (DeferredFileOutputStream out =
                    DeferredFileOutputStream.builder().setThreshold(cacheFileSize).setPrefix("fess-extractor-").setSuffix(".out").get()) {
                entity = httpResponse.getEntity();
                copyBounded(entity.getContent(), out, maxContentLength);
                out.flush();

                if (out.isInMemory()) {
                    responseData = out.getData();
                } else {
                    responseFile = out.getFile();
                }
            } finally {
                EntityUtils.consumeQuietly(entity);
            }
        }
        if (responseData != null) {
            return new ByteArrayInputStream(responseData);
        }
        return new FileInputStream(responseFile);
    }

    /**
     * Copies {@code in} to {@code out}, counting bytes as they are read and stopping before more
     * than {@code maxContentLength} have been buffered.
     *
     * @param in the source stream
     * @param out the destination stream
     * @param maxContentLength the maximum number of bytes to copy, or a negative number for no
     *            limit
     * @throws IOException if an error occurs while reading or writing
     * @throws MaxLengthExceededException if more than {@code maxContentLength} bytes were read
     */
    private void copyBounded(final InputStream in, final OutputStream out, final long maxContentLength) throws IOException {
        final byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (maxContentLength >= 0 && total > maxContentLength) {
                throw new MaxLengthExceededException(
                        "The content length is over " + maxContentLength + " byte. (Read " + total + " byte(s) so far.)");
            }
            out.write(buffer, 0, read);
        }
    }

    /**
     * Closes the response and cleans up resources.
     * This includes deleting any temporary files and closing the HTTP response.
     *
     * @throws IOException if an error occurs while closing resources
     */
    @Override
    public void close() throws IOException {
        if (responseFile != null && !responseFile.delete()) {
            logger.warn("Failed to delete {}.", responseFile.getAbsolutePath());
        }
        httpResponse.close();
    }
}
