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
package org.codelibs.fess.ds.sharepoint.client.api;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.core.lang.StringUtil;
import org.codelibs.core.stream.StreamUtil;
import org.codelibs.fess.crawler.Constants;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointServerException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;
import org.xml.sax.helpers.DefaultHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class SharePointApi<T extends SharePointApiResponse> {
    private static final Logger logger = LogManager.getLogger(SharePointApi.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    protected final CloseableHttpClient client;
    protected final String siteUrl;
    protected final OAuth oAuth;

    protected SharePointApi(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        this.client = client;
        this.siteUrl = siteUrl;
        this.oAuth = oAuth;
    }

    public abstract T execute();

    protected JsonResponse doJsonRequest(final HttpRequestBase httpRequest) {
        httpRequest.addHeader("Accept", "application/json");
        if (oAuth != null) {
            oAuth.apply(httpRequest);
        }
        try (CloseableHttpResponse httpResponse = client.execute(httpRequest)) {
            final String body = EntityUtils.toString(httpResponse.getEntity());
            if (logger.isDebugEnabled()) {
                logger.debug("API's ResponseBody. [url:{}] [body:{}]", httpRequest.getURI().toString(), body);
            }
            if (isErrorResponse(httpResponse)) {
                @SuppressWarnings("unchecked")
                final Map<String, Object> bodyMap = StringUtil.isNotBlank(body) ? objectMapper.readValue(body, Map.class) : null;
                throw new SharePointServerException("Api returned error. code:" + httpResponse.getStatusLine().getStatusCode() + "url:"
                        + httpRequest.getURI().toString() + " body:" + bodyMap, httpResponse.getStatusLine().getStatusCode());
            }

            @SuppressWarnings("unchecked")
            final Map<String, Object> bodyMap = objectMapper.readValue(body, Map.class);
            if (body.contains("odata.error")) {
                throw new SharePointServerException(
                        "Api returned error. " + " url:" + httpRequest.getURI().toString() + " body:" + bodyMap.toString(),
                        httpResponse.getStatusLine().getStatusCode());
            }
            return new JsonResponse(body, bodyMap, httpResponse.getStatusLine().getStatusCode());
        } catch (final SharePointServerException e) {
            throw e;
        } catch (final Exception e) {
            throw new SharePointClientException("Request failure. " + e.getMessage(), e);
        }
    }

    protected XmlResponse doXmlRequest(final HttpRequestBase httpRequest) {
        httpRequest.addHeader("Accept", "application/xml; charset=\"UTF-8\"");
        try (CloseableHttpResponse httpResponse = client.execute(httpRequest)) {
            final String body = EntityUtils.toString(httpResponse.getEntity());
            if (logger.isDebugEnabled()) {
                logger.debug("API's ResponseBody. [url:{}] [body:{}]", httpRequest.getURI().toString(), body);
            }
            if (isErrorResponse(httpResponse)) {
                throw new SharePointServerException("Api returned error. code:" + httpResponse.getStatusLine().getStatusCode() + "url:"
                        + httpRequest.getURI().toString() + " body:" + body, httpResponse.getStatusLine().getStatusCode());
            }

            if (body.contains("odata.error")) {
                throw new SharePointServerException("Api returned error. " + " url:" + httpRequest.getURI().toString() + " body:" + body,
                        httpResponse.getStatusLine().getStatusCode());
            }
            return new XmlResponse(body, httpResponse.getStatusLine().getStatusCode());
        } catch (final SharePointServerException e) {
            throw e;
        } catch (final Exception e) {
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
        if (url == null) {
            return null;
        }
        return StreamUtil.stream(StringUtils.splitPreserveAllTokens(url, '/'))
                .get(stream -> stream.map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8)).collect(Collectors.joining("/")))
                .replace("+", "%20");
    }

    public static class JsonResponse {
        private final String body;
        private final Map<String, Object> bodyMap;
        private final int statusCode;

        private JsonResponse(final String body, final Map<String, Object> bodyMap, final int statusCode) {
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

    public static class XmlResponse {
        private final String body;
        private final int statusCode;

        private XmlResponse(final String body, final int statusCode) {
            this.body = body;
            this.statusCode = statusCode;
        }

        public String getBody() {
            return body;
        }

        public boolean isErrorResponse() {
            return statusCode >= 400;
        }

        public void parseXml(final DefaultHandler handler) {
            parseXml(body, handler);
        }

        public static void parseXml(final String xml, final DefaultHandler handler) {
            final SAXParserFactory spfactory = SAXParserFactory.newInstance();
            try (InputStream is = new ByteArrayInputStream(xml.getBytes(UTF_8))) {
                spfactory.setFeature(Constants.FEATURE_SECURE_PROCESSING, true);
                spfactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                spfactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                // create a sax parser
                final SAXParser parser = spfactory.newSAXParser();
                try {
                    parser.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, StringUtil.EMPTY);
                    parser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, StringUtil.EMPTY);
                } catch (final Exception e) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Failed to set a property.", e);
                    }
                }
                // parse a content
                parser.parse(is, handler);
            } catch (final Exception e) {
                throw new SharePointClientException("Could not create a data map from XML content.", e);
            }
        }
    }
}
