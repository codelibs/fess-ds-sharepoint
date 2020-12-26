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
package org.codelibs.fess.ds.sharepoint.crawl;

import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemRoleResponse;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlists.GetListsResponse;
import org.codelibs.fess.ds.sharepoint.crawl.doclib.FolderCrawl;
import org.codelibs.fess.ds.sharepoint.crawl.list.ListCrawl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Queue;

public class SiteCrawl extends SharePointCrawl {
    private static final Logger logger = LoggerFactory.getLogger(SiteCrawl.class);

    private final String siteName;
    private final int numberPerPage;
    private final Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache;

    public SiteCrawl(SharePointClient client, String siteName, int numberPerPage, Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache) {
        super(client);
        this.siteName = siteName;
        this.numberPerPage = numberPerPage;
        this.sharePointGroupCache = sharePointGroupCache;
    }

    @Override
    public Map<String, Object> doCrawl(final Queue<SharePointCrawl> crawlingQueue) {
        if (logger.isInfoEnabled()) {
            logger.info("[Crawling Site] [siteName:{}]", siteName);
        }
        GetListsResponse getListsResponse = client.api().list().getLists().execute();
        getListsResponse.getLists().stream().forEach(list -> {
            if (list.isNoCrawl()) {
                return;
            }
            crawlingQueue.offer(new ListCrawl(client, list.getId(), null, numberPerPage, sharePointGroupCache, false));
        });
        crawlingQueue.offer(new FolderCrawl(client, "/sites/" + siteName + "/Shared Documents", sharePointGroupCache));
        return null;
    }
}
