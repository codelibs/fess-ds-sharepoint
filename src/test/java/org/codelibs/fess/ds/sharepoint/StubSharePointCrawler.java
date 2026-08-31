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
package org.codelibs.fess.ds.sharepoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.codelibs.core.misc.Pair;
import org.codelibs.fess.helper.CrawlerStatsHelper.StatsKeyObject;
import org.codelibs.fess.opensearch.config.exentity.DataConfig;

/**
 * A {@link SharePointCrawler} that replays a canned list of crawl results instead of talking to a
 * SharePoint farm, so a test can drive {@code SharePointDataStore#storeData} deterministically.
 */
class StubSharePointCrawler extends SharePointCrawler {

    private final List<Map<String, Object>> pending;

    private final List<RuntimeException> pendingFailures = new ArrayList<>();

    StubSharePointCrawler(final List<Map<String, Object>> results) {
        super(stubConfig());
        this.pending = new ArrayList<>(results);
    }

    /**
     * Queues one exception to be thrown by the next {@link #doCrawl} call, ahead of the canned
     * results, so a test can replay the way a real crawl loses a target to an exception rather
     * than to exhausted retries.
     *
     * @param failure the exception to throw once
     * @return this instance for chaining
     */
    StubSharePointCrawler failingOnceWith(final RuntimeException failure) {
        pendingFailures.add(failure);
        return this;
    }

    private static CrawlerConfig stubConfig() {
        final CrawlerConfig config = new CrawlerConfig();
        config.setUrl("http://localhost/");
        config.setSiteName("test");
        return config;
    }

    @Override
    public boolean hasCrawlTarget() {
        return !pendingFailures.isEmpty() || !pending.isEmpty();
    }

    @Override
    public Pair<Map<String, Object>, StatsKeyObject> doCrawl(final DataConfig dataConfig) {
        if (!pendingFailures.isEmpty()) {
            throw pendingFailures.remove(0);
        }
        if (pending.isEmpty()) {
            return null;
        }
        return new Pair<>(pending.remove(0), new StatsKeyObject("stub"));
    }
}
