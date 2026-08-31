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
package org.codelibs.fess.ds.sharepoint.client2013.api.file.getfile;

import java.io.IOException;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fess.ds.sharepoint.client.api.file.getfile.GetFile;
import org.codelibs.fess.ds.sharepoint.client.backoff.SharePointBackoff;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;

/**
 * SharePoint 2013 specific implementation for downloading individual files.
 * This class extends the base GetFile functionality with SharePoint 2013
 * compatibility, using the appropriate REST API endpoints for that version.
 */
public class GetFile2013 extends GetFile {
    /** Logger for this class. */
    private static final Logger logger = LogManager.getLogger(GetFile2013.class);

    /** The server-relative URL of the file to download (SharePoint 2013 specific). */
    private String serverRelativeUrl = null;

    /**
     * Constructs a new GetFile2013 API client.
     *
     * @param client the HTTP client to use for requests
     * @param siteUrl the SharePoint 2013 site URL
     * @param oAuth the OAuth authentication provider
     */
    public GetFile2013(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    /**
     * Constructs a new GetFile2013 API client with an explicit backoff, so a test can replace the
     * wait applied when SharePoint reports itself busy with one that does not actually sleep.
     *
     * @param client the HTTP client to use for requests
     * @param siteUrl the SharePoint 2013 site URL
     * @param oAuth the OAuth authentication provider
     * @param backoff the wait applied when SharePoint reports itself busy
     */
    public GetFile2013(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth, final SharePointBackoff backoff) {
        super(client, siteUrl, oAuth, backoff);
    }

    /**
     * Sets the server-relative URL of the file to download.
     *
     * @param serverRelativeUrl the server-relative URL of the target file
     * @return this instance for method chaining
     */
    @Override
    public GetFile2013 setServerRelativeUrl(final String serverRelativeUrl) {
        this.serverRelativeUrl = serverRelativeUrl;
        return this;
    }

    /**
     * Executes the file download request to SharePoint 2013.
     *
     * @return a GetFile2013Response containing the downloaded file content
     * @throws SharePointClientException if the server-relative URL is not set,
     *         if the request fails, or if an error response is received
     */
    @Override
    public GetFile2013Response execute() {
        if (serverRelativeUrl == null) {
            throw new SharePointClientException("serverRelativeUrl is required.");
        }

        final HttpGet httpGet = new HttpGet(buildUrl());
        httpGet.addHeader("Accept", "application/json");
        CloseableHttpResponse httpResponse = null;
        try {
            httpResponse = client.execute(httpGet);
            // See GetFile#execute for why this call is here rather than only in the shared path.
            awaitIfServerIsBusy(httpResponse);
            if (isErrorResponse(httpResponse)) {
                final int status = httpResponse.getStatusLine().getStatusCode();
                final String body = EntityUtils.toString(httpResponse.getEntity());
                // Carries the status code - see GetFile#execute for why.
                throw new SharePointClientException("GetFile Request failure. status:" + status + " body:" + body, status);
            }
            final GetFile2013Response response = new GetFile2013Response(httpResponse);
            // Ownership passes to the response, which closes it; the finally below must not.
            httpResponse = null;
            return response;
        } catch (final SharePointClientException e) {
            // Already carries the status code and the response body. Letting the catch below wrap
            // it a second time would bury both.
            throw e;
        } catch (final Exception e) {
            throw new SharePointClientException("Request failure.", e);
        } finally {
            if (httpResponse != null) {
                try {
                    httpResponse.close();
                } catch (final IOException e) {
                    logger.warn("Failed to close the response.", e);
                }
            }
        }
    }

    /**
     * Builds the SharePoint 2013 REST API URL for downloading a file by server-relative URL.
     * Uses the SharePoint 2013 specific API endpoint format.
     *
     * @return the complete API URL for the file download request
     */
    @Override
    protected String buildUrl() {
        return siteUrl + "/_api/web/GetFileByServerRelativeUrl('" + encodeRelativeUrl(serverRelativeUrl) + "')/$value";
    }
}
