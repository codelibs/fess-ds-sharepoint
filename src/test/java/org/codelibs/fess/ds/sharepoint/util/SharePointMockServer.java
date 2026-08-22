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
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
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

    private final Map<String, StubResponse> stubs = new LinkedHashMap<>();
    private final Map<String, String> globalHeaders = new LinkedHashMap<>();
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
        stubs.put(path, new StubResponse(200, contentType, readClasspath(fixtureClasspath)));
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
        stubs.put(path, new StubResponse(status, contentType, body));
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
     * @throws Exception if the server fails to start
     */
    public void start() throws Exception {
        server = new Server(0);
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

            final StubResponse stub = stubs.get(path);
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
