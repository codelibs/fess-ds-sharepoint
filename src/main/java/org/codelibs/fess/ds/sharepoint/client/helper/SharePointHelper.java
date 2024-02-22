/*
 * Copyright 2012-2024 CodeLibs Project and the Others.
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

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SharePointHelper {
    private static final Logger logger = LoggerFactory.getLogger(SharePointHelper.class);

    private final SharePointClient client;

    private final boolean verson2013;

    public SharePointHelper(final SharePointClient client, final boolean verson2013) {
        this.client = client;
        this.verson2013 = verson2013;
    }

    public String buildDocLibFileWebLink(final String id, final String serverRelativeUrl, final String parentUrl) {
        if (!verson2013) {
            return client.getSiteUrl() + "Shared%20Documents/Forms/AllItems.aspx?id="
                    + URLEncoder.encode(serverRelativeUrl, StandardCharsets.UTF_8) + "&parent="
                    + URLEncoder.encode(parentUrl, StandardCharsets.UTF_8);
        }
        String docLibName = null;
        final String[] splitArray = serverRelativeUrl.split("/");
        for (int i = 0; i < splitArray.length; i++) {
            final String s = splitArray[i];
            if (!"sites".equals(s)) {
                continue;
            }
            if (i + 2 < splitArray.length) {
                docLibName = splitArray[i + 2];
            }
            break;
        }
        if (docLibName == null) {
            throw new SharePointClientException("Failed to build webUrl. serverRelativeUrl:" + serverRelativeUrl);
        }
        return client.getSiteUrl() + docLibName + "/Forms/AllItems.aspx?ID=id&Source=";
    }

    public String encodeRelativeUrl(final String url) {
        String result = url;
        final String[] array = url.split("/");
        for (final String value : array) {
            result = result.replace(value, URLEncoder.encode(value, StandardCharsets.UTF_8));
        }
        return result.replace("+", "%20");
    }

    public String getHostName() {
        try {
            final URL url = new URL(client.getSiteUrl());
            return url.getHost();
        } catch (final MalformedURLException e) {
            logger.warn("Failed to parser url: {}", client.getSiteUrl(), e);
            return client.getSiteUrl();
        }
    }
}
