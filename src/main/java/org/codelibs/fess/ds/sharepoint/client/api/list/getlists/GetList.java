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

/**
 * API class for retrieving a single SharePoint list.
 * This class handles REST API calls to get detailed information about a specific SharePoint list.
 */
public class GetList extends SharePointApi<GetListResponse> {
    private static final Logger logger = LogManager.getLogger(GetList.class);

    private static final String API_BY_LIST_ID_PATH = "_api/web/lists(guid'{list_guid}')";
    private static final String API_BY_LIST_NAME_PATH = "_api/web/lists/GetByTitle('{list_name}')";

    /** The GUID of the SharePoint list to retrieve. */
    protected String listId = null;
    /** The name of the SharePoint list to retrieve. */
    protected String listName = null;

    /**
     * Constructs a new GetList instance.
     *
     * @param client the HTTP client for making requests
     * @param siteUrl the SharePoint site URL
     * @param oAuth the OAuth authentication object
     */
    public GetList(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    /**
     * Sets the list ID for the list to retrieve.
     *
     * @param listId the GUID of the SharePoint list
     * @return this GetList instance for method chaining
     */
    public GetList setListId(final String listId) {
        this.listId = listId;
        return this;
    }

    /**
     * Sets the list name for the list to retrieve.
     *
     * @param listName the name of the SharePoint list
     * @return this GetList instance for method chaining
     */
    public GetList setListName(final String listName) {
        this.listName = listName;
        return this;
    }

    @Override
    public GetListResponse execute() {
        final String apiPath;
        if (StringUtils.isNotBlank(listId)) {
            apiPath = API_BY_LIST_ID_PATH.replace("{list_guid}", requireGuidLiteral(listId, "listId"));
        } else if (StringUtils.isNotBlank(listName)) {
            apiPath = API_BY_LIST_NAME_PATH.replace("{list_name}",
                    URLEncoder.encode(escapeODataLiteral(listName), StandardCharsets.UTF_8).replace("+", "%20"));
        } else {
            throw new SharePointClientException("[GetList] listId/listName is required.");
        }

        final String buildUrl = siteUrl + "/" + apiPath;
        if (logger.isDebugEnabled()) {
            logger.debug("buildUrl: {}", buildUrl);
        }
        final HttpGet httpGet = new HttpGet(buildUrl);
        final JsonResponse jsonResponse = doJsonRequest(httpGet);
        return buildResponse(jsonResponse, buildUrl);
    }

    private GetListResponse buildResponse(final JsonResponse jsonResponse, final String requestUrl) {
        final Map<String, Object> jsonMap = jsonResponse.getBodyAsMap();

        final String title = DocumentUtil.getValue(jsonMap, "Title", String.class);
        if (title == null) {
            throw missingField("Title", requestUrl);
        }
        final String id = DocumentUtil.getValue(jsonMap, "Id", String.class);
        if (id == null) {
            throw missingField("Id", requestUrl);
        }
        final String entityTypeName = DocumentUtil.getValue(jsonMap, "EntityTypeName", String.class);
        if (entityTypeName == null) {
            throw missingField("EntityTypeName", requestUrl);
        }
        final boolean noCrawl = DocumentUtil.getValue(jsonMap, "NoCrawl", Boolean.class, Boolean.FALSE);
        final boolean hidden = DocumentUtil.getValue(jsonMap, "Hidden", Boolean.class, Boolean.FALSE);
        final GetListsResponse.SharePointList sharePointList =
                new GetListsResponse.SharePointList(id, title, noCrawl, hidden, entityTypeName);
        return new GetListResponse(sharePointList);
    }

    /**
     * Builds the exception for a response that does not describe a list.
     *
     * <p>A 200 response without a title is not a list at all - typically the url points at
     * something other than a SharePoint site, or a proxy answered in its place - so the message
     * names the list and the request instead of only the missing field.
     *
     * @param field the field the response lacks
     * @param requestUrl the URL that was requested
     * @return the exception to throw
     */
    protected SharePointClientException missingField(final String field, final String requestUrl) {
        return new SharePointClientException("[GetList] The response for list '" + (StringUtils.isNotBlank(listId) ? listId : listName)
                + "' has no " + field + ", so it is not a SharePoint list. Check the url, site.name and site.list_name/site.list_id"
                + " parameters. Requested: " + requestUrl);
    }
}
