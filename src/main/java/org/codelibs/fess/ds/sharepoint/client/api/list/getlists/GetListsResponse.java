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
package org.codelibs.fess.ds.sharepoint.client.api.list.getlists;

import java.util.List;

import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;

/**
 * Response class for SharePoint get lists API.
 */
public class GetListsResponse implements SharePointApiResponse {
    /** List of SharePoint lists. */
    protected final List<SharePointList> lists;

    /**
     * Constructor.
     *
     * @param lists list of SharePoint lists
     */
    public GetListsResponse(final List<SharePointList> lists) {
        this.lists = lists;
    }

    /**
     * Gets the list of SharePoint lists.
     *
     * @return list of SharePoint lists
     */
    public List<SharePointList> getLists() {
        return lists;
    }

    /**
     * Represents a SharePoint list.
     */
    public static class SharePointList {
        private final String id;
        private final String listName;
        private final boolean noCrawl;
        private final boolean hidden;
        private final String entityTypeName;

        /**
         * Constructor.
         *
         * @param id the list ID
         * @param listName the list name
         * @param noCrawl whether crawling is disabled
         * @param hidden whether the list is hidden
         * @param entityTypeName the entity type name
         */
        public SharePointList(final String id, final String listName, final boolean noCrawl, final boolean hidden,
                final String entityTypeName) {
            this.id = id;
            this.listName = listName;
            this.noCrawl = noCrawl;
            this.hidden = hidden;
            this.entityTypeName = entityTypeName;
        }

        /**
         * Gets the list ID.
         *
         * @return the list ID
         */
        public String getId() {
            return id;
        }

        /**
         * Gets the list name.
         *
         * @return the list name
         */
        public String getListName() {
            return listName;
        }

        /**
         * Checks if crawling is disabled for this list.
         *
         * @return true if crawling is disabled
         */
        public boolean isNoCrawl() {
            return noCrawl;
        }

        /**
         * Checks if the list is hidden.
         *
         * @return true if the list is hidden
         */
        public boolean isHidden() {
            return hidden;
        }

        /**
         * Gets the entity type name.
         *
         * @return the entity type name
         */
        public String getEntityTypeName() {
            return entityTypeName;
        }
    }

}
