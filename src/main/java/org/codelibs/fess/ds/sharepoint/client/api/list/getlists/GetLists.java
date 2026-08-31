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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;
import org.codelibs.fess.util.DocumentUtil;

/**
 * API class for retrieving SharePoint lists.
 */
public class GetLists extends SharePointApi<GetListsResponse> {
    private static final Logger logger = LogManager.getLogger(GetLists.class);

    private static final String API_PATH = "_api/lists";

    /**
     * Upper bound on the number of pages this request may follow via {@code odata.nextLink}.
     *
     * <p>The loop ends when a response carries no {@code odata.nextLink}. A server that kept
     * returning a link back to itself would otherwise loop forever, so this bound turns that into
     * a truncated listing with a warning instead - the same hazard {@link org.codelibs.fess.ds.sharepoint.crawl.doclib.FolderCrawl}
     * guards against for folder and file listings, applied here to a site's lists. At the
     * server's own default page size this allows several thousand lists, far more than a real
     * site ever defines.
     */
    private static final int MAX_PAGES = 100;

    /**
     * Constructor.
     *
     * @param client HTTP client
     * @param siteUrl SharePoint site URL
     * @param oAuth OAuth authentication
     */
    public GetLists(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    @Override
    public GetListsResponse execute() {
        final List<GetListsResponse.SharePointList> sharePointLists = new ArrayList<>();
        String url = siteUrl + "/" + API_PATH;
        for (int page = 0; page < MAX_PAGES; page++) {
            if (logger.isDebugEnabled()) {
                logger.debug("buildUrl: {}", url);
            }
            final HttpGet httpGet = new HttpGet(url);
            final JsonResponse jsonResponse = doJsonRequest(httpGet);
            final Map<String, Object> jsonMap = jsonResponse.getBodyAsMap();
            appendLists(jsonMap, sharePointLists);
            final String nextLink = DocumentUtil.getValue(jsonMap, "odata.nextLink", String.class);
            if (nextLink == null) {
                return new GetListsResponse(sharePointLists);
            }
            url = nextLink;
            if (page == MAX_PAGES - 1) {
                logger.warn("Stopped listing {} after {} pages; the listing may be truncated.", API_PATH, MAX_PAGES);
            }
        }
        return new GetListsResponse(sharePointLists);
    }

    private void appendLists(final Map<String, Object> jsonMap, final List<GetListsResponse.SharePointList> sharePointLists) {
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
    }
}
