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
package org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;

/**
 * API class for retrieving detailed field values of a specific SharePoint list item.
 * This class provides functionality to fetch all field values for a single item.
 */
public class GetListItemValue extends SharePointApi<GetListItemValueResponse> {
    private static final Logger logger = LogManager.getLogger(GetListItemValue.class);

    private String listId = null;
    private String itemId = null;

    /**
     * Constructs a new GetListItemValue instance.
     *
     * @param client the HTTP client for making requests
     * @param siteUrl the SharePoint site URL
     * @param oAuth the OAuth authentication handler
     */
    public GetListItemValue(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    /**
     * Sets the list ID for the SharePoint list containing the item.
     *
     * @param listId the unique identifier of the SharePoint list
     * @return this instance for method chaining
     */
    public GetListItemValue setListId(final String listId) {
        this.listId = listId;
        return this;
    }

    /**
     * Sets the item ID for the specific list item to retrieve.
     *
     * @param itemId the unique identifier of the list item
     * @return this instance for method chaining
     */
    public GetListItemValue setItemId(final String itemId) {
        this.itemId = itemId;
        return this;
    }

    /**
     * Executes the request to retrieve field values for the specified list item.
     *
     * @return the response containing the item's field values
     * @throws SharePointClientException if listId or itemId is not set, or if the request fails
     */
    @Override
    public GetListItemValueResponse execute() {
        if (listId == null || itemId == null) {
            throw new SharePointClientException("listId/itemId is required.");
        }
        final String buildUrl = buildUrl();
        if (logger.isDebugEnabled()) {
            logger.debug("buildUrl: {}", buildUrl);
        }
        final HttpGet httpGet = new HttpGet(buildUrl);
        final JsonResponse jsonResponse = doJsonRequest(httpGet);
        try {
            return GetListItemValueResponse.build(jsonResponse);
        } catch (final Exception e) {
            throw new SharePointClientException(e);
        }
    }

    /**
     * Builds the URL for the SharePoint API request.
     *
     * @return the complete URL for retrieving item field values
     */
    protected String buildUrl() {
        return siteUrl + "/_api/Web/Lists(guid'" + listId + "')/Items(" + itemId + ")/FieldValuesAsText";
    }
}
