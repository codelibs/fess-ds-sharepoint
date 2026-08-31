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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemAttachments;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;

/**
 * SharePoint 2013 implementation for retrieving list item attachments.
 * This class extends GetListItemAttachments to provide SharePoint 2013-specific XML-based API functionality.
 */
public class GetListItemAttachments2013 extends GetListItemAttachments {
    private static final Logger logger = LogManager.getLogger(GetListItemAttachments2013.class);

    /**
     * Upper bound on the number of pages this request may follow via the Atom feed's
     * {@code rel="next"} link. See {@code GetLists#execute()} for the reasoning.
     */
    private static final int MAX_PAGES = 100;

    private String listId = null;
    private String itemId = null;

    /**
     * Constructs a new GetListItemAttachments2013 instance.
     *
     * @param client the HTTP client for making requests
     * @param siteUrl the SharePoint site URL
     * @param oAuth the OAuth authentication object
     */
    public GetListItemAttachments2013(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    @Override
    public GetListItemAttachments2013 setId(final String listId, final String itemId) {
        this.listId = listId;
        this.itemId = itemId;
        return this;
    }

    @Override
    public GetListItemAttachments2013Response execute() {
        if (listId == null || itemId == null) {
            throw new SharePointClientException("listId/itemId is required.");
        }
        final GetListItemAttachments2013Response response = new GetListItemAttachments2013Response();
        String url = buildUrl();
        for (int page = 0; page < MAX_PAGES; page++) {
            final HttpGet httpGet = new HttpGet(url);
            final XmlResponse xmlResponse = doXmlRequest(httpGet);
            try {
                final GetListItemAttachments2013Response pageResponse = GetListItemAttachments2013Response.build(xmlResponse);
                response.getFiles().addAll(pageResponse.getFiles());
                final String nextLink = pageResponse.getNextLink();
                if (nextLink == null) {
                    return response;
                }
                url = nextLink;
            } catch (final Exception e) {
                throw new SharePointClientException(e);
            }
            if (page == MAX_PAGES - 1) {
                logger.warn("Stopped listing the attachments of item {} after {} pages; the listing may be truncated.", itemId, MAX_PAGES);
            }
        }
        return response;
    }

    @Override
    protected String buildUrl() {
        return siteUrl + "/_api/Web/Lists(guid'" + listId + "')/Items(" + itemId + ")/AttachmentFiles";
    }
}
