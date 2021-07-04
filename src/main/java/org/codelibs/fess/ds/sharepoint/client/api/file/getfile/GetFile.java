/*
 * Copyright 2012-2021 CodeLibs Project and the Others.
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
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;

public class GetFile extends SharePointApi<GetFileResponse> {
    private String serverRelativeUrl = null;

    public GetFile(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    public GetFile setServerRelativeUrl(final String serverRelativeUrl) {
        this.serverRelativeUrl = serverRelativeUrl;
        return this;
    }

    @Override
    public GetFileResponse execute() {
        if (serverRelativeUrl == null) {
            throw new SharePointClientException("serverRelativeUrl is required.");
        }

        final HttpGet httpGet = new HttpGet(buildUrl());
        httpGet.addHeader("Accept", "application/json");
        if (oAuth != null) {
            oAuth.apply(httpGet);
        }
        try {
            final CloseableHttpResponse httpResponse = client.execute(httpGet);
            if (isErrorResponse(httpResponse)) {
                throw new SharePointClientException("GetFile Request failure. status:" + httpResponse.getStatusLine().getStatusCode()
                        + " body:" + EntityUtils.toString(httpResponse.getEntity()));
            }
            return new GetFileResponse(httpResponse);
        } catch (final Exception e) {
            throw new SharePointClientException("GetFile Request failure.", e);
        }
    }

    protected String buildUrl() {
        return siteUrl + "/_api/web/GetFileByServerRelativeUrl('" + encodeRelativeUrl(serverRelativeUrl) + "')/$value";
    }
}
