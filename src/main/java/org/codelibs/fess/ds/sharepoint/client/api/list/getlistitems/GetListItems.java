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

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class GetListItems extends SharePointApi<GetListItemsResponse> {
    private static final Logger logger = LoggerFactory.getLogger(GetListItems.class);
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private static final String API_PATH = "_api/Web/Lists(guid'{{id}}')/Items";
    private static final String PAGING_PARAM = "%24top={{num}}&%24skiptoken=Paged=TRUE%26p_ID={{start}}";
    private static final String SELECT_PARAM = "%24select=Title,Id,Attachments,Created,Modified";

    private String listId = null;
    private String listName = null;
    private int num = 100;
    private int start = 0;

    public GetListItems(CloseableHttpClient client, String siteUrl) {
        super(client, siteUrl);
    }

    @Override
    public GetListItemsResponse execute() {
        if (listId == null && listName == null) {
            throw new SharePointClientException("ListID|ListName is required.");
        }
        final String pagingParam = PAGING_PARAM.replace("{{num}}", String.valueOf(num)).replace("{{start}}", String.valueOf(start));
        final HttpGet httpGet = new HttpGet(siteUrl + "/" + API_PATH.replace("{{id}}", listId) + "?" + pagingParam + "&" + SELECT_PARAM);
        JsonResponse jsonResponse = doJsonRequest(httpGet);
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

    @SuppressWarnings("unchecked")
    private GetListItemsResponse buildResponse(final JsonResponse jsonResponse) {
        final Map<String, Object> jsonMap = jsonResponse.getBodyAsMap();

        final List<GetListItemsResponse.ListItem> listItems = new ArrayList<>();
        final List<Map<String, Object>> valueList = (List)jsonMap.get("value");
        valueList.forEach(value -> {
            try {
                Object titleObj = value.get("Title");
                if (titleObj == null) {
                    logger.warn("Title field does not contain. Skip item. " + jsonResponse.getBody());
                    return;
                }
                Object idObj = value.get("Id");
                if (idObj == null) {
                    logger.warn("Id field does not contain. Skip item. " + jsonResponse.getBody());
                    return;
                }
                Object editLinkObj = value.get("odata.editLink");
                if (editLinkObj == null) {
                    logger.warn("odate.editLink field does not contain. Skip item. " + jsonResponse.getBody());
                    return;
                }
                Object attachmentsObj = value.get("Attachments");
                if (attachmentsObj == null) {
                    logger.warn("Attachments field does not contain. Skip item. " + jsonResponse.getBody());
                    return;
                }
                boolean attachments = Boolean.valueOf(attachmentsObj.toString());
                Object createdObj = value.get("Created");
                if (createdObj == null) {
                    logger.warn("Created field does not contain. Skip item. " + jsonResponse.getBody());
                    return;
                }
                Date created = sdf.parse(createdObj.toString());
                Object modifiedObj = value.get("Modified");
                if (modifiedObj == null) {
                    logger.warn("Modified field does not contain. Skip item. " + jsonResponse.getBody());
                    return;
                }
                Date modified = sdf.parse(modifiedObj.toString());

                GetListItemsResponse.ListItem listItem = new GetListItemsResponse.ListItem(idObj.toString(),
                        editLinkObj.toString(),
                        titleObj.toString(),
                        attachments,
                        created,
                        modified
                );
                listItems.add(listItem);
            } catch (ParseException e) {
                throw new SharePointClientException("Failed to get item info.", e);
            }
        });

        return new GetListItemsResponse(listItems);
    }
}
