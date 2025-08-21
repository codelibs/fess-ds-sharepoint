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
package org.codelibs.fess.ds.sharepoint.client2013.api.doclib.getfolders;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.doclib.getfolders.GetFolders;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;

/**
 * SharePoint 2013 specific implementation of GetFolders API.
 * This class extends the base GetFolders functionality for compatibility
 * with SharePoint 2013 REST API endpoints and XML response format.
 */
public class GetFolders2013 extends GetFolders {
    /** SharePoint 2013 REST API path template for getting folders */
    private static final String API_PATH = "_api/web/GetFolderByServerRelativeUrl('{{url}}')/Folders";
    /** URL parameters for pagination (skip and top parameters) */
    private static final String PAGING_PARAM = "%24skip={{start}}&%24top={{num}}";

    /** Server relative URL of the folder to get subfolders from */
    private String serverRelativeUrl = null;
    /** Maximum number of folders to retrieve (default: 100) */
    private int num = 100;
    /** Starting index for pagination (default: 0) */
    private int start = 0;

    /**
     * Constructs a new GetFolders2013 API instance.
     *
     * @param client HTTP client for making requests
     * @param siteUrl SharePoint site URL
     * @param oAuth OAuth authentication object
     */
    public GetFolders2013(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    /**
     * Sets the server relative URL of the folder to get subfolders from.
     *
     * @param serverRelativeUrl the server relative URL (required)
     * @return this instance for method chaining
     */
    @Override
    public GetFolders2013 setServerRelativeUrl(final String serverRelativeUrl) {
        this.serverRelativeUrl = serverRelativeUrl;
        return this;
    }

    /**
     * Sets the maximum number of folders to retrieve.
     *
     * @param num maximum number of folders (default: 100)
     * @return this instance for method chaining
     */
    @Override
    public GetFolders2013 setNum(final int num) {
        this.num = num;
        return this;
    }

    /**
     * Sets the starting index for pagination.
     *
     * @param start starting index (default: 0)
     * @return this instance for method chaining
     */
    @Override
    public GetFolders2013 setStart(final int start) {
        this.start = start;
        return this;
    }

    /**
     * Executes the API request to retrieve folders from SharePoint 2013.
     * This method uses XML response parsing specific to SharePoint 2013.
     *
     * @return GetFolders2013Response containing the list of folders
     * @throws SharePointClientException if serverRelativeUrl is not set or if the request fails
     */
    @Override
    public GetFolders2013Response execute() {
        if (serverRelativeUrl == null) {
            throw new SharePointClientException("serverRelativeUrl is required.");
        }

        final HttpGet httpGet = new HttpGet(siteUrl + "/" + API_PATH.replace("{{url}}", encodeRelativeUrl(serverRelativeUrl)) + "?"
                + PAGING_PARAM.replace("{{start}}", String.valueOf(start)).replace("{{num}}", String.valueOf(num)));
        final XmlResponse xmlResponse = doXmlRequest(httpGet);
        try {
            return GetFolders2013Response.build(xmlResponse);
        } catch (final Exception e) {
            throw new SharePointClientException(e);
        }
    }
}
