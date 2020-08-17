/*
 * Copyright 2012-2020 CodeLibs Project and the Others.
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

import org.apache.http.client.methods.CloseableHttpResponse;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;

import java.io.IOException;
import java.io.InputStream;

public class GetFileResponse implements SharePointApiResponse {
    private final CloseableHttpResponse httpResponse;

    public GetFileResponse(CloseableHttpResponse httpResponse) {
        this.httpResponse = httpResponse;
    }

    public InputStream getFileContent() throws IOException {
        return httpResponse.getEntity().getContent();
    }

    @Override
    public void close() throws IOException {
        httpResponse.close();
    }
}
