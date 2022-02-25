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
package org.codelibs.fess.ds.sharepoint.client2013.api.doclib.getlistitem;

import java.util.HashMap;
import java.util.Map;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.doclib.getlistitem.GetDoclibListItem;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;
import org.codelibs.fess.util.DocumentUtil;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

//TODO
public class GetDoclibListItem2013 extends GetDoclibListItem {
    private String serverRelativeUrl = null;

    public GetDoclibListItem2013(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    @Override
    public GetDoclibListItem2013 setServerRelativeUrl(final String serverRelativeUrl) {
        this.serverRelativeUrl = serverRelativeUrl;
        return this;
    }

    @Override
    public GetDoclibListItem2013Response execute() {
        if (serverRelativeUrl == null) {
            throw new SharePointClientException("serverRelativeUrl is required.");
        }

        final HttpGet httpGet = new HttpGet(buildUrl());
        final GetDoclibListItemHandler handler = new GetDoclibListItemHandler();
        final XmlResponse xmlResponse = doXmlRequest(httpGet);
        xmlResponse.parseXml(handler);
        final Map<String, Object> bodyMap = handler.getDataMap();
        try {
            final String itemId = DocumentUtil.getValue(bodyMap, "Id", String.class);
            final String listId = getListId(DocumentUtil.getValue(bodyMap, "odata.editLink", String.class));
            return new GetDoclibListItem2013Response(listId, itemId);
        } catch (final Exception e) {
            throw new SharePointClientException(e);
        }
    }

    @Override
    protected String buildUrl() {
        return siteUrl + "_api/Web/GetFolderByServerRelativeUrl('" + encodeRelativeUrl(serverRelativeUrl) + "')/ListItemAllFields";
    }

    @Override
    protected String getListId(final String editLink) {
        if (editLink == null) {
            return null;
        }
        return editLink.substring(editLink.indexOf("(guid'") + "(guid'".length(), editLink.indexOf("')"));
    }

    private static class GetDoclibListItemHandler extends DefaultHandler {
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
            if ("d:Id".equals(qName)) {
                fieldName = "Id";
                buffer.setLength(0);
            } else if ("link".equals(qName)) {
                final String rel = attributes.getValue("rel");
                if ("edit".equals(rel)) {
                    dataMap.put("odata.editLink", attributes.getValue("href"));
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
