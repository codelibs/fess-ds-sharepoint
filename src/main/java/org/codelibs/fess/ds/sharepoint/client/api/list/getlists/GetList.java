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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;
import org.codelibs.fess.util.DocumentUtil;

public class GetList extends SharePointApi<GetListResponse> {
    private static final Logger logger = LogManager.getLogger(GetList.class);

    private static final String API_BY_LIST_ID_PATH = "_api/web/lists(guid'{list_guid}')";
    private static final String API_BY_LIST_NAME_PATH = "_api/web/lists/GetByTitle('{list_name}')";

    protected String listId = null;
    protected String listName = null;

    public GetList(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    public GetList setListId(final String listId) {
        this.listId = listId;
        return this;
    }

    public GetList setListName(final String listName) {
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

        final String buildUrl = siteUrl + "/" + apiPath;
        if (logger.isDebugEnabled()) {
            logger.debug("buildUrl: {}", buildUrl);
        }
        final HttpGet httpGet = new HttpGet(buildUrl);
        final JsonResponse jsonResponse = doJsonRequest(httpGet);
        return buildResponse(jsonResponse);
    }

    private GetListResponse buildResponse(final JsonResponse jsonResponse) {
        final Map<String, Object> jsonMap = jsonResponse.getBodyAsMap();

        final String title = DocumentUtil.getValue(jsonMap, "Title", String.class);
        if (title == null) {
            throw new SharePointClientException("Title is null.");
        }
        final String id = DocumentUtil.getValue(jsonMap, "Id", String.class);
        if (id == null) {
            throw new SharePointClientException("Id is null.");
        }
        final String entityTypeName = DocumentUtil.getValue(jsonMap, "EntityTypeName", String.class);
        if (entityTypeName == null) {
            throw new SharePointClientException("entityTypeName is null.");
        }
        final boolean noCrawl = DocumentUtil.getValue(jsonMap, "NoCrawl", Boolean.class, Boolean.FALSE);
        final boolean hidden = DocumentUtil.getValue(jsonMap, "Hidden", Boolean.class, Boolean.FALSE);
        final GetListsResponse.SharePointList sharePointList =
                new GetListsResponse.SharePointList(id, title, noCrawl, hidden, entityTypeName);
        return new GetListResponse(sharePointList);
    }
}
