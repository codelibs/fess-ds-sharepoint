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
package org.codelibs.fess.ds.sharepoint.client.api.doclib.getfiles;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;

public class GetFiles extends SharePointApi<GetFilesResponse> {
    private static final String API_PATH = "_api/web/GetFolderByServerRelativeUrl('{{url}}')/Files";
    private static final String PAGING_PARAM = "%24skip={{start}}&%24top={{num}}";

    private String serverRelativeUrl = null;
    private int num = 100;
    private int start = 0;

    public GetFiles(CloseableHttpClient client, String siteUrl, OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    public GetFiles setServerRelativeUrl(String serverRelativeUrl) {
        this.serverRelativeUrl = serverRelativeUrl;
        return this;
    }

    public GetFiles setNum(int num) {
        this.num = num;
        return this;
    }

    public GetFiles setStart(int start) {
        this.start = start;
        return this;
    }

    @Override
    public GetFilesResponse execute() {
        if (serverRelativeUrl == null) {
            throw new SharePointClientException("serverRelativeUrl is required.");
        }

        final HttpGet httpGet =
                new HttpGet(siteUrl + "/"+
                        API_PATH.replace("{{url}}", encodeRelativeUrl(serverRelativeUrl)) +
                        "?" + PAGING_PARAM.replace("{{start}}", String.valueOf(start)).replace("{{num}}", String.valueOf(num)));
        SharePointApi.JsonResponse jsonResponse = doJsonRequest(httpGet);
        try {
            return GetFilesResponse.build(jsonResponse);
        } catch (Exception e) {
            throw new SharePointClientException(e);
        }
    }
}
