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
package org.codelibs.fess.ds.sharepoint.client.helper;

import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class SharePointHelper {
    private static final Logger logger = LoggerFactory.getLogger(SharePointHelper.class);

    private final SharePointClient client;

    public SharePointHelper(final SharePointClient client) {
        this.client = client;
    }

    public String buildDocLibFileWebLink(final String serverRelativeUrl, final String parentUrl) {
        return client.getSiteUrl() + "Shared%20Documents/Forms/AllItems.aspx?id=" +
                URLEncoder.encode(serverRelativeUrl, StandardCharsets.UTF_8) +
                "&parent=" + URLEncoder.encode(parentUrl, StandardCharsets.UTF_8);
    }

    public String encodeRelativeUrl(final String url) {
        String result = url;
        String[] array = url.split("/");
        for (String value: array) {
            result = result.replace(value, URLEncoder.encode(value, StandardCharsets.UTF_8));
        }
        return result.replace("+", "%20");
    }

    public String getHostName() {
        try {
            final URL url = new URL(client.getSiteUrl());
            return url.getHost();
        } catch (MalformedURLException e) {
            logger.warn("Failed to parser url. " + client.getSiteUrl(), e);
            return client.getSiteUrl();
        }
    }
}
