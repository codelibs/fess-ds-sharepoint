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
package org.codelibs.fess.ds.sharepoint.crawl;

import org.codelibs.fess.crawler.filter.UrlFilter;

/**
 * A {@link UrlFilter} a test can construct directly, without the real {@code UrlFilterImpl}'s
 * {@code CrawlerContainer}/{@code UrlFilterService} wiring, which this plugin's own unit test
 * container does not provide (see {@code SharePointCrawler#buildUrlFilter}). Public so it is
 * usable from {@code crawl.doclib}/{@code crawl.list} test classes as well as this package's own.
 */
public final class SimpleUrlFilter implements UrlFilter {

    private final String includePattern;
    private final String excludePattern;

    /**
     * @param includePattern a value must match this to be crawled, or null for no restriction
     * @param excludePattern a value matching this is not crawled, or null for no restriction
     */
    public SimpleUrlFilter(final String includePattern, final String excludePattern) {
        this.includePattern = includePattern;
        this.excludePattern = excludePattern;
    }

    @Override
    public void init(final String sessionId) {
        // no-op: this stub needs no session-scoped backing service
    }

    @Override
    public boolean match(final String url) {
        if (includePattern != null && !url.matches(includePattern)) {
            return false;
        }
        return excludePattern == null || !url.matches(excludePattern);
    }

    @Override
    public void addInclude(final String urlPattern) {
        // no-op: patterns are supplied through the constructor
    }

    @Override
    public void addExclude(final String urlPattern) {
        // no-op: patterns are supplied through the constructor
    }

    @Override
    public void processUrl(final String url) {
        // no-op: this plugin never seeds URLs through a filter
    }

    @Override
    public void clear() {
        // no-op: nothing cached to clear
    }
}
