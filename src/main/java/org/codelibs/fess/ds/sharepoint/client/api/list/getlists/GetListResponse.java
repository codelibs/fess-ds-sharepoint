package org.codelibs.fess.ds.sharepoint.client.api.list.getlists;

import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;

public class GetListResponse implements SharePointApiResponse {
    private final GetListsResponse.SharePointList list;

    public GetListResponse(GetListsResponse.SharePointList list) {
        this.list = list;
    }

    public GetListsResponse.SharePointList getList() {
        return list;
    }
}
