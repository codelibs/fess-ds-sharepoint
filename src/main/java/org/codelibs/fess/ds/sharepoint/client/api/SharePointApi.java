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
package org.codelibs.fess.ds.sharepoint.client.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public abstract class SharePointApi<T extends SharePointApiResponse> {
    private static final Logger logger = LoggerFactory.getLogger(SharePointApi.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    protected final CloseableHttpClient client;
    protected final String siteUrl;

    public SharePointApi(CloseableHttpClient client, String siteUrl) {
        this.client = client;
        this.siteUrl = siteUrl;
    }

    abstract public T execute();

    @SuppressWarnings("unchecked")
    protected JsonResponse doRequest(final HttpRequestBase httpRequest) {
        httpRequest.addHeader("Accept", "application/json");
        try (CloseableHttpResponse httpResponse = client.execute(httpRequest)) {
            final String body = EntityUtils.toString(httpResponse.getEntity());
            if (logger.isDebugEnabled()) {
                logger.debug("API's ResponseBody. [url:{}] [body:{}]", httpRequest.getURI().toString(), body);
            }
            if (isErrorResponse(httpResponse)) {
                final Map<String, Object> bodyMap;
                if (StringUtils.isNotBlank(body)) {
                    bodyMap = objectMapper.readValue(body, Map.class);
                } else {
                    bodyMap = null;
                }
                throw new SharePointServerException("Api returned error. code:" + httpResponse.getStatusLine().getStatusCode() +
                        "url:" + httpRequest.getURI().toString() + " body:" + bodyMap,
                        httpResponse.getStatusLine().getStatusCode());
            }

            final Map<String, Object> bodyMap = objectMapper.readValue(body, Map.class);
            if (body.contains("odata.error")) {
                throw new SharePointServerException("Api returned error. " + " url:" + httpRequest.getURI().toString() + " body:" + bodyMap.toString(), httpResponse.getStatusLine().getStatusCode());
            }
            return new JsonResponse(body, bodyMap, httpResponse.getStatusLine().getStatusCode());
        } catch (SharePointServerException e) {
            throw e;
        } catch(Exception e) {
            throw new SharePointClientException("Request failure. " + e.getMessage(), e);
        }
    }

    protected boolean isErrorResponse(final CloseableHttpResponse response) {
        if (response.getStatusLine().getStatusCode() >= 400) {
            return true;
        }
        return false;
    }

    protected String encodeRelativeUrl(final String url) {
        String result = url;
        String[] array = url.split("/");
        for (String value: array) {
            result = result.replace(value, URLEncoder.encode(value, StandardCharsets.UTF_8));
        }
        return result.replace("+", "%20");
    }

    public static class JsonResponse {
        private final String body;
        private final Map<String, Object> bodyMap;
        private final int statusCode;

        private JsonResponse(String body, Map<String, Object> bodyMap, int statusCode) {
            this.body = body;
            this.bodyMap = bodyMap;
            this.statusCode = statusCode;
        }

        public String getBody() {
            return body;
        }

        public Map<String, Object> getBodyAsMap() {
            return bodyMap;
        }

        public boolean isErrorResponse() {
            return statusCode >= 400;
        }
    }
}
