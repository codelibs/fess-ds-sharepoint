/*
 * Copyright 2012-2021 CodeLibs Project and the Others.
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
package org.codelibs.fess.ds.sharepoint.client2013.api.list.getlists;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlists.GetList;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlists.GetListResponse;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlists.GetListsResponse;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class GetList2013 extends GetList {
    private static final String API_BY_LIST_ID_PATH = "_api/web/lists(guid'{list_guid}')";
    private static final String API_BY_LIST_NAME_PATH = "_api/web/lists/GetByTitle('{list_name}')";


    protected String listId = null;
    protected String listName = null;

    public GetList2013(CloseableHttpClient client, String siteUrl, OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    public GetList2013 setListId(String listId) {
        this.listId = listId;
        return this;
    }

    public GetList2013 setListName(String listName) {
        this.listName = listName;
        return this;
    }

    @Override
    public GetListResponse execute() {
        final String apiPath;
        if (StringUtils.isNotBlank(listId)) {
            apiPath = API_BY_LIST_ID_PATH.replace("{list_guid}", listId);
        } else if (StringUtils.isNotBlank(listName)) {
            apiPath = API_BY_LIST_NAME_PATH.replace("{list_name}", URLEncoder.encode(listName, StandardCharsets.UTF_8).replace("+", "%20"));
        } else {
            throw new SharePointClientException("[GetList] listId/listName is required.");
        }

        final HttpGet httpGet = new HttpGet(siteUrl + "/" + apiPath);
        XmlResponse xmlResponse = doXmlRequest(httpGet);
        return buildResponse(xmlResponse);
    }

    private GetListResponse buildResponse(final XmlResponse xmlResponse) {
        final GetListDocHandler handler = new GetListDocHandler();
        xmlResponse.parseXml(handler);
        final Map<String, Object> dataMap = handler.getDataMap();

        Object titleObj = dataMap.get("Title");
        if (titleObj == null) {
            throw new SharePointClientException("Title is null.");
        }
        Object idObj = dataMap.get("Id");
        if (idObj == null) {
            throw new SharePointClientException("Id is null.");
        }
        Object noCrawl = dataMap.get("NoCrawl");
        if (noCrawl == null) {
            noCrawl = "true";
        }
        GetListsResponse.SharePointList sharePointList = new GetListsResponse.SharePointList(idObj.toString(), titleObj.toString(), Boolean.valueOf(noCrawl.toString()));
        return new GetListResponse(sharePointList);
    }

    private static class GetListDocHandler extends DefaultHandler {
        private final Map<String, Object> dataMap = new HashMap<>();

        private String fieldName;

        private final StringBuilder buffer = new StringBuilder(1000);

        @Override
        public void startDocument() {
            dataMap.clear();
            dataMap.put("Exists", true);
        }

        @Override
        public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) {
            if ("d:Title".equals(qName)) {
                fieldName = "Title";
                buffer.setLength(0);
            } else if ("d:Id".equals(qName)) {
                fieldName = "Id";
                buffer.setLength(0);
            } else if ("d:NoCrawl".equals(qName)) {
                fieldName = "NoCrawl";
                buffer.setLength(0);
            }
        }

        @Override
        public void characters(final char[] ch, final int offset, final int length) {
            if (fieldName != null) {
                buffer.append(new String(ch, offset, length));
            }
        }

        @Override
        public void endElement(final String uri, final String localName, final String qName) {
            if (fieldName != null) {
                if (!dataMap.containsKey(fieldName)) {
                    dataMap.put(fieldName, buffer.toString());
                }
                fieldName = null;
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