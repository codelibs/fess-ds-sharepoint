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
package org.codelibs.fess.ds.sharepoint.client2013.api.doclib.getfiles;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.doclib.getfiles.GetFiles;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;

/**
 * SharePoint 2013 specific implementation for retrieving files from document libraries.
 * Extends the base GetFiles class with SharePoint 2013 specific API endpoints and XML response handling.
 */
public class GetFiles2013 extends GetFiles {
    private static final String API_PATH = "_api/web/GetFolderByServerRelativeUrl('{{url}}')/Files";
    private static final String PAGING_PARAM = "%24skip={{start}}&%24top={{num}}";

    private String serverRelativeUrl = null;
    private int num = 100;
    private int start = 0;

    /**
     * Constructs a GetFiles2013 instance for SharePoint 2013 file retrieval operations.
     *
     * @param client the HTTP client for making requests
     * @param siteUrl the SharePoint site URL
     * @param oAuth the OAuth authentication handler
     */
    public GetFiles2013(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    /**
     * Sets the server relative URL of the folder to retrieve files from.
     *
     * @param serverRelativeUrl the server relative URL of the target folder
     * @return this instance for method chaining
     */
    @Override
    public GetFiles2013 setServerRelativeUrl(final String serverRelativeUrl) {
        this.serverRelativeUrl = serverRelativeUrl;
        return this;
    }

    /**
     * Sets the maximum number of files to retrieve in a single request.
     *
     * @param num the maximum number of files to retrieve
     * @return this instance for method chaining
     */
    @Override
    public GetFiles2013 setNum(final int num) {
        this.num = num;
        return this;
    }

    /**
     * Sets the starting index for pagination when retrieving files.
     *
     * @param start the starting index (0-based)
     * @return this instance for method chaining
     */
    @Override
    public GetFiles2013 setStart(final int start) {
        this.start = start;
        return this;
    }

    /**
     * Executes the file retrieval request and returns the response.
     *
     * @return GetFiles2013Response containing the retrieved files
     * @throws SharePointClientException if the serverRelativeUrl is not set or if the request fails
     */
    @Override
    public GetFiles2013Response execute() {
        if (serverRelativeUrl == null) {
            throw new SharePointClientException("serverRelativeUrl is required.");
        }

        final HttpGet httpGet = new HttpGet(siteUrl + "/" + API_PATH.replace("{{url}}", encodeRelativeUrl(serverRelativeUrl)) + "?"
                + PAGING_PARAM.replace("{{start}}", String.valueOf(start)).replace("{{num}}", String.valueOf(num)));
        final XmlResponse xmlResponse = doXmlRequest(httpGet);
        try {
            return GetFiles2013Response.build(xmlResponse);
        } catch (final Exception e) {
            throw new SharePointClientException(e);
        }
    }
}
