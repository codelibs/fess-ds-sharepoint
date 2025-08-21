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

import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;

/**
 * Response object containing document library list item metadata retrieved from SharePoint.
 * This response includes the list ID and item ID for a specific file or folder.
 */
public class GetDoclibListItemResponse implements SharePointApiResponse {
    /** The unique identifier of the SharePoint list containing the item. */
    protected final String listId;
    /** The unique identifier of the specific list item. */
    protected final String itemId;

    /**
     * Constructs a new GetDoclibListItemResponse with the specified list and item IDs.
     *
     * @param listId the unique identifier of the SharePoint list
     * @param itemId the unique identifier of the list item
     */
    public GetDoclibListItemResponse(final String listId, final String itemId) {
        this.listId = listId;
        this.itemId = itemId;
    }

    /**
     * Returns the unique identifier of the SharePoint list.
     *
     * @return the list ID
     */
    public String getListId() {
        return listId;
    }

    /**
     * Returns the unique identifier of the list item.
     *
     * @return the item ID
     */
    public String getItemId() {
        return itemId;
    }
}
