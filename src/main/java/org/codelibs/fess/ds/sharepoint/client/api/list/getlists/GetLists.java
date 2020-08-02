package org.codelibs.fess.ds.sharepoint.client.api.list.getlists;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GetLists extends SharePointApi<GetListsResponse> {
    private static final String API_PATH = "_api/lists";

    public GetLists(CloseableHttpClient client, String siteUrl) {
        super(client, siteUrl);
    }

    @Override
    public GetListsResponse execute() {
        final HttpGet httpGet = new HttpGet(siteUrl + "/" + API_PATH);
        JsonResponse jsonResponse = doRequest(httpGet);
        return buildResponse(jsonResponse);
    }

    @SuppressWarnings("unchecked")
    private GetListsResponse buildResponse(final JsonResponse jsonResponse) {
        final Map<String, Object> jsonMap = jsonResponse.getBodyAsMap();

        final List<GetListsResponse.SharePointList> sharePointLists = new ArrayList<>();
        final List<Map<String, Object>> valueList = (List)jsonMap.get("value");
        valueList.forEach(value -> {
            Object titleObj = value.get("Title");
            if (titleObj == null) {
                return;
            }
            Object idObj = value.get("Id");
            if (idObj == null) {
                return;
            }
            Object noCrawl = value.get("NoCrawl");
            if (noCrawl == null) {
                noCrawl = "true";
            }
            GetListsResponse.SharePointList sharePointList = new GetListsResponse.SharePointList(idObj.toString(), titleObj.toString(), Boolean.valueOf(noCrawl.toString()));
            sharePointLists.add(sharePointList);
        });

        return new GetListsResponse(sharePointLists);
    }
}

