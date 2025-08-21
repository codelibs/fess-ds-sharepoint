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
 * API class for retrieving SharePoint list item attachments.
 * This class handles REST API calls to get attachment files associated with a specific list item.
 */
public class GetListItemAttachments extends SharePointApi<GetListItemAttachmentsResponse> {
    private static final Logger logger = LogManager.getLogger(GetListItemAttachments.class);

    private String listId = null;
    private String itemId = null;

    /**
     * Constructs a new GetListItemAttachments instance.
     *
     * @param client the HTTP client for making requests
     * @param siteUrl the SharePoint site URL
     * @param oAuth the OAuth authentication object
     */
    public GetListItemAttachments(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    /**
     * Sets the list ID and item ID for the attachments to retrieve.
     *
     * @param listId the GUID of the SharePoint list
     * @param itemId the ID of the list item
     * @return this GetListItemAttachments instance for method chaining
     */
    public GetListItemAttachments setId(final String listId, final String itemId) {
        this.listId = listId;
        this.itemId = itemId;
        return this;
    }

    @Override
    public GetListItemAttachmentsResponse execute() {
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
            return GetListItemAttachmentsResponse.build(jsonResponse);
        } catch (final Exception e) {
            throw new SharePointClientException(e);
        }
    }

    /**
     * Builds the URL for the attachment files API endpoint.
     *
     * @return the complete URL for retrieving attachment files
     */
    protected String buildUrl() {
        return siteUrl + "/_api/Web/Lists(guid'" + listId + "')/Items(" + itemId + ")/AttachmentFiles";
    }
}
