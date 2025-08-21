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
package org.codelibs.fess.ds.sharepoint.client2013.api.doclib.getfolder;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.doclib.getfolder.GetFolder;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;

/**
 * SharePoint 2013-specific API client for retrieving folder information from document libraries.
 * This class extends the base GetFolder functionality and adapts it for SharePoint 2013
 * compatibility by using XML responses and the GetFolderByServerRelativeUrl endpoint.
 *
 * <p>Unlike newer SharePoint versions, this implementation processes XML responses
 * instead of JSON and uses slightly different API endpoints.</p>
 *
 * @see GetFolder
 * @see GetFolder2013Response
 */
public class GetFolder2013 extends GetFolder {
    private static final String API_PATH = "_api/web/GetFolderByServerRelativeUrl('{{url}}')";

    /** The server-relative URL of the folder to retrieve */
    private String serverRelativeUrl = null;

    /**
     * Constructs a new GetFolder2013 API client for SharePoint 2013.
     *
     * @param client the HTTP client for making requests
     * @param siteUrl the base URL of the SharePoint 2013 site
     * @param oAuth the OAuth authentication provider
     */
    public GetFolder2013(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    /**
     * Sets the server-relative URL of the folder to retrieve.
     * This overrides the parent method to return the correct type for method chaining.
     *
     * @param serverRelativeUrl the server-relative path to the folder (e.g., "/sites/mysite/documents/myfolder")
     * @return this instance for method chaining
     */
    @Override
    public GetFolder2013 setServerRelativeUrl(final String serverRelativeUrl) {
        this.serverRelativeUrl = serverRelativeUrl;
        return this;
    }

    /**
     * Executes the get folder request for SharePoint 2013 and returns the folder information.
     * This method processes XML responses specific to SharePoint 2013 format.
     *
     * @return the folder response containing folder metadata and properties
     * @throws SharePointClientException if the server relative URL is not set,
     *         if the HTTP request fails, or if the XML response cannot be parsed
     */
    @Override
    public GetFolder2013Response execute() {
        if (serverRelativeUrl == null) {
            throw new SharePointClientException("serverRelativeUrl is required.");
        }

        final HttpGet httpGet = new HttpGet(siteUrl + "/" + API_PATH.replace("{{url}}", encodeRelativeUrl(serverRelativeUrl)));
        final XmlResponse xmlResponse = doXmlRequest(httpGet);
        try {
            return GetFolder2013Response.build(xmlResponse);
        } catch (final Exception e) {
            throw new SharePointClientException(e);
        }
    }
}
