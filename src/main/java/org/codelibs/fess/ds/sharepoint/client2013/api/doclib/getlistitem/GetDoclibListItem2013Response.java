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
package org.codelibs.fess.ds.sharepoint.client2013.api.doclib.getlistitem;

import org.codelibs.fess.ds.sharepoint.client.api.doclib.getlistitem.GetDoclibListItemResponse;

/**
 * SharePoint 2013 specific response object for document library list item metadata.
 * This response extends the base GetDoclibListItemResponse to provide SharePoint 2013
 * compatibility while maintaining the same interface.
 */
public class GetDoclibListItem2013Response extends GetDoclibListItemResponse {
    /**
     * Constructs a new GetDoclibListItem2013Response with the specified list and item IDs.
     *
     * @param listId the unique identifier of the SharePoint list
     * @param itemId the unique identifier of the list item
     */
    public GetDoclibListItem2013Response(final String listId, final String itemId) {
        super(listId, itemId);
    }

    /**
     * Returns the unique identifier of the SharePoint list.
     *
     * @return the list ID
     */
    @Override
    public String getListId() {
        return listId;
    }

    /**
     * Returns the unique identifier of the list item.
     *
     * @return the item ID
     */
    @Override
    public String getItemId() {
        return itemId;
    }
}
