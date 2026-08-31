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
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.core.lang.StringUtil;
import org.codelibs.core.stream.StreamUtil;
import org.codelibs.fess.crawler.Constants;
import org.codelibs.fess.ds.sharepoint.client.backoff.SharePointBackoff;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointResponseFormatException;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointServerException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;
import org.xml.sax.helpers.DefaultHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.ValidationException;

/**
 * Abstract base class for SharePoint API operations.
 *
 * @param <T> the response type
 */
public abstract class SharePointApi<T extends SharePointApiResponse> {
    private static final Logger logger = LogManager.getLogger(SharePointApi.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Accepts a bare GUID, one wrapped in plain braces ({...}), and one wrapped in the
    // percent-encoded braces (%7B...%7D) you get by copying a classic SharePoint "List="
    // query-string value verbatim - all three round-trip through the server's own
    // percent-decode today, so none of them can start being rejected.
    private static final Pattern GUID_PATTERN = Pattern
            .compile("(?:\\{|%7[bB])?" + "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}(?:\\}|%7[dD])?");

    /**
     * The response header SharePoint uses to report how loaded the server is, from 0 (idle) up
     * to 10 (very busy).
     */
    protected static final String HEALTH_SCORE_HEADER = "X-SharePointHealthScore";

    /**
     * The health score at and below which a server is not considered busy. A score above this
     * triggers a voluntary wait before this call returns, scaled by how far past the threshold
     * the score is - the same backoff shape used for a 503, since both mean the same thing: the
     * server is telling this crawl to slow down.
     */
    protected static final int HEALTH_SCORE_BUSY_THRESHOLD = 8;

    /**
     * HTTP client used for making requests to SharePoint.
     */
    protected final CloseableHttpClient client;

    /**
     * The base URL of the SharePoint site.
     */
    protected final String siteUrl;

    /**
     * OAuth authentication handler for SharePoint requests.
     */
    protected final OAuth oAuth;

    /**
     * The wait applied when SharePoint reports itself busy via {@link #HEALTH_SCORE_HEADER}.
     *
     * <p>Every subclass reaches this through this base class's field rather than a shared static,
     * so a test can hand one subclass instance a backoff with a recording, non-sleeping sleeper
     * without affecting any other instance.
     */
    protected final SharePointBackoff backoff;

    /**
     * Constructs a new SharePointApi instance, backing off on a busy server with
     * {@link SharePointBackoff#defaults()}.
     *
     * @param client the HTTP client for making requests
     * @param siteUrl the base URL of the SharePoint site
     * @param oAuth the OAuth authentication handler, may be null
     */
    protected SharePointApi(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        this(client, siteUrl, oAuth, SharePointBackoff.defaults());
    }

    /**
     * Constructs a new SharePointApi instance with an explicit backoff, so a test can replace the
     * wait applied when SharePoint reports itself busy with one that does not actually sleep.
     *
     * @param client the HTTP client for making requests
     * @param siteUrl the base URL of the SharePoint site
     * @param oAuth the OAuth authentication handler, may be null
     * @param backoff the wait applied when SharePoint reports itself busy
     */
    protected SharePointApi(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth, final SharePointBackoff backoff) {
        this.client = client;
        this.siteUrl = siteUrl;
        this.oAuth = oAuth;
        this.backoff = backoff;
    }

    /**
     * Executes the SharePoint API request and returns the response.
     *
     * @return the API response of type T
     */
    public abstract T execute();

    /**
     * Executes an HTTP request expecting a JSON response from SharePoint.
     *
     * @param httpRequest the HTTP request to execute
     * @return a JsonResponse containing the response body and metadata
     * @throws SharePointServerException if the server returns an error response
     * @throws SharePointClientException if there is a client-side error
     */
    protected JsonResponse doJsonRequest(final HttpRequestBase httpRequest) {
        // Deliberately left as the bare media type, not an explicit odata=verbose/minimalmetadata
        // parameter: every JSON parser in this plugin (GetLists, GetFoldersResponse,
        // GetFilesResponse, GetFormsResponse, GetListItemAttachmentsResponse, GetListItemRole,
        // GetListItems, GetDoclibListItem, ...) reads the JSON Light shape - jsonMap.get("value"),
        // odata.nextLink, odata.editLink - which is what a bare "application/json" already yields
        // on the SharePoint versions this plugin targets. odata=verbose would switch every one of
        // those responses to the pre-JSON-Light {"d":{"results":[...]}} shape and break them all
        // with a bare NullPointerException instead of the clear message requireJsonContentType
        // below produces for the one case this header cannot itself prevent: a server with JSON
        // Light OData disabled entirely, which ignores the requested metadata level and answers
        // with Atom XML instead, 200 and all.
        httpRequest.addHeader("Accept", "application/json");
        return doJsonRequest(httpRequest, oAuth != null);
    }

    /**
     * Executes {@code httpRequest}, refreshing the OAuth access token and retrying exactly once
     * if the first attempt comes back 401.
     *
     * <p>The build-time token this plugin acquires (see {@code SharePointClientBuilder#build})
     * never gets read again, and {@code expires_in} is never checked, so once it lapses every
     * remaining request in the crawl would otherwise fail with 401 - a long crawl outliving its
     * own token's lifetime turns into a wall of identical, useless retries. Requesting a fresh
     * token on the first 401 and replaying the same request once recovers from exactly that,
     * without polling the token's expiry ahead of time.
     *
     * @param httpRequest the HTTP request to execute
     * @param allowTokenRefresh whether a 401 here may still refresh and retry; false on the retry
     *            itself, so this recurses at most once
     * @return a JsonResponse containing the response body and metadata
     * @throws SharePointServerException if the server returns an error response
     * @throws SharePointClientException if there is a client-side error
     */
    private JsonResponse doJsonRequest(final HttpRequestBase httpRequest, final boolean allowTokenRefresh) {
        if (oAuth != null) {
            oAuth.apply(httpRequest);
        }
        try (CloseableHttpResponse httpResponse = client.execute(httpRequest)) {
            awaitIfServerIsBusy(httpResponse);
            if (allowTokenRefresh && httpResponse.getStatusLine().getStatusCode() == 401) {
                EntityUtils.consumeQuietly(httpResponse.getEntity());
                if (logger.isDebugEnabled()) {
                    logger.debug("Got 401 for {}; refreshing the access token and retrying once.", httpRequest.getURI());
                }
                oAuth.updateAccessToken(client);
                httpRequest.removeHeaders("Authorization");
                return doJsonRequest(httpRequest, false);
            }
            if (!isErrorResponse(httpResponse)) {
                requireJsonContentType(httpResponse, httpRequest);
            }
            final HttpEntity entity = httpResponse.getEntity();
            final String body = entity == null ? StringUtil.EMPTY : EntityUtils.toString(entity);
            if (logger.isDebugEnabled()) {
                logger.debug("API's ResponseBody. [url:{}] [body:{}]", httpRequest.getURI().toString(), body);
            }
            if (isErrorResponse(httpResponse)) {
                final int statusCode = httpResponse.getStatusLine().getStatusCode();
                throw new SharePointServerException("Api returned error. code:" + statusCode + "url:" + httpRequest.getURI().toString()
                        + " body:" + describeErrorBody(body), statusCode);
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
        } catch (final SharePointResponseFormatException e) {
            // Not one of the two types SharePointCrawler#doCrawl's retry loop retries - see that
            // exception's javadoc for why a Content-Type mismatch must not be retried at all.
            throw e;
        } catch (final Exception e) {
            throw new SharePointClientException("Request failure. " + e.getMessage(), e);
        }
    }

    /**
     * Renders an error response's body for the exception message, without letting a body that is
     * not JSON prevent that exception from being thrown at all.
     *
     * <p>This used to be an {@code objectMapper.readValue} on the line before the
     * {@link SharePointServerException} was constructed. An error body is not necessarily JSON
     * even when the request asked for JSON - a 503 from IIS, a load balancer or a WAF is typically
     * an HTML page - and parsing one threw from inside {@link #doJsonRequest}'s {@code try}, so
     * what reached {@code SharePointCrawler#doCrawl} was the generic
     * {@code catch (Exception)} branch's {@link SharePointClientException}, whose
     * {@code getStatusCode()} is -1. That loop's {@code == 503} check was then false and the 503
     * backoff never ran. Formatting the body here, after the status code is already in hand,
     * keeps the status on the exception whatever the body turns out to be.
     *
     * @param body the raw response body, possibly blank and possibly not JSON
     * @return the parsed body's map form when it is JSON, the raw body when it is not, and the
     *         string "null" when it is blank - the same rendering a blank body always had
     */
    private String describeErrorBody(final String body) {
        if (StringUtil.isBlank(body)) {
            return String.valueOf((Object) null);
        }
        try {
            return objectMapper.readValue(body, Map.class).toString();
        } catch (final Exception e) {
            if (logger.isDebugEnabled()) {
                logger.debug("An error response body was not JSON; reporting it verbatim.", e);
            }
            return body;
        }
    }

    /**
     * Executes an HTTP request expecting an XML response from SharePoint.
     *
     * @param httpRequest the HTTP request to execute
     * @return an XmlResponse containing the response body and metadata
     * @throws SharePointServerException if the server returns an error response
     * @throws SharePointClientException if there is a client-side error
     */
    protected XmlResponse doXmlRequest(final HttpRequestBase httpRequest) {
        httpRequest.addHeader("Accept", "application/xml; charset=\"UTF-8\"");
        try (CloseableHttpResponse httpResponse = client.execute(httpRequest)) {
            awaitIfServerIsBusy(httpResponse);
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

    /**
     * Determines if the HTTP response indicates an error condition.
     *
     * @param response the HTTP response to check
     * @return true if the response status code is 400 or higher, false otherwise
     */
    protected boolean isErrorResponse(final CloseableHttpResponse response) {
        if (response.getStatusLine().getStatusCode() >= 400) {
            return true;
        }
        return false;
    }

    /**
     * Waits before returning when {@code response} reports SharePoint is busy, via
     * {@value #HEALTH_SCORE_HEADER}.
     *
     * <p>{@link #doJsonRequest} and {@link #doXmlRequest} both call this, but so does
     * {@code GetFile}/{@code GetFile2013}'s own {@code execute()} - they call {@code client}
     * directly instead of going through either of those two methods, so without their own call
     * here every file download would silently skip this wait.
     *
     * <p>Called regardless of whether {@code response} turns out to be an error: a server can
     * report itself busy on a response it still answers successfully, and the crawl benefits from
     * slowing down either way.
     *
     * @param response the response to inspect
     */
    protected void awaitIfServerIsBusy(final CloseableHttpResponse response) {
        final Header header = response.getFirstHeader(HEALTH_SCORE_HEADER);
        if (header == null || StringUtil.isBlank(header.getValue())) {
            return;
        }
        final int score;
        try {
            score = Integer.parseInt(header.getValue().trim());
        } catch (final NumberFormatException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Non-numeric {}: {}", HEALTH_SCORE_HEADER, header.getValue(), e);
            }
            return;
        }
        if (score <= HEALTH_SCORE_BUSY_THRESHOLD) {
            return;
        }
        // The first busy score (threshold + 1) uses attempt 0, the base delay step, not attempt 1
        // (which would skip straight to the first doubling): subtracting 1 here is what makes
        // that so - without it, the very first busy signal would already double the base delay.
        final int attempt = score - HEALTH_SCORE_BUSY_THRESHOLD - 1;
        if (logger.isDebugEnabled()) {
            logger.debug("{} is {}; backing off.", HEALTH_SCORE_HEADER, score);
        }
        backoff.await(attempt);
    }

    /**
     * Rejects a non-error response whose {@code Content-Type} does not look like JSON.
     *
     * <p>An on-premises server with JSON Light OData disabled answers a JSON request with Atom XML
     * instead, 200 and all: {@code objectMapper.readValue} chokes on the leading {@code '<'} with
     * {@code JsonParseException: Unexpected character}, a message that says nothing about what
     * actually went wrong, wrapped in a {@code SharePointClientException} - one of the two types
     * {@code SharePointCrawler#doCrawl} retries, so a deterministic configuration mismatch used to
     * be retried to exhaustion before being reported this unhelpfully. Checking the declared
     * {@code Content-Type} here catches it before the parser ever runs, with a message that
     * names the actual cause.
     *
     * @param httpResponse the response to inspect; must not be an error response
     * @param httpRequest the request that produced it, for the exception message
     * @throws SharePointResponseFormatException if the response is not declared as JSON
     */
    private void requireJsonContentType(final CloseableHttpResponse httpResponse, final HttpRequestBase httpRequest) {
        final Header contentType = httpResponse.getFirstHeader("Content-Type");
        final String value = contentType == null ? null : contentType.getValue();
        if (value != null && value.toLowerCase(Locale.ROOT).contains("json")) {
            return;
        }
        throw new SharePointResponseFormatException("SharePoint responded with Content-Type '" + value + "' to a request sent with"
                + " Accept: application/json, for " + httpRequest.getURI()
                + ". This usually means JSON Light OData support is disabled on the server, which makes it answer with Atom XML"
                + " instead of JSON. Enable JSON Light or check the endpoint's OData configuration.");
    }

    /**
     * URL-encodes a relative URL path by encoding each path segment separately.
     * This method properly handles path separators and converts + to %20.
     *
     * <p>Each segment also goes through {@link #escapeODataLiteral}, so the result is only valid
     * inside an OData string literal - every current caller interpolates it into one, such as
     * {@code GetFolderByServerRelativePath(decodedUrl='...')}. Placing it anywhere else leaves any
     * apostrophe in the path doubled. Use {@code URLEncoder} directly for that.
     *
     * @param url the relative URL to encode
     * @return the URL-encoded path, escaped for an OData string literal, or null if input is null
     */
    protected String encodeRelativeUrl(final String url) {
        if (url == null) {
            return null;
        }
        return StreamUtil.stream(StringUtils.splitPreserveAllTokens(url, '/'))
                .get(stream -> stream.map(s -> URLEncoder.encode(escapeODataLiteral(s), StandardCharsets.UTF_8))
                        .collect(Collectors.joining("/")))
                .replace("+", "%20");
    }

    /**
     * Doubles the apostrophes in a value that is about to be placed inside an OData string
     * literal.
     *
     * <p>OData ends a string literal at the first apostrophe, so a name containing one produces a
     * malformed expression and the server answers 400. Doubling is the escape the protocol
     * defines. It has to happen before percent-encoding, which is a separate layer: the server
     * percent-decodes the path before the OData expression is parsed, so encoding alone leaves the
     * literal broken.
     *
     * @param value the raw value, may be null
     * @return the value with every apostrophe doubled, or null if the value was null
     */
    protected String escapeODataLiteral(final String value) {
        if (value == null) {
            return null;
        }
        return value.replace("'", "''");
    }

    /**
     * Rejects a value that is about to be interpolated into an OData {@code guid'...'} literal
     * unless it actually looks like a GUID.
     *
     * <p>A list id is never supposed to contain an apostrophe, so doubling is not the right
     * defense the way it is for a path or a title: a value that fails this check is not a
     * legitimate id that happens to need escaping, it is bad input. Rejecting it up front avoids
     * both the malformed OData expression and any other character that would otherwise reach the
     * request unescaped.
     *
     * <p>This method only validates and throws; whether that failure ends up loud or quiet
     * depends entirely on where it is called from, not on this method. Called from
     * {@code SharePointCrawler#validate}, before any crawl target exists, the
     * {@link ValidationException} it throws runs outside {@code SharePointCrawler.doCrawl}'s
     * retry handling entirely and fails the job immediately - that is the only call site that
     * actually makes a malformed {@code site.list_id} loud. Called from {@code GetList} or
     * {@code GetList2013} while a crawl is already in progress, the same exception instead falls
     * into {@code doCrawl}'s generic {@code catch (Exception e)}, which wraps it in a
     * {@code DataStoreCrawlingException} with {@code abort} left {@code false} - the crawl for
     * that target ends, but the job is reported as a warning, not a failure. Static so a caller in
     * a different package ({@code SharePointCrawler}) can reach it without a second copy of
     * {@code GUID_PATTERN}.
     *
     * @param value the value to check
     * @param paramName the name to use in the exception message if validation fails
     * @return {@code value}, unchanged
     * @throws ValidationException if {@code value} is not a GUID
     */
    public static String requireGuidLiteral(final String value, final String paramName) {
        if (value == null || !GUID_PATTERN.matcher(value).matches()) {
            throw new ValidationException("[" + paramName + "] must be a GUID. value:" + value);
        }
        return value;
    }

    /**
     * Represents a JSON response from a SharePoint API call.
     * Contains the raw response body, parsed JSON map, and HTTP status code.
     */
    public static class JsonResponse {
        private final String body;
        private final Map<String, Object> bodyMap;
        private final int statusCode;

        /**
         * Constructs a new JsonResponse.
         *
         * @param body the raw response body as a string
         * @param bodyMap the parsed JSON response as a map
         * @param statusCode the HTTP status code
         */
        private JsonResponse(final String body, final Map<String, Object> bodyMap, final int statusCode) {
            this.body = body;
            this.bodyMap = bodyMap;
            this.statusCode = statusCode;
        }

        /**
         * Returns the raw response body as a string.
         *
         * @return the raw response body
         */
        public String getBody() {
            return body;
        }

        /**
         * Returns the parsed JSON response as a map.
         *
         * @return the parsed JSON response map
         */
        public Map<String, Object> getBodyAsMap() {
            return bodyMap;
        }

        /**
         * Determines if this response indicates an error condition.
         *
         * @return true if the status code is 400 or higher, false otherwise
         */
        public boolean isErrorResponse() {
            return statusCode >= 400;
        }
    }

    /**
     * Represents an XML response from a SharePoint API call.
     * Contains the raw response body and HTTP status code, with utilities for XML parsing.
     */
    public static class XmlResponse {
        private final String body;
        private final int statusCode;

        /**
         * Constructs a new XmlResponse.
         *
         * @param body the raw XML response body as a string
         * @param statusCode the HTTP status code
         */
        private XmlResponse(final String body, final int statusCode) {
            this.body = body;
            this.statusCode = statusCode;
        }

        /**
         * Returns the raw XML response body as a string.
         *
         * @return the raw XML response body
         */
        public String getBody() {
            return body;
        }

        /**
         * Determines if this response indicates an error condition.
         *
         * @return true if the status code is 400 or higher, false otherwise
         */
        public boolean isErrorResponse() {
            return statusCode >= 400;
        }

        /**
         * Parses the XML response body using the provided SAX handler.
         *
         * @param handler the SAX handler to process the XML content
         * @throws SharePointClientException if XML parsing fails
         */
        public void parseXml(final DefaultHandler handler) {
            parseXml(body, handler);
        }

        /**
         * Static utility method to parse XML content using a SAX parser with security features enabled.
         * This method configures the parser to prevent XXE attacks and other XML security vulnerabilities.
         *
         * @param xml the XML content to parse
         * @param handler the SAX handler to process the XML content
         * @throws SharePointClientException if XML parsing fails
         */
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
