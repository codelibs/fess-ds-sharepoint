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
package org.codelibs.fess.ds.sharepoint.client2013.api.list.getlistitem;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemValue;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;

public class GetListItemValue2013 extends GetListItemValue {
    private String listId = null;
    private String itemId = null;

    public GetListItemValue2013(CloseableHttpClient client, String siteUrl) {
        super(client, siteUrl);
    }

    public GetListItemValue2013 setListId(String listId) {
        this.listId = listId;
        return this;
    }

    public GetListItemValue2013 setItemId(String itemId) {
        this.itemId = itemId;
        return this;
    }

    @Override
    public GetListItemValue2013Response execute() {
        if (listId == null || itemId == null) {
            throw new SharePointClientException("listId/itemId is required.");
        }
        final HttpGet httpGet = new HttpGet(buildUrl());
        XmlResponse xmlResponse = doXmlRequest(httpGet);
        try {
            return GetListItemValue2013Response.build(xmlResponse);
        } catch (Exception e) {
            throw new SharePointClientException(e);
        }
    }

    private String buildUrl() {
        return siteUrl + "/_api/Web/Lists(guid'" + listId + "')/Items(" + itemId + ")/FieldValuesAsText";
    }
}