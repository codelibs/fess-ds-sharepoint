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
package org.codelibs.fess.ds.sharepoint.client.api.list.getlistforms;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;

public class GetForms extends SharePointApi<GetFormsResponse> {
    private static final String API_PATH = "_api/Web/Lists(guid'{{id}}')/Forms";
    private static final String GETBYTITLE_API_PATH = "_api/lists/getbytitle('{{list_name}}')/Forms";

    private String listId = null;
    private String listName = null;

    public GetForms(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    public GetForms setListId(final String listId) {
        this.listId = listId;
        return this;
    }

    public GetForms setListName(final String listName) {
        this.listName = listName;
        return this;
    }

    @Override
    public GetFormsResponse execute() {
        if (listId == null && listName == null) {
            throw new SharePointClientException("ListID/ListName is required.");
        }
        final HttpGet httpGet;
        if (listId != null) {
            httpGet = new HttpGet(siteUrl + "/" + API_PATH.replace("{{id}}", listId));
        } else {
            httpGet = new HttpGet(siteUrl + "/" + GETBYTITLE_API_PATH.replace("{{list_name}}", listName));
        }
        final JsonResponse jsonResponse = doJsonRequest(httpGet);
        return GetFormsResponse.build(jsonResponse);
    }
}
