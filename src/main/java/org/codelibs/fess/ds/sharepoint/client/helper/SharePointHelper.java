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
package org.codelibs.fess.ds.sharepoint.client.helper;

import java.net.MalformedURLException;
import java.net.URL;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fess.ds.sharepoint.client.SharePointClient;

/**
 * Helper class for SharePoint operations.
 */
public class SharePointHelper {
    private static final Logger logger = LogManager.getLogger(SharePointHelper.class);

    private final SharePointClient client;

    private final boolean verson2013;

    /**
     * Creates a new SharePointHelper instance.
     *
     * @param client the SharePoint client
     * @param verson2013 true if using SharePoint 2013
     */
    public SharePointHelper(final SharePointClient client, final boolean verson2013) {
        this.client = client;
        this.verson2013 = verson2013;
    }

    /**
     * Gets the hostname from the SharePoint site URL.
     *
     * @return the hostname
     */
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
