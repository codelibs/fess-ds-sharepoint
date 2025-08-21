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
package org.codelibs.fess.ds.sharepoint.client.api.list.getlistitems;

import java.util.Date;
import java.util.List;

import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;

/**
 * Response class for GetListItems API containing the retrieved list items.
 * This class encapsulates the results from a SharePoint list items query.
 */
public class GetListItemsResponse implements SharePointApiResponse {

    /** The list of items retrieved from SharePoint. */
    protected final List<ListItem> listItems;

    /**
     * Constructs a new GetListItemsResponse with the provided list items.
     *
     * @param listItems the list of items retrieved from SharePoint
     */
    public GetListItemsResponse(final List<ListItem> listItems) {
        this.listItems = listItems;
    }

    /**
     * Returns the list of items retrieved from SharePoint.
     *
     * @return the list of SharePoint list items
     */
    public List<ListItem> getListItems() {
        return listItems;
    }

    /**
     * Represents a single item from a SharePoint list.
     * Contains the essential metadata and properties of a list item.
     */
    public static class ListItem {
        private final String id;
        private final String editLink;
        private final String title;
        private final boolean attachments;
        private final Date created;
        private final Date modified;

        /**
         * Constructs a new ListItem with the specified properties.
         *
         * @param id the unique identifier of the list item
         * @param editLink the edit link for the item
         * @param title the title of the item
         * @param attachments true if the item has attachments, false otherwise
         * @param created the creation date of the item
         * @param modified the last modification date of the item
         */
        public ListItem(final String id, final String editLink, final String title, final boolean attachments, final Date created,
                final Date modified) {
            this.id = id;
            this.editLink = editLink;
            this.title = title;
            this.attachments = attachments;
            this.created = created;
            this.modified = modified;
        }

        /**
         * Returns the unique identifier of the list item.
         *
         * @return the item ID
         */
        public String getId() {
            return id;
        }

        /**
         * Returns the edit link for the list item.
         *
         * @return the edit link URL
         */
        public String getEditLink() {
            return editLink;
        }

        /**
         * Returns the title of the list item.
         *
         * @return the item title
         */
        public String getTitle() {
            return title;
        }

        /**
         * Returns whether the list item has attachments.
         *
         * @return true if the item has attachments, false otherwise
         */
        public boolean hasAttachments() {
            return attachments;
        }

        /**
         * Returns the creation date of the list item.
         *
         * @return the creation date
         */
        public Date getCreated() {
            return created;
        }

        /**
         * Returns the last modification date of the list item.
         *
         * @return the modification date
         */
        public Date getModified() {
            return modified;
        }
    }
}
