package org.codelibs.fess.ds.sharepoint.client.api.list.getlistitems;

import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;

import java.util.Date;
import java.util.List;

public class GetListItemsResponse implements SharePointApiResponse {

    private final List<ListItem> listItems;

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
