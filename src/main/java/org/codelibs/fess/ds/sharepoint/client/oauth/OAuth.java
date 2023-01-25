/*
 * Copyright 2012-2023 CodeLibs Project and the Others.
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
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

public class OAuth {
    private static final Logger logger = LoggerFactory.getLogger(OAuth.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String ACCESS_CTL_URL = "https://accounts.accesscontrol.windows.net/";

    private final String clientId;
    private final String clientSecret;
    private final String tenant;
    private final String realm;
    private String accessToken = null;

    public OAuth(final String clientId, final String clientSecret, final String tenant, final String realm) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tenant = tenant;
        this.realm = realm;
    }

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

    public void apply(final HttpRequestBase httpRequest) {
        httpRequest.addHeader("Authorization", "Bearer " + accessToken);
    }

    private String buildGetTokenUtl() {
        return ACCESS_CTL_URL + realm + "/tokens/OAuth/2";
    }

    private List<NameValuePair> buildFormParams() {
        final List<NameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("grant_type", "client_credentials"));
        params.add(new BasicNameValuePair("resource", "00000003-0000-0ff1-ce00-000000000000/" + tenant + ".sharepoint.com@" + realm));
        params.add(new BasicNameValuePair("client_id", clientId + "@" + realm));
        params.add(new BasicNameValuePair("client_secret", clientSecret));
        return params;
    }
}
