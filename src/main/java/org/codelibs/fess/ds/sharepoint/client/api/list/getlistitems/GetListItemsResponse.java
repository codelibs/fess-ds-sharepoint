/*
 * Copyright 2012-2020 CodeLibs Project and the Others.
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

import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;

import java.util.Date;
import java.util.List;

public class GetListItemsResponse implements SharePointApiResponse {

    protected final List<ListItem> listItems;

    public GetListItemsResponse(List<ListItem> listItems) {
        this.listItems = listItems;
    }

    public List<ListItem> getListItems() {
        return listItems;
    }

    public static class ListItem {
        private final String id;
        private final String editLink;
        private final String title;
        private final boolean attachments;
        private final Date created;
        private final Date modified;

        public ListItem(final String id, final String editLink, final String title, final boolean attachments, final Date created, final Date modified) {
            this.id = id;
            this.editLink = editLink;
            this.title = title;
            this.attachments = attachments;
            this.created = created;
            this.modified = modified;
        }

        public String getId() {
            return id;
        }

        public String getEditLink() {
            return editLink;
        }

        public String getTitle() {
            return title;
        }

        public boolean hasAttachments() {
            return attachments;
        }

        public Date getCreated() {
            return created;
        }

        public Date getModified() {
            return modified;
        }
    }
}
