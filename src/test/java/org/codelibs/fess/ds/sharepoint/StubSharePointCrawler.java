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

    StubSharePointCrawler(final List<Map<String, Object>> results) {
        super(stubConfig());
        this.pending = new ArrayList<>(results);
    }

    private static CrawlerConfig stubConfig() {
        final CrawlerConfig config = new CrawlerConfig();
        config.setUrl("http://localhost/");
        config.setSiteName("test");
        return config;
    }

    @Override
    public boolean hasCrawlTarget() {
        return !pending.isEmpty();
    }

    @Override
    public Pair<Map<String, Object>, StatsKeyObject> doCrawl(final DataConfig dataConfig) {
        if (pending.isEmpty()) {
            return null;
        }
        return new Pair<>(pending.remove(0), new StatsKeyObject("stub"));
    }
}
