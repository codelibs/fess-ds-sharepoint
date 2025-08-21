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
package org.codelibs.fess.ds.sharepoint.client.oauth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * OAuth 2.0 authentication handler for SharePoint Online.
 * This class manages OAuth access tokens for authenticating with SharePoint Online
 * using client credentials flow. It handles token acquisition and applies
 * authorization headers to HTTP requests.
 *
 * <p>The OAuth flow uses the Windows Azure Access Control Service (ACS) to obtain
 * access tokens that are valid for SharePoint Online API operations.</p>
 */
public class OAuth {
    /** Logger for OAuth operations */
    private static final Logger logger = LogManager.getLogger(OAuth.class);
    /** JSON object mapper for parsing token responses */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** Base URL for Azure Access Control Service */
    private static final String ACCESS_CTL_URL = "https://accounts.accesscontrol.windows.net/";

    /** OAuth client ID for SharePoint application */
    private final String clientId;
    /** OAuth client secret for SharePoint application */
    private final String clientSecret;
    /** SharePoint tenant name */
    private final String tenant;
    /** Azure AD realm identifier */
    private final String realm;
    /** Current access token for API authentication */
    private String accessToken = null;

    /**
     * Constructs a new OAuth instance for SharePoint authentication.
     *
     * @param clientId OAuth client ID for the SharePoint application
     * @param clientSecret OAuth client secret for the SharePoint application
     * @param tenant SharePoint tenant name (without .sharepoint.com)
     * @param realm Azure AD realm identifier
     */
    public OAuth(final String clientId, final String clientSecret, final String tenant, final String realm) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tenant = tenant;
        this.realm = realm;
    }

    /**
     * Updates the OAuth access token by requesting a new token from Azure ACS.
     * This method performs a client credentials flow to obtain a new access token
     * that can be used for SharePoint API authentication.
     *
     * @param httpClient HTTP client for making the token request
     * @throws SharePointClientException if token acquisition fails
     */
    @SuppressWarnings("unchecked")
    public void updateAccessToken(final CloseableHttpClient httpClient) {
        logger.info("Update access_token.");
        final HttpPost httpPost = new HttpPost(buildGetTokenUtl());
        httpPost.setEntity(new UrlEncodedFormEntity(buildFormParams(), StandardCharsets.UTF_8));
        try (final CloseableHttpResponse httpResponse = httpClient.execute(httpPost)) {
            final String body = EntityUtils.toString(httpResponse.getEntity());
            if (httpResponse.getStatusLine().getStatusCode() >= 400) {
                throw new SharePointClientException(
                        "Failed to update access_token. http_status:" + httpResponse.getStatusLine().getStatusCode() + " body: " + body);
            }
            final Map<String, String> bodyMap = objectMapper.readValue(body, Map.class);
            if (!bodyMap.containsKey("access_token")) {
                throw new SharePointClientException("Failed to update access_token. " + body);
            }
            accessToken = bodyMap.get("access_token");
        } catch (final IOException e) {
            throw new SharePointClientException("Failed to update access_token.", e);
        }
    }

    /**
     * Applies the OAuth Bearer token to an HTTP request.
     * This method adds the Authorization header with the current access token
     * to authenticate the request with SharePoint.
     *
     * @param httpRequest HTTP request to authenticate
     */
    public void apply(final HttpRequestBase httpRequest) {
        httpRequest.addHeader("Authorization", "Bearer " + accessToken);
    }

    /**
     * Builds the OAuth token endpoint URL for the specific realm.
     *
     * @return complete URL for token acquisition
     */
    private String buildGetTokenUtl() {
        return ACCESS_CTL_URL + realm + "/tokens/OAuth/2";
    }

    /**
     * Builds the form parameters required for OAuth client credentials flow.
     * Creates the necessary parameters including grant type, resource, client ID, and secret.
     *
     * @return list of form parameters for the token request
     */
    private List<NameValuePair> buildFormParams() {
        final List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("grant_type", "client_credentials"));
        params.add(new BasicNameValuePair("resource", "00000003-0000-0ff1-ce00-000000000000/" + tenant + ".sharepoint.com@" + realm));
        params.add(new BasicNameValuePair("client_id", clientId + "@" + realm));
        params.add(new BasicNameValuePair("client_secret", clientSecret));
        return params;
    }
}
