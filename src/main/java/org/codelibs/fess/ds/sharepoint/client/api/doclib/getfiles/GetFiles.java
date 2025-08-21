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
package org.codelibs.fess.ds.sharepoint.client.api.doclib.getfiles;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;

/**
 * API class for retrieving files from a SharePoint document library folder.
 */
public class GetFiles extends SharePointApi<GetFilesResponse> {
    private static final Logger logger = LogManager.getLogger(GetFiles.class);

    private static final String API_PATH = "_api/web/GetFolderByServerRelativePath(decodedUrl='{{url}}')/Files";
    private static final String PAGING_PARAM = "%24skip={{start}}&%24top={{num}}";

    private String serverRelativeUrl = null;
    private int num = 100;
    private int start = 0;

    /**
     * Constructs a GetFiles API instance.
     *
     * @param client the HTTP client for making requests
     * @param siteUrl the SharePoint site URL
     * @param oAuth the OAuth authentication handler
     */
    public GetFiles(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    /**
     * Sets the server-relative URL of the folder to get files from.
     *
     * @param serverRelativeUrl the server-relative URL of the folder
     * @return this GetFiles instance for method chaining
     */
    public GetFiles setServerRelativeUrl(final String serverRelativeUrl) {
        this.serverRelativeUrl = serverRelativeUrl;
        return this;
    }

    /**
     * Sets the maximum number of files to retrieve.
     *
     * @param num the maximum number of files to retrieve
     * @return this GetFiles instance for method chaining
     */
    public GetFiles setNum(final int num) {
        this.num = num;
        return this;
    }

    /**
     * Sets the starting index for pagination.
     *
     * @param start the starting index for pagination
     * @return this GetFiles instance for method chaining
     */
    public GetFiles setStart(final int start) {
        this.start = start;
        return this;
    }

    @Override
    public GetFilesResponse execute() {
        if (serverRelativeUrl == null) {
            throw new SharePointClientException("serverRelativeUrl is required.");
        }

        final String buildUrl = buildUrl();
        if (logger.isDebugEnabled()) {
            logger.debug("buildUrl: {}", buildUrl);
        }
        final HttpGet httpGet = new HttpGet(buildUrl);
        final SharePointApi.JsonResponse jsonResponse = doJsonRequest(httpGet);
        try {
            return GetFilesResponse.build(jsonResponse);
        } catch (final Exception e) {
            throw new SharePointClientException(e);
        }
    }

    private String buildUrl() {
        return siteUrl + "/" + API_PATH.replace("{{url}}", encodeRelativeUrl(serverRelativeUrl)) + "?"
                + PAGING_PARAM.replace("{{start}}", String.valueOf(start)).replace("{{num}}", String.valueOf(num));
    }
}
