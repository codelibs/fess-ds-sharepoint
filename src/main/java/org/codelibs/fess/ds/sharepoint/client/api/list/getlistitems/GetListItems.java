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
package org.codelibs.fess.ds.sharepoint.client.api.list.getlistitems;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.core.lang.StringUtil;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;
import org.codelibs.fess.util.DocumentUtil;

/**
 * API class for retrieving list items from SharePoint.
 * This class provides functionality to fetch items from SharePoint lists with pagination support.
 */
public class GetListItems extends SharePointApi<GetListItemsResponse> {
    private static final Logger logger = LogManager.getLogger(GetListItems.class);

    private static final String API_PATH = "_api/Web/Lists(guid'{{id}}')/Items";
    private static final String PAGING_PARAM = "%24top={{num}}&%24skiptoken=Paged=TRUE%26p_ID={{start}}";
    private static final String SELECT_PARAM = "%24select=Title,Id,Attachments,Created,Modified";
    private static final String SELECT_PARAM_SITE_PAGE = "%24select=Id,Created,Modified";

    private String listId = null;
    private final String listName = null;
    private int num = 100;
    private int start = 0;
    private boolean isSubPage = false;

    /**
     * Constructs a new GetListItems instance.
     *
     * @param client the HTTP client for making requests
     * @param siteUrl the SharePoint site URL
     * @param oAuth the OAuth authentication handler
     */
    public GetListItems(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    /**
     * Executes the request to retrieve list items from SharePoint.
     *
     * @return the response containing the list items
     * @throws SharePointClientException if the listId is not set or if the request fails
     */
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

        final String buildUrl = siteUrl + "/" + API_PATH.replace("{{id}}", listId) + "?" + pagingParam + "&" + selectParam;
        if (logger.isDebugEnabled()) {
            logger.debug("buildUrl: {}", buildUrl);
        }
        final HttpGet httpGet = new HttpGet(buildUrl);
        final JsonResponse jsonResponse = doJsonRequest(httpGet);
        return buildResponse(jsonResponse);
    }

    /**
     * Sets the list ID for the SharePoint list to query.
     *
     * @param listId the unique identifier of the SharePoint list
     * @return this instance for method chaining
     */
    public GetListItems setListId(final String listId) {
        this.listId = listId;
        return this;
    }

    /**
     * Sets the maximum number of items to retrieve per request.
     *
     * @param num the maximum number of items (default is 100)
     * @return this instance for method chaining
     */
    public GetListItems setNum(final int num) {
        this.num = num;
        return this;
    }

    /**
     * Sets the item-ID cursor the {@code $skiptoken=Paged=TRUE&amp;p_ID=...} paging token resumes
     * from: SharePoint returns only items whose ID is greater than this value. This is not an
     * offset into the list - passing an offset instead of the highest item ID already read
     * silently re-reads whatever items happen to have that item ID or the next ones after it.
     *
     * @param start the item ID to resume paging after
     * @return this instance for method chaining
     */
    public GetListItems setStart(final int start) {
        this.start = start;
        return this;
    }

    /**
     * Sets whether this request is for a sub-page, which affects the field selection.
     *
     * @param subPage true if this is a sub-page request, false otherwise
     * @return this instance for method chaining
     */
    public GetListItems setSubPage(final boolean subPage) {
        isSubPage = subPage;
        return this;
    }

    /**
     * Builds the response object from the JSON response received from SharePoint.
     *
     * @param jsonResponse the JSON response from the SharePoint API
     * @return the parsed response containing list items
     */
    @SuppressWarnings("unchecked")
    private GetListItemsResponse buildResponse(final JsonResponse jsonResponse) {
        final Map<String, Object> jsonMap = jsonResponse.getBodyAsMap();
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        final List<GetListItemsResponse.ListItem> listItems = new ArrayList<>();
        final List<Map<String, Object>> valueList = (List<Map<String, Object>>) jsonMap.get("value");
        valueList.forEach(value -> {
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
            // A date that is absent or does not match this format used to drop the whole item
            // (if absent) or throw and abort every remaining item on this page (if malformed).
            // Neither should cost more than the date itself.
            final Date created = parseDate(sdf, DocumentUtil.getValue(value, "Created", String.class));
            final Date modified = parseDate(sdf, DocumentUtil.getValue(value, "Modified", String.class));

            final GetListItemsResponse.ListItem listItem =
                    new GetListItemsResponse.ListItem(id, editLink, title, attachments, created, modified);
            listItems.add(listItem);
        });

        return new GetListItemsResponse(listItems);
    }

    /**
     * Parses an ISO 8601 date, tolerating an absent or unparseable value.
     *
     * @param sdf the date format to parse with
     * @param value the date text, may be null
     * @return the parsed date, or null if {@code value} is null or does not match the format
     */
    private Date parseDate(final SimpleDateFormat sdf, final String value) {
        if (value == null) {
            return null;
        }
        try {
            return sdf.parse(value);
        } catch (final ParseException e) {
            logger.warn("Failed to parse date: {}", value, e);
            return null;
        }
    }
}
