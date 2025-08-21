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

import org.apache.commons.io.output.DeferredFileOutputStream;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.core.io.CopyUtil;
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
     * Gets the file content as an InputStream.
     * For files smaller than the cache size threshold (1MB), content is stored in memory.
     * For larger files, content is stored in a temporary file and streamed from disk.
     *
     * @return an InputStream containing the file content
     * @throws IOException if an error occurs while reading the file content
     */
    public InputStream getFileContent() throws IOException {
        if (responseData == null && responseFile == null) {
            HttpEntity entity = null;
            try (DeferredFileOutputStream out =
                    DeferredFileOutputStream.builder().setThreshold(cacheFileSize).setPrefix("fess-extractor-").setSuffix(".out").get()) {
                entity = httpResponse.getEntity();
                CopyUtil.copy(entity.getContent(), out);
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
