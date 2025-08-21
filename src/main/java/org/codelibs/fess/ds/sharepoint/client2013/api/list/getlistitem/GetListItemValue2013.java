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
package org.codelibs.fess.ds.sharepoint.client2013.api.list.getlistitem;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemValue;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;

/**
 * SharePoint 2013 specific implementation for retrieving detailed field values of a list item.
 * This class extends the base GetListItemValue functionality with XML-based communication
 * suitable for SharePoint 2013 environments.
 */
public class GetListItemValue2013 extends GetListItemValue {
    private String listId = null;
    private String itemId = null;

    /**
     * Constructs a new GetListItemValue2013 instance.
     *
     * @param client the HTTP client for making requests
     * @param siteUrl the SharePoint site URL
     * @param oAuth the OAuth authentication handler
     */
    public GetListItemValue2013(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    /**
     * Sets the list ID for the SharePoint list containing the item.
     *
     * @param listId the unique identifier of the SharePoint list
     * @return this instance for method chaining
     */
    @Override
    public GetListItemValue2013 setListId(final String listId) {
        this.listId = listId;
        return this;
    }

    /**
     * Sets the item ID for the specific list item to retrieve.
     *
     * @param itemId the unique identifier of the list item
     * @return this instance for method chaining
     */
    @Override
    public GetListItemValue2013 setItemId(final String itemId) {
        this.itemId = itemId;
        return this;
    }

    /**
     * Executes the request to retrieve field values for the specified list item from SharePoint 2013.
     * Uses XML-based communication instead of JSON.
     *
     * @return the response containing the item's field values
     * @throws SharePointClientException if listId or itemId is not set, or if the request fails
     */
    @Override
    public GetListItemValue2013Response execute() {
        if (listId == null || itemId == null) {
            throw new SharePointClientException("listId/itemId is required.");
        }
        final HttpGet httpGet = new HttpGet(buildUrl());
        final XmlResponse xmlResponse = doXmlRequest(httpGet);
        try {
            return GetListItemValue2013Response.build(xmlResponse);
        } catch (final Exception e) {
            throw new SharePointClientException(e);
        }
    }

    /**
     * Builds the URL for the SharePoint 2013 API request.
     *
     * @return the complete URL for retrieving item field values
     */
    @Override
    protected String buildUrl() {
        return siteUrl + "/_api/Web/Lists(guid'" + listId + "')/Items(" + itemId + ")/FieldValuesAsText";
    }
}
