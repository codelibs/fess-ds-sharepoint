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
package org.codelibs.fess.ds.sharepoint.client2013.api.list.getlistitems;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.core.lang.StringUtil;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitems.GetListItems;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;
import org.codelibs.fess.util.DocumentUtil;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * SharePoint 2013 specific implementation for retrieving list items.
 * This class extends the base GetListItems functionality with XML-based communication
 * suitable for SharePoint 2013 environments.
 */
public class GetListItems2013 extends GetListItems {
    private static final Logger logger = LogManager.getLogger(GetListItems2013.class);

    private static final String API_PATH = "_api/Web/Lists(guid'{{id}}')/Items";
    private static final String PAGING_PARAM = "%24skiptoken=Paged=TRUE%26p_ID={{start}}&%24top={{num}}";
    private static final String SELECT_PARAM = "%24select=Title,Id,Attachments,Created,Modified";
    private static final String SELECT_PARAM_SITE_PAGE = "%24select=Id,Created,Modified";

    private String listId = null;
    private final String listName = null;
    private int num = 100;
    private int start = 0;
    private boolean isSubPage = false;

    /**
     * Constructs a new GetListItems2013 instance.
     *
     * @param client the HTTP client for making requests
     * @param siteUrl the SharePoint site URL
     * @param oAuth the OAuth authentication handler
     */
    public GetListItems2013(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    /**
     * Executes the request to retrieve list items from SharePoint 2013.
     * Uses XML-based communication instead of JSON.
     *
     * @return the response containing the list items
     * @throws SharePointClientException if the listId is not set or if the request fails
     */
    @Override
    public GetListItems2013Response execute() {
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
        final XmlResponse xmlResponse = doXmlRequest(httpGet);
        return buildResponse(xmlResponse);
    }

    /**
     * Sets the list ID for the SharePoint list to query.
     *
     * @param listId the unique identifier of the SharePoint list
     * @return this instance for method chaining
     */
    @Override
    public GetListItems2013 setListId(final String listId) {
        this.listId = listId;
        return this;
    }

    /**
     * Sets the maximum number of items to retrieve per request.
     *
     * @param num the maximum number of items (default is 100)
     * @return this instance for method chaining
     */
    @Override
    public GetListItems2013 setNum(final int num) {
        this.num = num;
        return this;
    }

    /**
     * Sets the starting index for pagination.
     *
     * @param start the starting index for retrieving items
     * @return this instance for method chaining
     */
    @Override
    public GetListItems2013 setStart(final int start) {
        this.start = start;
        return this;
    }

    /**
     * Sets whether this request is for a sub-page, which affects the field selection.
     *
     * @param subPage true if this is a sub-page request, false otherwise
     * @return this instance for method chaining
     */
    @Override
    public GetListItems2013 setSubPage(final boolean subPage) {
        isSubPage = subPage;
        return this;
    }

    /**
     * Builds the response object from the XML response received from SharePoint 2013.
     *
     * @param xmlResponse the XML response from the SharePoint API
     * @return the parsed response containing list items
     * @throws SharePointClientException if parsing fails
     */
    private GetListItems2013Response buildResponse(final XmlResponse xmlResponse) {
        final GetListItemsDocHandler handler = new GetListItemsDocHandler();
        xmlResponse.parseXml(handler);
        final Map<String, Object> dataMap = handler.getDataMap();
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

        final List<GetListItems2013Response.ListItem> listItems = new ArrayList<>();
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> valueList = (List<Map<String, Object>>) dataMap.get("value");
        valueList.forEach(value -> {
            try {
                final String title = DocumentUtil.getValue(value, "Title", String.class, StringUtil.EMPTY);
                final String id = DocumentUtil.getValue(value, "Id", String.class);
                if (id == null) {
                    logger.warn("Id field does not contain. Skip item. {}", xmlResponse.getBody());
                    return;
                }
                final String editLink = DocumentUtil.getValue(value, "odata.editLink", String.class);
                if (editLink == null) {
                    logger.warn("odate.editLink field does not contain. Skip item. {}", xmlResponse.getBody());
                    return;
                }
                final boolean attachments = DocumentUtil.getValue(value, "Attachments", Boolean.class, Boolean.FALSE);
                final String createdObj = DocumentUtil.getValue(value, "Created", String.class);
                if (createdObj == null) {
                    logger.warn("Created field does not contain. Skip item. {}", xmlResponse.getBody());
                    return;
                }
                final Date created = sdf.parse(createdObj);
                final String modifiedObj = DocumentUtil.getValue(value, "Modified", String.class);
                if (modifiedObj == null) {
                    logger.warn("Modified field does not contain. Skip item. {}", xmlResponse.getBody());
                    return;
                }
                final Date modified = sdf.parse(modifiedObj);

                final GetListItems2013Response.ListItem listItem =
                        new GetListItems2013Response.ListItem(id, editLink, title, attachments, created, modified);
                listItems.add(listItem);
            } catch (final ParseException e) {
                throw new SharePointClientException("Failed to get item info.", e);
            }
        });

        return new GetListItems2013Response(listItems);
    }

    /**
     * SAX document handler for parsing SharePoint 2013 XML responses.
     * Extracts list item data from the XML structure.
     */
    private static class GetListItemsDocHandler extends DefaultHandler {
        private final Map<String, Object> dataMap = new HashMap<>();
        private Map<String, Object> resultMap = null;

        private String fieldName;

        private final StringBuilder buffer = new StringBuilder(1000);

        /**
         * Initializes the data map when starting document parsing.
         */
        @Override
        public void startDocument() {
            dataMap.clear();
            dataMap.put("value", new ArrayList<>());
        }

        /**
         * Handles the start of XML elements during parsing.
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
            } else if ("d:Id".equals(qName)) {
                fieldName = "Id";
                buffer.setLength(0);
            } else if ("d:Title".equals(qName)) {
                fieldName = "Title";
                buffer.setLength(0);
            } else if ("d:Attachments".equals(qName)) {
                fieldName = "Attachments";
                buffer.setLength(0);
            } else if ("d:Created".equals(qName)) {
                fieldName = "Created";
                buffer.setLength(0);
            } else if ("d:Modified".equals(qName)) {
                fieldName = "Modified";
                buffer.setLength(0);
            } else if ("link".equals(qName)) {
                final String rel = attributes.getValue("rel");
                if ("edit".equals(rel)) {
                    resultMap.put("odata.editLink", attributes.getValue("href"));
                }
            }
        }

        /**
         * Handles character data within XML elements.
         *
         * @param ch the character array
         * @param offset the start offset in the array
         * @param length the number of characters to read
         */
        @Override
        public void characters(final char[] ch, final int offset, final int length) {
            if (fieldName != null) {
                buffer.append(new String(ch, offset, length));
            }
        }

        /**
         * Handles the end of XML elements during parsing.
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
         * Returns the parsed data map containing list items.
         *
         * @return the data map with parsed content
         */
        public Map<String, Object> getDataMap() {
            return dataMap;
        }
    }
}
