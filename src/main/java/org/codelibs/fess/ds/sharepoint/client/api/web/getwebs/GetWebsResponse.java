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
package org.codelibs.fess.ds.sharepoint.client.api.web.getwebs;

import java.util.List;

import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;

/**
 * Response class for the SharePoint get subsites API ({@code _api/web/webinfos}).
 */
public class GetWebsResponse implements SharePointApiResponse {
    /** The site's direct child sites. */
    protected final List<SubSite> subSites;

    /**
     * Constructor.
     *
     * @param subSites the site's direct child sites
     */
    public GetWebsResponse(final List<SubSite> subSites) {
        this.subSites = subSites;
    }

    /**
     * Gets the site's direct child sites.
     *
     * @return the child sites
     */
    public List<SubSite> getSubSites() {
        return subSites;
    }

    /**
     * Represents one direct child site, as reported by {@code SP.WebInformation}.
     *
     * <p>{@code SP.WebInformation} has no absolute {@code Url} property - only
     * {@link #getServerRelativeUrl()} identifies where the child site actually is.
     */
    public static class SubSite {
        private final String id;
        private final String title;
        private final String serverRelativeUrl;

        /**
         * Constructor.
         *
         * @param id the child site's GUID
         * @param title the child site's title
         * @param serverRelativeUrl the child site's server-relative URL
         */
        public SubSite(final String id, final String title, final String serverRelativeUrl) {
            this.id = id;
            this.title = title;
            this.serverRelativeUrl = serverRelativeUrl;
        }

        /**
         * Gets the child site's GUID.
         *
         * @return the child site's GUID
         */
        public String getId() {
            return id;
        }

        /**
         * Gets the child site's title.
         *
         * @return the child site's title
         */
        public String getTitle() {
            return title;
        }

        /**
         * Gets the child site's server-relative URL.
         *
         * @return the child site's server-relative URL
         */
        public String getServerRelativeUrl() {
            return serverRelativeUrl;
        }
    }
}
