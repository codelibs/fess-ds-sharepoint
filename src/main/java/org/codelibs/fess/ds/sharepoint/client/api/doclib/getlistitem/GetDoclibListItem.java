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
package org.codelibs.fess.ds.sharepoint.client.api.doclib.getlistitem;

import java.util.Map;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;
import org.codelibs.fess.util.DocumentUtil;

/**
 * SharePoint API client for retrieving document library list item metadata.
 * This class provides functionality to fetch list item information for files
 * stored in SharePoint document libraries using the REST API.
 */
public class GetDoclibListItem extends SharePointApi<GetDoclibListItemResponse> {
    /** Logger instance for this class. */
    private static final Logger logger = LogManager.getLogger(GetDoclibListItem.class);

    /** The server-relative URL of the SharePoint folder/file. */
    private String serverRelativeUrl = null;

    /**
     * Constructs a new GetDoclibListItem API client.
     *
     * @param client the HTTP client for making requests
     * @param siteUrl the SharePoint site URL
     * @param oAuth the OAuth authentication handler
     */
    public GetDoclibListItem(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    /**
     * Sets the server-relative URL of the SharePoint folder/file to retrieve list item data for.
     *
     * @param serverRelativeUrl the server-relative URL path
     * @return this instance for method chaining
     */
    public GetDoclibListItem setServerRelativeUrl(final String serverRelativeUrl) {
        this.serverRelativeUrl = serverRelativeUrl;
        return this;
    }

    /**
     * Executes the API request to retrieve document library list item metadata.
     *
     * @return the response containing list ID and item ID
     * @throws SharePointClientException if serverRelativeUrl is not set or if the request fails
     */
    @Override
    public GetDoclibListItemResponse execute() {
        if (serverRelativeUrl == null) {
            throw new SharePointClientException("serverRelativeUrl is required.");
        }

        final String buildUrl = buildUrl();
        if (logger.isDebugEnabled()) {
            logger.debug("buildUrl: {}", buildUrl);
        }
        final HttpGet httpGet = new HttpGet(buildUrl);
        final JsonResponse jsonResponse = doJsonRequest(httpGet);
        final Map<String, Object> bodyMap = jsonResponse.getBodyAsMap();
        try {
            final String itemId = DocumentUtil.getValue(bodyMap, "Id", String.class);
            final String listId = getListId(DocumentUtil.getValue(bodyMap, "odata.editLink", String.class));
            return new GetDoclibListItemResponse(listId, itemId);
        } catch (final Exception e) {
            throw new SharePointClientException(e);
        }
    }

    /**
     * Builds the SharePoint REST API URL for retrieving list item fields.
     *
     * @return the complete API URL
     */
    protected String buildUrl() {
        return siteUrl + "_api/Web/GetFolderByServerRelativePath(decodedurl='" + encodeRelativeUrl(serverRelativeUrl)
                + "')/ListItemAllFields";
    }

    /**
     * Extracts the list ID from the OData edit link.
     *
     * @param editLink the OData edit link containing the list GUID
     * @return the extracted list ID, or null if editLink is null
     */
    protected String getListId(final String editLink) {
        if (editLink == null) {
            return null;
        }
        return editLink.substring(editLink.indexOf("(guid'") + "(guid'".length(), editLink.indexOf("')"));
    }
}
