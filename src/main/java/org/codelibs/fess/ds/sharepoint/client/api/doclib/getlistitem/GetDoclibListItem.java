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
package org.codelibs.fess.ds.sharepoint.client.api.doclib.getlistitem;

import java.util.Map;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;
import org.codelibs.fess.util.DocumentUtil;

public class GetDoclibListItem extends SharePointApi<GetDoclibListItemResponse> {
    private String serverRelativeUrl = null;

    public GetDoclibListItem(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    public GetDoclibListItem setServerRelativeUrl(final String serverRelativeUrl) {
        this.serverRelativeUrl = serverRelativeUrl;
        return this;
    }

    @Override
    public GetDoclibListItemResponse execute() {
        if (serverRelativeUrl == null) {
            throw new SharePointClientException("serverRelativeUrl is required.");
        }

        final HttpGet httpGet = new HttpGet(buildUrl());
        final JsonResponse jsonResponse = doJsonRequest(httpGet);
        final Map<String, Object> bodyMap = jsonResponse.getBodyAsMap();
        try {
            final String itemId = DocumentUtil.getValue(bodyMap, "Id", String.class);
            final String listId = getListId(DocumentUtil.getValue(bodyMap, "odata.editLink", String.class));
            return new GetDoclibListItemResponse(listId, itemId);
        } catch (final Exception e) {
            throw new SharePointClientException(e);
        }
    }

    protected String buildUrl() {
        return siteUrl + "_api/Web/GetFolderByServerRelativePath(decodedurl='" + encodeRelativeUrl(serverRelativeUrl)
                + "')/ListItemAllFields";
    }

    protected String getListId(final String editLink) {
        if (editLink == null) {
            return null;
        }
        return editLink.substring(editLink.indexOf("(guid'") + "(guid'".length(), editLink.indexOf("')"));
    }
}
