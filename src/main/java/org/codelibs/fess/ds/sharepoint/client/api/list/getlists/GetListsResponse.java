package org.codelibs.fess.ds.sharepoint.client.api.list.getlists;

import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;

import java.util.List;

public class GetListsResponse implements SharePointApiResponse {
    private final List<SharePointList> lists;

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

        public SharePointList(final String id, final String listName, final boolean noCrawl) {
            this.id = id;
            this.listName = listName;
            this.noCrawl = noCrawl;
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
    }

}
