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

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;

/**
 * SharePoint API client for downloading individual files from SharePoint.
 * This class provides functionality to retrieve file content by server-relative URL
 * using SharePoint's REST API.
 */
public class GetFile extends SharePointApi<GetFileResponse> {
    /** Logger for this class. */
    private static final Logger logger = LogManager.getLogger(GetFile.class);

    /** The server-relative URL of the file to download. */
    private String serverRelativeUrl = null;

    /**
     * Constructs a new GetFile API client.
     *
     * @param client the HTTP client to use for requests
     * @param siteUrl the SharePoint site URL
     * @param oAuth the OAuth authentication provider
     */
    public GetFile(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    /**
     * Sets the server-relative URL of the file to download.
     *
     * @param serverRelativeUrl the server-relative URL of the target file
     * @return this instance for method chaining
     */
    public GetFile setServerRelativeUrl(final String serverRelativeUrl) {
        this.serverRelativeUrl = serverRelativeUrl;
        return this;
    }

    /**
     * Executes the file download request to SharePoint.
     *
     * @return a GetFileResponse containing the downloaded file content
     * @throws SharePointClientException if the server-relative URL is not set,
     *         if the request fails, or if an error response is received
     */
    @Override
    public GetFileResponse execute() {
        if (serverRelativeUrl == null) {
            throw new SharePointClientException("serverRelativeUrl is required.");
        }

        final String buildUrl = buildUrl();
        if (logger.isDebugEnabled()) {
            logger.debug("buildUrl: {}", buildUrl);
        }
        final HttpGet httpGet = new HttpGet(buildUrl);
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

    /**
     * Builds the SharePoint REST API URL for downloading a file by server-relative path.
     *
     * @return the complete API URL for the file download request
     */
    protected String buildUrl() {
        return siteUrl + "/_api/web/GetFileByServerRelativePath(decodedUrl='" + encodeRelativeUrl(serverRelativeUrl) + "')/$value";
    }
}
