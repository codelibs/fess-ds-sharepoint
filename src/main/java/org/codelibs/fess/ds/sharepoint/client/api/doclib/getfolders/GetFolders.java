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
package org.codelibs.fess.ds.sharepoint.client.api.doclib.getfolders;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;

public class GetFolders extends SharePointApi<GetFoldersResponse> {
    private static final String API_PATH = "_api/web/GetFolderByServerRelativeUrl('{{url}}')/Folders";
    private static final String PAGING_PARAM = "%24skip={{start}}&%24top={{num}}";

    private String serverRelativeUrl = null;
    private int num = 100;
    private int start = 0;

    public GetFolders(CloseableHttpClient client, String siteUrl) {
        super(client, siteUrl);
    }

    public GetFolders setServerRelativeUrl(String serverRelativeUrl) {
        this.serverRelativeUrl = serverRelativeUrl;
        return this;
    }

    public GetFolders setNum(int num) {
        this.num = num;
        return this;
    }

    public GetFolders setStart(int start) {
        this.start = start;
        return this;
    }

    @Override
    public GetFoldersResponse execute() {
        if (serverRelativeUrl == null) {
            throw new SharePointClientException("serverRelativeUrl is required.");
        }

        final HttpGet httpGet =
                new HttpGet(siteUrl + "/"+
                        API_PATH.replace("{{url}}", encodeRelativeUrl(serverRelativeUrl)) +
                        "?" + PAGING_PARAM.replace("{{start}}", String.valueOf(start)).replace("{{num}}", String.valueOf(num)));        JsonResponse jsonResponse = doJsonRequest(httpGet);
        try {
            return GetFoldersResponse.build(jsonResponse);
        } catch (Exception e) {
            throw new SharePointClientException(e);
        }
    }
}
