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
package org.codelibs.fess.ds.sharepoint.client.api.file.getfile;

import java.io.IOException;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.backoff.SharePointBackoff;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;

/**
 * SharePoint API client for downloading individual files from SharePoint.
 * This class provides functionality to retrieve file content by server-relative URL
 * using SharePoint's REST API.
 */
public class GetFile extends SharePointApi<GetFileResponse> {
    /** Logger for this class. */
    private static final Logger logger = LogManager.getLogger(GetFile.class);

    /** The server-relative URL of the file to download. */
    private String serverRelativeUrl = null;

    /**
     * Constructs a new GetFile API client.
     *
     * @param client the HTTP client to use for requests
     * @param siteUrl the SharePoint site URL
     * @param oAuth the OAuth authentication provider
     */
    public GetFile(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    /**
     * Constructs a new GetFile API client with an explicit backoff, so a test can replace the
     * wait applied when SharePoint reports itself busy with one that does not actually sleep.
     *
     * @param client the HTTP client to use for requests
     * @param siteUrl the SharePoint site URL
     * @param oAuth the OAuth authentication provider
     * @param backoff the wait applied when SharePoint reports itself busy
     */
    public GetFile(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth, final SharePointBackoff backoff) {
        super(client, siteUrl, oAuth, backoff);
    }

    /**
     * Sets the server-relative URL of the file to download.
     *
     * @param serverRelativeUrl the server-relative URL of the target file
     * @return this instance for method chaining
     */
    public GetFile setServerRelativeUrl(final String serverRelativeUrl) {
        this.serverRelativeUrl = serverRelativeUrl;
        return this;
    }

    /**
     * Executes the file download request to SharePoint.
     *
     * @return a GetFileResponse containing the downloaded file content
     * @throws SharePointClientException if the server-relative URL is not set,
     *         if the request fails, or if an error response is received
     */
    @Override
    public GetFileResponse execute() {
        if (serverRelativeUrl == null) {
            throw new SharePointClientException("serverRelativeUrl is required.");
        }

        final String buildUrl = buildUrl();
        if (logger.isDebugEnabled()) {
            logger.debug("buildUrl: {}", buildUrl);
        }
        final HttpGet httpGet = new HttpGet(buildUrl);
        httpGet.addHeader("Accept", "application/json");
        if (oAuth != null) {
            oAuth.apply(httpGet);
        }
        CloseableHttpResponse httpResponse = null;
        try {
            httpResponse = client.execute(httpGet);
            // The shared path (SharePointApi#doJsonRequest/doXmlRequest) calls this too; GetFile
            // bypasses that path by calling client.execute() directly above, so without this call
            // a file download would never see a busy-server wait at all.
            awaitIfServerIsBusy(httpResponse);
            if (oAuth != null && httpResponse.getStatusLine().getStatusCode() == 401) {
                // The build-time token never gets refreshed on its own (see
                // SharePointClientBuilder#build), so once it lapses every remaining download
                // would otherwise fail with 401 for the rest of the crawl. One refresh-and-retry
                // recovers from exactly that.
                if (logger.isDebugEnabled()) {
                    logger.debug("Got 401 for {}; refreshing the access token and retrying once.", buildUrl);
                }
                httpResponse.close();
                httpResponse = null;
                oAuth.updateAccessToken(client);
                httpGet.removeHeaders("Authorization");
                oAuth.apply(httpGet);
                httpResponse = client.execute(httpGet);
                awaitIfServerIsBusy(httpResponse);
            }
            if (isErrorResponse(httpResponse)) {
                final int status = httpResponse.getStatusLine().getStatusCode();
                final String body = EntityUtils.toString(httpResponse.getEntity());
                // Carries the status code (unlike a plain SharePointClientException(message)) so
                // SharePointCrawler#doCrawl's retry loop can still recognize a 503 here and back
                // off before retrying, exactly as it already does for a 503 from any other API
                // call - and, just as importantly, so it does not wait before a final, abandoned
                // attempt that will not be retried at all.
                throw new SharePointClientException("GetFile Request failure. status:" + status + " body:" + body, status);
            }
            final GetFileResponse response = new GetFileResponse(httpResponse);
            // Ownership passes to the response, which closes it; the finally below must not.
            httpResponse = null;
            return response;
        } catch (final SharePointClientException e) {
            // Already carries the status code and the response body. Letting the catch below wrap
            // it a second time would bury both.
            throw e;
        } catch (final Exception e) {
            throw new SharePointClientException("GetFile Request failure.", e);
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
     * Builds the SharePoint REST API URL for downloading a file by server-relative path.
     *
     * @return the complete API URL for the file download request
     */
    protected String buildUrl() {
        return siteUrl + "/_api/web/GetFileByServerRelativePath(decodedUrl='" + encodeRelativeUrl(serverRelativeUrl) + "')/$value";
    }
}
