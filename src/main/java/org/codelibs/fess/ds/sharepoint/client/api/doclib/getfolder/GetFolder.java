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
package org.codelibs.fess.ds.sharepoint.client.api.doclib.getfolder;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;

public class GetFolder extends SharePointApi<GetFolderResponse> {
    private static final Logger logger = LogManager.getLogger(GetFolder.class);

    private static final String API_PATH = "_api/web/GetFolderByServerRelativePath(decodedUrl='{{url}}')";

    private String serverRelativeUrl = null;

    public GetFolder(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    public GetFolder setServerRelativeUrl(final String serverRelativeUrl) {
        this.serverRelativeUrl = serverRelativeUrl;
        return this;
    }

    @Override
    public GetFolderResponse execute() {
        if (serverRelativeUrl == null) {
            throw new SharePointClientException("serverRelativeUrl is required.");
        }

        final String buildUrl = buildUrl();
        if (logger.isDebugEnabled()) {
            logger.debug("buildUrl: {}", buildUrl);
        }
        final HttpGet httpGet = new HttpGet(buildUrl);
        final JsonResponse jsonResponse = doJsonRequest(httpGet);
        try {
            return GetFolderResponse.build(jsonResponse);
        } catch (final Exception e) {
            throw new SharePointClientException(e);
        }
    }

    private String buildUrl() {
        return siteUrl + "/" + API_PATH.replace("{{url}}", encodeRelativeUrl(serverRelativeUrl));
    }
}
