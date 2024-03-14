/*
 * Copyright 2012-2024 CodeLibs Project and the Others.
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
import org.codelibs.core.io.CopyUtil;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetFileResponse implements SharePointApiResponse {
    private static final Logger logger = LoggerFactory.getLogger(GetFileResponse.class);

    private final CloseableHttpResponse httpResponse;

    private final int cacheFileSize = 1_000_000;

    private byte[] responseData;

    private File responseFile;

    public GetFileResponse(final CloseableHttpResponse httpResponse) {
        this.httpResponse = httpResponse;
    }

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

    @Override
    public void close() throws IOException {
        if (responseFile != null && !responseFile.delete()) {
            logger.warn("Failed to delete {}.", responseFile.getAbsolutePath());
        }
        httpResponse.close();
    }
}
