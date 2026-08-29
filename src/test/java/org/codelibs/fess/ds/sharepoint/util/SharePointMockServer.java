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
package org.codelibs.fess.ds.sharepoint.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.UriCompliance;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;

/**
 * Embedded Jetty server that serves canned SharePoint REST responses.
 *
 * <p>Follows the fess-crawler {@code CrawlerWebServer} convention of using an
 * embedded Jetty rather than a third-party mocking framework. Unlike that class
 * this one routes by request path instead of serving a document root, so it uses
 * the Jetty 12 core {@code Handler.Abstract} API and needs no servlet layer.
 */
public class SharePointMockServer implements AutoCloseable {

    private final Map<String, StubResponse> stubs = new ConcurrentHashMap<>();
    private final Map<String, Queue<StubResponse>> onceStubs = new ConcurrentHashMap<>();
    private final Map<String, String> globalHeaders = new ConcurrentHashMap<>();
    private final List<RecordedRequest> recordedRequests = new CopyOnWriteArrayList<>();

    private Server server;

    /**
     * Registers a fixture loaded from the classpath, served with HTTP 200.
     *
     * @param path the request path without query string, e.g. {@code /sites/test/_api/lists}
     * @param contentType the Content-Type header to send
     * @param fixtureClasspath classpath location, e.g. {@code fixtures/modern/lists.json}
     * @return this instance for chaining
     */
    public SharePointMockServer onPath(final String path, final String contentType, final String fixtureClasspath) {
        stubs.put(path, new StubResponse(200, withUtf8Charset(contentType), readClasspath(fixtureClasspath)));
        return this;
    }

    /**
     * Registers a fixed status and body for a path.
     *
     * @param path the request path without query string
     * @param status the HTTP status to return
     * @param contentType the Content-Type header to send
     * @param body the response body
     * @return this instance for chaining
     */
    public SharePointMockServer onPathStatus(final String path, final int status, final String contentType, final String body) {
        stubs.put(path, new StubResponse(status, withUtf8Charset(contentType), body));
        return this;
    }

    /**
     * Registers a body served only for one exact raw query string on a path, with HTTP 200.
     *
     * <p>A stub registered with {@link #onPath} or {@link #onPathStatus} answers every query on
     * its path, which is enough for an API that fetches a resource once but not for one that
     * pages: a paging loop that keeps receiving a full page never reaches its last page. Register
     * one of these per page, and the empty page that ends the loop.
     *
     * <p>The query is matched against {@link RecordedRequest#getQuery()}, i.e. the raw wire form
     * with the OData dollar signs still percent-encoded, e.g.
     * {@code %24skip=0&%24top=200}. A path stub still answers any query this method has not
     * claimed.
     *
     * @param path the request path without query string
     * @param query the exact raw query string to match
     * @param contentType the Content-Type header to send
     * @param body the response body
     * @return this instance for chaining
     */
    public SharePointMockServer onPathQuery(final String path, final String query, final String contentType, final String body) {
        stubs.put(path + "?" + query, new StubResponse(200, withUtf8Charset(contentType), body));
        return this;
    }

    /**
     * Registers a response served once, on the next request to a path, ahead of every standing
     * stub for it.
     *
     * <p>Lets a test express a transient failure: register the error here and the real body with
     * {@link #onPath} or {@link #onPathStatus}, and the first call fails while the retry
     * succeeds. Repeated calls queue up and are served in registration order; once the queue is
     * drained the standing stubs answer as usual. Matching ignores the query string, so this
     * claims the next request to the path whatever it asks for.
     *
     * @param path the request path without query string
     * @param status the HTTP status to return on that one request
     * @param contentType the Content-Type header to send
     * @param body the response body
     * @return this instance for chaining
     */
    public SharePointMockServer onPathOnce(final String path, final int status, final String contentType, final String body) {
        onceStubs.computeIfAbsent(path, key -> new ConcurrentLinkedQueue<>())
                .add(new StubResponse(status, withUtf8Charset(contentType), body));
        return this;
    }

    /**
     * Adds a header sent on every response. Used to inject X-SharePointHealthScore.
     *
     * @param name the header name
     * @param value the header value
     * @return this instance for chaining
     */
    public SharePointMockServer withHeader(final String name, final String value) {
        globalHeaders.put(name, value);
        return this;
    }

    /**
     * Starts the server on an ephemeral port.
     *
     * <p>The connector is configured with a relaxed {@link UriCompliance} rather than
     * the bare {@code new Server(0)} default. Jetty 12's default compliance mode
     * ({@code UriCompliance.DEFAULT}) allows no ambiguous URI encodings at all, so it
     * rejects requests like {@code /f/a%25b.txt} (a literal {@code %} in a SharePoint
     * file name) and OData paths like
     * {@code GetFolderByServerRelativeUrl(%27%2Fsites%2F...%27)} with a 400 before
     * {@code StubHandler.handle} ever runs - the request is not even recorded. Real
     * IIS/SharePoint servers accept these wire forms, and the escaping tests this
     * mock exists to support need to see them reach the handler with their raw
     * encoding intact. Do not "simplify" this back to {@code new Server(0)}: doing so
     * silently reintroduces those 400s with an empty request log and nothing to
     * assert on. {@link Request#getHttpURI()}{@code .getPath()} reflects whatever bytes
     * the client actually put on the wire - Jetty does not decode or rewrite it further.
     * That is not necessarily the exact string a test built: Apache HttpClient's own
     * request-line handling can rewrite a URI before sending it (see {@code GetListTest}
     * for a case where an encoded apostrophe in the request path is normalized back to a
     * literal one, and why it happens). This method still records the true wire form,
     * which is what escaping assertions need - just not always the client's original
     * string.
     *
     * @throws Exception if the server fails to start
     */
    public void start() throws Exception {
        server = new Server();
        final HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setUriCompliance(UriCompliance.DEFAULT.with("sharepoint", UriCompliance.Violation.AMBIGUOUS_PATH_ENCODING,
                UriCompliance.Violation.AMBIGUOUS_PATH_SEPARATOR, UriCompliance.Violation.AMBIGUOUS_PATH_SEGMENT));
        final ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
        connector.setPort(0);
        server.addConnector(connector);
        server.setHandler(new StubHandler());
        server.start();
    }

    /**
     * Stops the server and waits for it to finish.
     *
     * @throws Exception if the server fails to stop
     */
    public void stop() throws Exception {
        if (server != null) {
            server.stop();
            server.join();
            server = null;
        }
    }

    @Override
    public void close() throws Exception {
        stop();
    }

    /**
     * Returns the port the server bound to.
     *
     * @return the local port
     * @throws IllegalStateException if the server has not been started
     */
    public int getPort() {
        if (server == null) {
            throw new IllegalStateException("Server is not started.");
        }
        return ((ServerConnector) server.getConnectors()[0]).getLocalPort();
    }

    /**
     * Returns the base URL with a trailing slash, matching what SharePointClient expects.
     *
     * @return e.g. {@code http://localhost:12345/}
     */
    public String getBaseUrl() {
        return "http://localhost:" + getPort() + "/";
    }

    /**
     * Returns the requests received so far, in arrival order.
     *
     * @return the recorded requests
     */
    public List<RecordedRequest> getRecordedRequests() {
        return new ArrayList<>(recordedRequests);
    }

    /**
     * Clears the recorded requests.
     */
    public void clearRecordedRequests() {
        recordedRequests.clear();
    }

    private static String readClasspath(final String location) {
        try (InputStream in = SharePointMockServer.class.getClassLoader().getResourceAsStream(location)) {
            if (in == null) {
                throw new IllegalArgumentException("Fixture not found on classpath: " + location);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to read fixture: " + location, e);
        }
    }

    /**
     * Ensures the given Content-Type declares a charset, defaulting to UTF-8.
     *
     * <p>{@link Content.Sink#write(org.eclipse.jetty.io.Content.Sink, boolean, String, org.eclipse.jetty.util.Callback)}
     * always encodes the body as UTF-8, but a Content-Type header with no charset
     * leaves HttpCore's {@code EntityUtils.toString(HttpEntity)} (used by
     * {@code SharePointApi}) to fall back to its own MIME registry, which defaults
     * XML and plain-text media types to ISO-8859-1. Without this, non-ASCII fixture
     * bodies would decode incorrectly on the client side even though the bytes on
     * the wire are correct.
     *
     * @param contentType the Content-Type value to check
     * @return {@code contentType} unchanged if it already declares a charset, otherwise
     *         {@code contentType} with {@code ; charset=UTF-8} appended
     */
    private static String withUtf8Charset(final String contentType) {
        if (contentType == null || contentType.toLowerCase(Locale.ROOT).contains("charset=")) {
            return contentType;
        }
        return contentType + "; charset=UTF-8";
    }

    private class StubHandler extends Handler.Abstract {
        @Override
        public boolean handle(final Request request, final Response response, final Callback callback) throws Exception {
            final String path = request.getHttpURI().getPath();
            final String query = request.getHttpURI().getQuery();

            final Map<String, String> headers = new LinkedHashMap<>();
            for (final HttpField field : request.getHeaders()) {
                headers.put(field.getName().toLowerCase(Locale.ROOT), field.getValue());
            }
            recordedRequests.add(new RecordedRequest(request.getMethod(), path, query, headers));

            globalHeaders.forEach((name, value) -> response.getHeaders().put(name, value));

            final Queue<StubResponse> once = onceStubs.get(path);
            StubResponse stub = once == null ? null : once.poll();
            if (stub == null && query != null) {
                stub = stubs.get(path + "?" + query);
            }
            if (stub == null) {
                stub = stubs.get(path);
            }
            if (stub == null) {
                response.setStatus(404);
                response.getHeaders().put("Content-Type", "text/plain; charset=UTF-8");
                Content.Sink.write(response, true, "No stub registered for " + path, callback);
                return true;
            }

            response.setStatus(stub.status);
            response.getHeaders().put("Content-Type", stub.contentType);
            Content.Sink.write(response, true, stub.body, callback);
            return true;
        }
    }

    private static class StubResponse {
        private final int status;
        private final String contentType;
        private final String body;

        StubResponse(final int status, final String contentType, final String body) {
            this.status = status;
            this.contentType = contentType;
            this.body = body;
        }
    }

    /** A request received by the mock server. */
    public static class RecordedRequest {
        private final String method;
        private final String path;
        private final String query;
        private final Map<String, String> headers;

        RecordedRequest(final String method, final String path, final String query, final Map<String, String> headers) {
            this.method = method;
            this.path = path;
            this.query = query;
            this.headers = headers;
        }

        /**
         * Returns the HTTP method.
         *
         * @return e.g. {@code GET}
         */
        public String getMethod() {
            return method;
        }

        /**
         * Returns the request path without the query string.
         *
         * @return the path
         */
        public String getPath() {
            return path;
        }

        /**
         * Returns the raw query string.
         *
         * @return the query string, or null if there was none
         */
        public String getQuery() {
            return query;
        }

        /**
         * Returns a request header, case-insensitively.
         *
         * @param name the header name
         * @return the header value, or null if absent
         */
        public String getHeader(final String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }
    }
}
