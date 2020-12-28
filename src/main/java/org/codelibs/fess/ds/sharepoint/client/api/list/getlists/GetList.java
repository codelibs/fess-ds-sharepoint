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
package org.codelibs.fess.ds.sharepoint.client.api.list.getlists;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class GetList extends SharePointApi<GetListResponse> {
    private static final String API_BY_LIST_ID_PATH = "_api/web/lists(guid'{list_guid}')";
    private static final String API_BY_LIST_NAME_PATH = "_api/web/lists/GetByTitle('{list_name}')";


    protected String listId = null;
    protected String listName = null;

    public GetList(CloseableHttpClient client, String siteUrl) {
        super(client, siteUrl);
    }

    public GetList setListId(String listId) {
        this.listId = listId;
        return this;
    }

    public GetList setListName(String listName) {
        this.listName = listName;
        return this;
    }

    @Override
    public GetListResponse execute() {
        final String apiPath;
        if (StringUtils.isNotBlank(listId)) {
            apiPath = API_BY_LIST_ID_PATH.replace("{list_guid}", listId);
        } else if (StringUtils.isNotBlank(listName)) {
            apiPath = API_BY_LIST_NAME_PATH.replace("{list_name}", URLEncoder.encode(listName, StandardCharsets.UTF_8).replace("+", "%20"));
        } else {
            throw new SharePointClientException("[GetList] listId/listName is required.");
        }

        final HttpGet httpGet = new HttpGet(siteUrl + "/" + apiPath);
        JsonResponse jsonResponse = doJsonRequest(httpGet);
        return buildResponse(jsonResponse);
    }

    private GetListResponse buildResponse(final JsonResponse jsonResponse) {
        final Map<String, Object> jsonMap = jsonResponse.getBodyAsMap();

        Object titleObj = jsonMap.get("Title");
        if (titleObj == null) {
            throw new SharePointClientException("Title is null.");
        }
        Object idObj = jsonMap.get("Id");
        if (idObj == null) {
            throw new SharePointClientException("Id is null.");
        }
        Object noCrawl = jsonMap.get("NoCrawl");
        if (noCrawl == null) {
            noCrawl = "true";
        }
        GetListsResponse.SharePointList sharePointList = new GetListsResponse.SharePointList(idObj.toString(), titleObj.toString(), Boolean.valueOf(noCrawl.toString()));
        return new GetListResponse(sharePointList);

    }
}
