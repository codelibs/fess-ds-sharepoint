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
package org.codelibs.fess.ds.sharepoint.client2013.api.doclib.getfolders;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.doclib.getfolders.GetFolders;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;

public class GetFolders2013 extends GetFolders {
    private static final String API_PATH = "_api/web/GetFolderByServerRelativeUrl('{{url}}')/Folders";
    private static final String PAGING_PARAM = "%24skip={{start}}&%24top={{num}}";

    private String serverRelativeUrl = null;
    private int num = 100;
    private int start = 0;

    public GetFolders2013(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    @Override
    public GetFolders2013 setServerRelativeUrl(final String serverRelativeUrl) {
        this.serverRelativeUrl = serverRelativeUrl;
        return this;
    }

    @Override
    public GetFolders2013 setNum(final int num) {
        this.num = num;
        return this;
    }

    @Override
    public GetFolders2013 setStart(final int start) {
        this.start = start;
        return this;
    }

    @Override
    public GetFolders2013Response execute() {
        if (serverRelativeUrl == null) {
            throw new SharePointClientException("serverRelativeUrl is required.");
        }

        final HttpGet httpGet = new HttpGet(siteUrl + "/" + API_PATH.replace("{{url}}", encodeRelativeUrl(serverRelativeUrl)) + "?"
                + PAGING_PARAM.replace("{{start}}", String.valueOf(start)).replace("{{num}}", String.valueOf(num)));
        final XmlResponse xmlResponse = doXmlRequest(httpGet);
        try {
            return GetFolders2013Response.build(xmlResponse);
        } catch (final Exception e) {
            throw new SharePointClientException(e);
        }
    }
}
