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
package org.codelibs.fess.ds.sharepoint.client.api.list.getlistitems;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.core.lang.StringUtil;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;
import org.codelibs.fess.util.DocumentUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetListItems extends SharePointApi<GetListItemsResponse> {
    private static final Logger logger = LoggerFactory.getLogger(GetListItems.class);

    private static final String API_PATH = "_api/Web/Lists(guid'{{id}}')/Items";
    private static final String PAGING_PARAM = "%24top={{num}}&%24skiptoken=Paged=TRUE%26p_ID={{start}}";
    private static final String SELECT_PARAM = "%24select=Title,Id,Attachments,Created,Modified";
    private static final String SELECT_PARAM_SITE_PAGE = "%24select=Id,Created,Modified";

    private String listId = null;
    private final String listName = null;
    private int num = 100;
    private int start = 0;
    private boolean isSubPage = false;

    public GetListItems(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    @Override
    public GetListItemsResponse execute() {
        if (listId == null && listName == null) {
            throw new SharePointClientException("ListID|ListName is required.");
        }
        final String pagingParam = PAGING_PARAM.replace("{{num}}", String.valueOf(num)).replace("{{start}}", String.valueOf(start));
        final String selectParam;
        if (isSubPage) {
            selectParam = SELECT_PARAM_SITE_PAGE;
        } else {
            selectParam = SELECT_PARAM;
        }

        final HttpGet httpGet = new HttpGet(siteUrl + "/" + API_PATH.replace("{{id}}", listId) + "?" + pagingParam + "&" + selectParam);
        final JsonResponse jsonResponse = doJsonRequest(httpGet);
        return buildResponse(jsonResponse);
    }

    public GetListItems setListId(final String listId) {
        this.listId = listId;
        return this;
    }

    public GetListItems setNum(final int num) {
        this.num = num;
        return this;
    }

    public GetListItems setStart(final int start) {
        this.start = start;
        return this;
    }

    public GetListItems setSubPage(final boolean subPage) {
        isSubPage = subPage;
        return this;
    }

    @SuppressWarnings("unchecked")
    private GetListItemsResponse buildResponse(final JsonResponse jsonResponse) {
        final Map<String, Object> jsonMap = jsonResponse.getBodyAsMap();
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

        final List<GetListItemsResponse.ListItem> listItems = new ArrayList<>();
        final List<Map<String, Object>> valueList = (List<Map<String, Object>>) jsonMap.get("value");
        valueList.forEach(value -> {
            try {
                final String title = DocumentUtil.getValue(value, "Title", String.class, StringUtil.EMPTY);
                final String id = DocumentUtil.getValue(value, "Id", String.class);
                if (id == null) {
                    logger.warn("Id field does not contain. Skip item. {}", jsonResponse.getBody());
                    return;
                }
                final String editLink = DocumentUtil.getValue(value, "odata.editLink", String.class);
                if (editLink == null) {
                    logger.warn("odate.editLink field does not contain. Skip item. {}", jsonResponse.getBody());
                    return;
                }
                final boolean attachments = DocumentUtil.getValue(value, "Attachments", Boolean.class, Boolean.FALSE);
                final String createdObj = DocumentUtil.getValue(value, "Created", String.class);
                if (createdObj == null) {
                    logger.warn("Created field does not contain. Skip item. {}", jsonResponse.getBody());
                    return;
                }
                final Date created = sdf.parse(createdObj);
                final String modifiedObj = DocumentUtil.getValue(value, "Modified", String.class);
                if (modifiedObj == null) {
                    logger.warn("Modified field does not contain. Skip item. {}", jsonResponse.getBody());
                    return;
                }
                final Date modified = sdf.parse(modifiedObj);

                final GetListItemsResponse.ListItem listItem =
                        new GetListItemsResponse.ListItem(id, editLink, title, attachments, created, modified);
                listItems.add(listItem);
            } catch (final ParseException e) {
                throw new SharePointClientException("Failed to get item info.", e);
            }
        });

        return new GetListItemsResponse(listItems);
    }
}
