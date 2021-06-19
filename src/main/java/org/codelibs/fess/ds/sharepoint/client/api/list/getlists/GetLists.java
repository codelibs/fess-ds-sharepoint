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

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GetLists extends SharePointApi<GetListsResponse> {
    private static final String API_PATH = "_api/lists";

    public GetLists(CloseableHttpClient client, String siteUrl, OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    @Override
    public GetListsResponse execute() {
        final HttpGet httpGet = new HttpGet(siteUrl + "/" + API_PATH);
        JsonResponse jsonResponse = doJsonRequest(httpGet);
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
            Object entityTypeName = value.get("EntityTypeName");
            if (entityTypeName == null) {
                return;
            }
            Object noCrawl = value.get("NoCrawl");
            if (noCrawl == null) {
                noCrawl = "false";
            }
            Object hidden = value.get("Hidden");
            if (hidden == null) {
                hidden = "false";
            }
            GetListsResponse.SharePointList sharePointList = new GetListsResponse.SharePointList(idObj.toString(), titleObj.toString(), Boolean.parseBoolean(noCrawl.toString()), Boolean.parseBoolean(hidden.toString()), entityTypeName.toString());
            sharePointLists.add(sharePointList);
        });

        return new GetListsResponse(sharePointLists);
    }
}

