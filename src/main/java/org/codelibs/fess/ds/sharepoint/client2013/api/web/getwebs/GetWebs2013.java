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
package org.codelibs.fess.ds.sharepoint.client2013.api.web.getwebs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fess.ds.sharepoint.client.api.web.getwebs.GetWebs;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;
import org.codelibs.fess.util.DocumentUtil;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * SharePoint 2013 implementation for retrieving a site's direct child sites.
 * This class extends GetWebs to provide XML parsing capabilities
 * specific to SharePoint 2013's REST API response format.
 *
 * <p>Uses SAX XML parsing to extract child site metadata from SharePoint 2013
 * responses. The handler is deliberately flat - it recognizes {@code entry},
 * {@code d:Title}, {@code d:Id} and {@code d:ServerRelativeUrl} wherever they occur, with no
 * element-nesting stack - because no navigation property is expanded here, so no nested
 * occurrence of those element names appears in the feed.
 *
 * @see GetWebs
 * @see GetWebs2013Response
 */
public class GetWebs2013 extends GetWebs {
    private static final Logger logger = LogManager.getLogger(GetWebs2013.class);

    /** SharePoint 2013 API endpoint path for a site's child sites */
    private static final String API_PATH = "_api/web/webinfos";

    /**
     * Upper bound on the number of pages this request may follow via the Atom feed's
     * {@code rel="next"} link. See {@code GetLists2013#execute()} for the reasoning; the
     * SharePoint 2013 feed exposes the same paging hazard through XML instead of
     * {@code odata.nextLink}.
     */
    private static final int MAX_PAGES = 100;

    /**
     * Constructs a new GetWebs2013 instance.
     *
     * @param client HTTP client for making API requests
     * @param siteUrl SharePoint site URL
     * @param oAuth OAuth authentication handler
     */
    public GetWebs2013(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    /**
     * Executes the SharePoint 2013 child sites retrieval request.
     *
     * @return GetWebs2013Response containing the parsed child sites
     */
    @Override
    public GetWebs2013Response execute() {
        final List<GetWebs2013Response.SubSite> subSites = new ArrayList<>();
        String url = siteUrl + "/" + API_PATH;
        for (int page = 0; page < MAX_PAGES; page++) {
            final HttpGet httpGet = new HttpGet(url);
            final XmlResponse xmlResponse = doXmlRequest(httpGet);
            final GetWebsDocHandler handler = new GetWebsDocHandler();
            xmlResponse.parseXml(handler);
            final Map<String, Object> dataMap = handler.getDataMap();
            appendSubSites(dataMap, subSites);
            final String nextLink = DocumentUtil.getValue(dataMap, "nextLink", String.class);
            if (nextLink == null) {
                return new GetWebs2013Response(subSites);
            }
            url = nextLink;
            if (page == MAX_PAGES - 1) {
                logger.warn("Stopped listing {} after {} pages; the listing may be truncated.", API_PATH, MAX_PAGES);
            }
        }
        return new GetWebs2013Response(subSites);
    }

    /**
     * Extracts child site information from one page of parsed XML data and appends it to the
     * given accumulator.
     *
     * @param dataMap the data map for one page, parsed from SharePoint 2013 XML
     * @param subSites the accumulator to append this page's child sites to
     */
    private void appendSubSites(final Map<String, Object> dataMap, final List<GetWebs2013Response.SubSite> subSites) {
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> valueList = (List<Map<String, Object>>) dataMap.get("value");
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
            subSites.add(new GetWebs2013Response.SubSite(id, title, serverRelativeUrl));
        });
    }

    /**
     * SAX handler for parsing SharePoint 2013 XML responses containing child site data.
     * This handler extracts child site properties from the XML structure.
     */
    private static class GetWebsDocHandler extends DefaultHandler {
        /** Map to store all parsed child site data */
        private final Map<String, Object> dataMap = new HashMap<>();
        /** Current child site being parsed */
        private Map<String, Object> resultMap = null;

        /** Current field name being processed */
        private String fieldName;

        /** Buffer for accumulating character data */
        private final StringBuilder buffer = new StringBuilder(1000);

        /**
         * Called at the start of document parsing to initialize data structures.
         */
        @Override
        public void startDocument() {
            dataMap.clear();
            dataMap.put("value", new ArrayList<>());
        }

        /**
         * Called when an XML element starts, identifies child site properties to extract.
         *
         * @param uri the namespace URI
         * @param localName the local name
         * @param qName the qualified name
         * @param attributes the element attributes
         */
        @Override
        public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) {
            if ("entry".equals(qName)) {
                resultMap = new HashMap<>();
            } else if ("d:Title".equals(qName)) {
                fieldName = "Title";
                buffer.setLength(0);
            } else if ("d:Id".equals(qName)) {
                fieldName = "Id";
                buffer.setLength(0);
            } else if ("d:ServerRelativeUrl".equals(qName)) {
                fieldName = "ServerRelativeUrl";
                buffer.setLength(0);
            } else if ("link".equals(qName) && "next".equals(attributes.getValue("rel"))) {
                // The Atom feed's own paging link, present only when the server has more entries
                // than fit on this page. Absent from the leaf <entry> elements' own <link
                // rel="edit"> elements, so this always refers to the feed-level next page.
                dataMap.put("nextLink", attributes.getValue("href"));
            }
        }

        /**
         * Accumulates character data for the current field being processed.
         *
         * @param ch the character array
         * @param offset the start offset
         * @param length the number of characters to use
         */
        @Override
        public void characters(final char[] ch, final int offset, final int length) {
            if (fieldName != null) {
                buffer.append(new String(ch, offset, length));
            }
        }

        /**
         * Called when an XML element ends, stores the accumulated field value.
         *
         * @param uri the namespace URI
         * @param localName the local name
         * @param qName the qualified name
         */
        @Override
        @SuppressWarnings("unchecked")
        public void endElement(final String uri, final String localName, final String qName) {
            if ("entry".equals(qName)) {
                if (resultMap != null) {
                    ((List) dataMap.get("value")).add(resultMap);
                }
                resultMap = null;
            } else if (resultMap != null && fieldName != null) {
                if (!resultMap.containsKey(fieldName)) {
                    resultMap.put(fieldName, buffer.toString());
                }
                fieldName = null;
            }
        }

        /**
         * Called when document parsing is complete.
         */
        @Override
        public void endDocument() {
            // nothing
        }

        /**
         * Gets the map containing all parsed child site data.
         *
         * @return the data map with child site information
         */
        public Map<String, Object> getDataMap() {
            return dataMap;
        }
    }
}
