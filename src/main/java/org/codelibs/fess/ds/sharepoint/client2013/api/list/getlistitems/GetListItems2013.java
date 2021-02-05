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
package org.codelibs.fess.ds.sharepoint.client2013.api.list.getlistitems;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitems.GetListItems;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class GetListItems2013 extends GetListItems {
    private static final Logger logger = LoggerFactory.getLogger(GetListItems2013.class);
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private static final String API_PATH = "_api/Web/Lists(guid'{{id}}')/Items";
    private static final String PAGING_PARAM = "%24skiptoken=Paged=TRUE%26p_ID={{start}}&%24top={{num}}";
    private static final String SELECT_PARAM = "%24select=Title,Id,Attachments,Created,Modified";
    private static final String SELECT_PARAM_SITE_PAGE = "%24select=Id,Created,Modified";

    private String listId = null;
    private String listName = null;
    private int num = 100;
    private int start = 0;
    private boolean isSubPage = false;

    public GetListItems2013(CloseableHttpClient client, String siteUrl, OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

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
        XmlResponse xmlResponse = doXmlRequest(httpGet);
        return buildResponse(xmlResponse);
    }

    public GetListItems2013 setListId(final String listId) {
        this.listId = listId;
        return this;
    }

    public GetListItems2013 setNum(final int num) {
        this.num = num;
        return this;
    }

    public GetListItems2013 setStart(final int start) {
        this.start = start;
        return this;
    }

    public GetListItems2013 setSubPage(boolean subPage) {
        isSubPage = subPage;
        return this;
    }

    @SuppressWarnings("unchecked")
    private GetListItems2013Response buildResponse(final XmlResponse xmlResponse) {
        final GetListItemsDocHandler handler = new GetListItemsDocHandler();
        xmlResponse.parseXml(handler);
        final Map<String, Object> dataMap = handler.getDataMap();

        final List<GetListItems2013Response.ListItem> listItems = new ArrayList<>();
        final List<Map<String, Object>> valueList = (List)dataMap.get("value");
        valueList.forEach(value -> {
            try {
                Object titleObj = value.get("Title");
                if (titleObj == null) {
                    titleObj = "";
                }
                Object idObj = value.get("Id");
                if (idObj == null) {
                    logger.warn("Id field does not contain. Skip item. " + xmlResponse.getBody());
                    return;
                }
                Object editLinkObj = value.get("odata.editLink");
                if (editLinkObj == null) {
                    logger.warn("odate.editLink field does not contain. Skip item. " + xmlResponse.getBody());
                    return;
                }
                Object attachmentsObj = value.get("Attachments");
                if (attachmentsObj == null) {
                    attachmentsObj = "false";
                }
                boolean attachments = Boolean.valueOf(attachmentsObj.toString());
                Object createdObj = value.get("Created");
                if (createdObj == null) {
                    logger.warn("Created field does not contain. Skip item. " + xmlResponse.getBody());
                    return;
                }
                Date created = sdf.parse(createdObj.toString());
                Object modifiedObj = value.get("Modified");
                if (modifiedObj == null) {
                    logger.warn("Modified field does not contain. Skip item. " + xmlResponse.getBody());
                    return;
                }
                Date modified = sdf.parse(modifiedObj.toString());

                GetListItems2013Response.ListItem listItem = new GetListItems2013Response.ListItem(idObj.toString(),
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

        return new GetListItems2013Response(listItems);
    }

    private static class GetListItemsDocHandler extends DefaultHandler {
        private final Map<String, Object> dataMap = new HashMap<>();
        private Map<String, Object> resultMap = null;

        private String fieldName;

        private final StringBuilder buffer = new StringBuilder(1000);

        @Override
        public void startDocument() {
            dataMap.clear();
            dataMap.put("value", new ArrayList<>());
        }

        @Override
        public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) {
            if ("entry".equals(qName)) {
                resultMap = new HashMap<>();
            } else {
                if ("d:Id".equals(qName)) {
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
                    String rel = attributes.getValue("rel");
                    if (rel != null && rel.equals("edit")) {
                        resultMap.put("odata.editLink", attributes.getValue("href"));
                    }
                }
            }
        }

        @Override
        public void characters(final char[] ch, final int offset, final int length) {
            if (fieldName != null) {
                buffer.append(new String(ch, offset, length));
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public void endElement(final String uri, final String localName, final String qName) {
            if ("entry".equals(qName)) {
                if (resultMap != null) {
                    ((List)dataMap.get("value")).add(resultMap);
                }
                resultMap = null;
            } else {
                if (resultMap != null && fieldName != null) {
                    if (!resultMap.containsKey(fieldName)) {
                        resultMap.put(fieldName, buffer.toString());
                    }
                    fieldName = null;
                }
            }
        }

        @Override
        public void endDocument() {
            // nothing
        }

        public Map<String, Object> getDataMap() {
            return dataMap;
        }
    }
}
