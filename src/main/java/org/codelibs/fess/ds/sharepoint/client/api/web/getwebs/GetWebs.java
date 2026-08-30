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
package org.codelibs.fess.ds.sharepoint.client.api.web.getwebs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;
import org.codelibs.fess.util.DocumentUtil;

/**
 * API class for retrieving a SharePoint site's direct child sites (subsites).
 *
 * <p>{@code _api/web/webinfos} returns <b>every</b> child site, regardless of whether the calling
 * account has permission to read it - unlike most of this plugin's other listing endpoints, it is
 * not security-trimmed. A caller that cannot read a listed child site gets a 403 when it goes on
 * to crawl that child, not an empty listing here. {@code SiteCrawl#doCrawl} catches that 403 on
 * the child's own crawl and skips the subsite with a warning instead of counting it as a crawl
 * failure; {@code SiteCrawl#crawlSubsites} does the same for a 403 on this listing itself.
 */
public class GetWebs extends SharePointApi<GetWebsResponse> {
    private static final Logger logger = LogManager.getLogger(GetWebs.class);

    private static final String API_PATH = "_api/web/webinfos";

    /**
     * Upper bound on the number of pages this request may follow via {@code odata.nextLink}. See
     * {@code GetLists#execute()} for the reasoning, applied here to a site's child sites.
     */
    private static final int MAX_PAGES = 100;

    /**
     * Constructor.
     *
     * @param client HTTP client
     * @param siteUrl SharePoint site URL
     * @param oAuth OAuth authentication
     */
    public GetWebs(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    @Override
    public GetWebsResponse execute() {
        final List<GetWebsResponse.SubSite> subSites = new ArrayList<>();
        String url = siteUrl + "/" + API_PATH;
        for (int page = 0; page < MAX_PAGES; page++) {
            if (logger.isDebugEnabled()) {
                logger.debug("buildUrl: {}", url);
            }
            final HttpGet httpGet = new HttpGet(url);
            final JsonResponse jsonResponse = doJsonRequest(httpGet);
            final Map<String, Object> jsonMap = jsonResponse.getBodyAsMap();
            appendSubSites(jsonMap, subSites);
            final String nextLink = DocumentUtil.getValue(jsonMap, "odata.nextLink", String.class);
            if (nextLink == null) {
                return new GetWebsResponse(subSites);
            }
            url = nextLink;
            if (page == MAX_PAGES - 1) {
                logger.warn("Stopped listing {} after {} pages; the listing may be truncated.", API_PATH, MAX_PAGES);
            }
        }
        return new GetWebsResponse(subSites);
    }

    /**
     * Extracts child sites from one page of parsed JSON data and appends them to the given
     * accumulator.
     *
     * @param jsonMap the parsed JSON body for one page
     * @param subSites the accumulator to append this page's child sites to
     */
    private void appendSubSites(final Map<String, Object> jsonMap, final List<GetWebsResponse.SubSite> subSites) {
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> valueList = (List<Map<String, Object>>) jsonMap.get("value");
        valueList.forEach(value -> {
            // SP.WebInformation has no absolute Url property - ServerRelativeUrl is the only
            // field that identifies where the child site actually is, so an entry without one is
            // useless to the crawl. Blank is rejected along with null: an empty string would
            // otherwise reach SharePointClient.normalizeSitePath, which reinterprets a blank path
            // as the root site's own - turning a malformed entry into a silent, wrong match
            // instead of being dropped here.
            final String serverRelativeUrl = DocumentUtil.getValue(value, "ServerRelativeUrl", String.class);
            if (StringUtils.isBlank(serverRelativeUrl)) {
                return;
            }
            final String id = DocumentUtil.getValue(value, "Id", String.class);
            final String title = DocumentUtil.getValue(value, "Title", String.class);
            subSites.add(new GetWebsResponse.SubSite(id, title, serverRelativeUrl));
        });
    }
}
