/*
 * Copyright 2012-2022 CodeLibs Project and the Others.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;
import org.codelibs.fess.util.DocumentUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetLists extends SharePointApi<GetListsResponse> {
    private static final Logger logger = LoggerFactory.getLogger(GetLists.class);

    private static final String API_PATH = "_api/lists";

    public GetLists(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    @Override
    public GetListsResponse execute() {
        final String buildUrl = siteUrl + "/" + API_PATH;
        if (logger.isDebugEnabled()) {
            logger.debug("buildUrl: {}", buildUrl);
        }
        final HttpGet httpGet = new HttpGet(buildUrl);
        final JsonResponse jsonResponse = doJsonRequest(httpGet);
        return buildResponse(jsonResponse);
    }

    private GetListsResponse buildResponse(final JsonResponse jsonResponse) {
        final Map<String, Object> jsonMap = jsonResponse.getBodyAsMap();

        final List<GetListsResponse.SharePointList> sharePointLists = new ArrayList<>();
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> valueList = (List<Map<String, Object>>) jsonMap.get("value");
        valueList.forEach(value -> {
            final String title = DocumentUtil.getValue(value, "Title", String.class);
            if (title == null) {
                return;
            }
            final String id = DocumentUtil.getValue(value, "Id", String.class);
            if (id == null) {
                return;
            }
            final String entityTypeName = DocumentUtil.getValue(value, "EntityTypeName", String.class);
            if (entityTypeName == null) {
                return;
            }
            final boolean noCrawl = DocumentUtil.getValue(value, "NoCrawl", Boolean.class, Boolean.FALSE);
            final boolean hidden = DocumentUtil.getValue(value, "Hidden", Boolean.class, Boolean.FALSE);
            final GetListsResponse.SharePointList sharePointList =
                    new GetListsResponse.SharePointList(id, title, noCrawl, hidden, entityTypeName);
            sharePointLists.add(sharePointList);
        });

        return new GetListsResponse(sharePointLists);
    }
}
