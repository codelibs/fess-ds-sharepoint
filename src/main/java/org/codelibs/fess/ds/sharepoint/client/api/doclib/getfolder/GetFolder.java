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
package org.codelibs.fess.ds.sharepoint.client.api.doclib.getfolder;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;

/**
 * SharePoint API client for retrieving folder information from document libraries.
 * This class provides functionality to get folder details including metadata,
 * creation/modification dates, and item counts using SharePoint REST API.
 *
 * <p>Uses the GetFolderByServerRelativePath endpoint to retrieve folder information
 * by server-relative URL path.</p>
 *
 * @see GetFolderResponse
 * @see SharePointApi
 */
public class GetFolder extends SharePointApi<GetFolderResponse> {
    private static final Logger logger = LogManager.getLogger(GetFolder.class);

    private static final String API_PATH = "_api/web/GetFolderByServerRelativePath(decodedUrl='{{url}}')";

    /** The server-relative URL of the folder to retrieve */
    private String serverRelativeUrl = null;

    /**
     * Constructs a new GetFolder API client.
     *
     * @param client the HTTP client for making requests
     * @param siteUrl the base URL of the SharePoint site
     * @param oAuth the OAuth authentication provider
     */
    public GetFolder(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    /**
     * Sets the server-relative URL of the folder to retrieve.
     *
     * @param serverRelativeUrl the server-relative path to the folder (e.g., "/sites/mysite/documents/myfolder")
     * @return this instance for method chaining
     */
    public GetFolder setServerRelativeUrl(final String serverRelativeUrl) {
        this.serverRelativeUrl = serverRelativeUrl;
        return this;
    }

    /**
     * Executes the get folder request and returns the folder information.
     *
     * @return the folder response containing folder metadata and properties
     * @throws SharePointClientException if the server relative URL is not set,
     *         if the HTTP request fails, or if the response cannot be parsed
     */
    @Override
    public GetFolderResponse execute() {
        if (serverRelativeUrl == null) {
            throw new SharePointClientException("serverRelativeUrl is required.");
        }

        final String buildUrl = buildUrl();
        if (logger.isDebugEnabled()) {
            logger.debug("buildUrl: {}", buildUrl);
        }
        final HttpGet httpGet = new HttpGet(buildUrl);
        final JsonResponse jsonResponse = doJsonRequest(httpGet);
        try {
            return GetFolderResponse.build(jsonResponse);
        } catch (final Exception e) {
            throw new SharePointClientException(e);
        }
    }

    /**
     * Builds the complete API URL for the get folder request.
     *
     * @return the complete URL with encoded server relative path
     */
    private String buildUrl() {
        return siteUrl + "/" + API_PATH.replace("{{url}}", encodeRelativeUrl(serverRelativeUrl));
    }
}
