/*
 * Copyright 2012-2021 CodeLibs Project and the Others.
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

import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;

import java.util.List;

public class GetListsResponse implements SharePointApiResponse {
    protected final List<SharePointList> lists;

    public GetListsResponse(final List<SharePointList> lists) {
        this.lists = lists;
    }

    public List<SharePointList> getLists() {
        return lists;
    }

    public static class SharePointList {
        private final String id;
        private final String listName;
        private final boolean noCrawl;
        private final boolean hidden;
        private final String entityTypeName;

        public SharePointList(final String id, final String listName, final boolean noCrawl, final boolean hidden, final String entityTypeName) {
            this.id = id;
            this.listName = listName;
            this.noCrawl = noCrawl;
            this.hidden = hidden;
            this.entityTypeName = entityTypeName;
        }

        public String getId() {
            return id;
        }

        public String getListName() {
            return listName;
        }

        public boolean isNoCrawl() {
            return noCrawl;
        }

        public boolean isHidden() {
            return hidden;
        }

        public String getEntityTypeName() {
            return entityTypeName;
        }
    }

}
