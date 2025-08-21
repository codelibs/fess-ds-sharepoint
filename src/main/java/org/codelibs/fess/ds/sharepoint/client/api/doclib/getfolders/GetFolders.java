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
package org.codelibs.fess.ds.sharepoint.client.api.doclib.getfolders;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;

/**
 * API class for retrieving folders from SharePoint document libraries.
 * This class provides functionality to get a list of folders (subdirectories)
 * from a specified location in SharePoint using the REST API.
 */
public class GetFolders extends SharePointApi<GetFoldersResponse> {
    /** Logger for this class */
    private static final Logger logger = LogManager.getLogger(GetFolders.class);

    /** SharePoint REST API path template for getting folders */
    private static final String API_PATH = "_api/web/GetFolderByServerRelativePath(decodedUrl='{{url}}')/Folders";
    /** URL parameters for pagination (skip and top parameters) */
    private static final String PAGING_PARAM = "%24skip={{start}}&%24top={{num}}";

    /** Server relative URL of the folder to get subfolders from */
    private String serverRelativeUrl = null;
    /** Maximum number of folders to retrieve (default: 100) */
    private int num = 100;
    /** Starting index for pagination (default: 0) */
    private int start = 0;

    /**
     * Constructs a new GetFolders API instance.
     *
     * @param client HTTP client for making requests
     * @param siteUrl SharePoint site URL
     * @param oAuth OAuth authentication object
     */
    public GetFolders(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    /**
     * Sets the server relative URL of the folder to get subfolders from.
     *
     * @param serverRelativeUrl the server relative URL (required)
     * @return this instance for method chaining
     */
    public GetFolders setServerRelativeUrl(final String serverRelativeUrl) {
        this.serverRelativeUrl = serverRelativeUrl;
        return this;
    }

    /**
     * Sets the maximum number of folders to retrieve.
     *
     * @param num maximum number of folders (default: 100)
     * @return this instance for method chaining
     */
    public GetFolders setNum(final int num) {
        this.num = num;
        return this;
    }

    /**
     * Sets the starting index for pagination.
     *
     * @param start starting index (default: 0)
     * @return this instance for method chaining
     */
    public GetFolders setStart(final int start) {
        this.start = start;
        return this;
    }

    /**
     * Executes the API request to retrieve folders from SharePoint.
     *
     * @return GetFoldersResponse containing the list of folders
     * @throws SharePointClientException if serverRelativeUrl is not set or if the request fails
     */
    @Override
    public GetFoldersResponse execute() {
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
            return GetFoldersResponse.build(jsonResponse);
        } catch (final Exception e) {
            throw new SharePointClientException(e);
        }
    }

    /**
     * Builds the complete URL for the SharePoint API request.
     *
     * @return the complete URL with parameters
     */
    private String buildUrl() {
        return siteUrl + "/" + API_PATH.replace("{{url}}", encodeRelativeUrl(serverRelativeUrl)) + "?"
                + PAGING_PARAM.replace("{{start}}", String.valueOf(start)).replace("{{num}}", String.valueOf(num));
    }
}
