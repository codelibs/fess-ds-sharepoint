/*
 * Copyright 2012-2022 CodeLibs Project and the Others.
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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.codelibs.fess.ds.sharepoint.SharePointCrawler;
import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.client.api.doclib.getfolders.GetFoldersResponse;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemRoleResponse;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlists.GetListsResponse;
import org.codelibs.fess.ds.sharepoint.crawl.doclib.FolderCrawl;
import org.codelibs.fess.ds.sharepoint.crawl.list.ListCrawl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SiteCrawl extends SharePointCrawl {
    private static final Logger logger = LoggerFactory.getLogger(SiteCrawl.class);

    private final SharePointCrawler.CrawlerConfig config;
    private final Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache;

    public SiteCrawl(final SharePointClient client, final SharePointCrawler.CrawlerConfig config,
            final Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache) {
        super(client);
        this.config = config;

        this.sharePointGroupCache = sharePointGroupCache;
    }

    @Override
    public Map<String, Object> doCrawl(final Queue<SharePointCrawl> crawlingQueue) {
        if (logger.isInfoEnabled()) {
            logger.info("[Crawling Site] [siteName:{}]", config.getSiteName());
        }
        final Set<String> targetFolderName = new HashSet<>();
        final GetFoldersResponse getFoldersResponse =
                client.api().doclib().getFolders().setServerRelativeUrl("/sites/" + config.getSiteName() + "/").execute();
        getFoldersResponse.getFolders().stream().filter(folder -> !isExcludeFolder(folder.getName())).forEach(folder -> {
            targetFolderName.add(folder.getName());
            crawlingQueue.offer(new FolderCrawl(client, folder.getServerRelativeUrl(), config.isSkipRole(), sharePointGroupCache));
        });
        final GetListsResponse getListsResponse = client.api().list().getLists().execute();
        getListsResponse.getLists().stream().filter(list -> !list.isNoCrawl() && !list.isHidden())
                .filter(list -> !targetFolderName.contains(list.getListName())).filter(list -> !isExcludeList(list.getEntityTypeName()))
                .forEach(list -> crawlingQueue.offer(new ListCrawl(client, list.getId(), list.getListName(),
                        config.getListItemNumPerPages(), sharePointGroupCache, isSubPageList(list.getEntityTypeName()), config.isSkipRole(),
                        config.getListContentIncludeFields(), config.getListContentExcludeFields())));
        crawlingQueue.offer(new FolderCrawl(client, "/sites/" + config.getSiteName() + "/Shared Documents", false, sharePointGroupCache));
        return null;
    }

    private boolean isExcludeList(final String listEntityName) {
        return defaultExcludeListEntityTypes.stream().anyMatch(listEntityName::matches)
                || config.getExcludeList().stream().anyMatch(listEntityName::matches);
    }

    private boolean isSubPageList(final String listEntityName) {
        return "SitePages".equals(listEntityName);
    }

    private static final List<String> defaultExcludeListEntityTypes = Arrays.asList("OData__.*", "TaxonomyHiddenListList", "SiteAssets",
            "Style_x0020_Library", "FormServerTemplates", "UserInfo", "IWConvertedForms", "Shared_x0020_Documents");

    private boolean isExcludeFolder(final String folderTitle) {
        return defaultExcludeFolderTitle.stream().anyMatch(folderTitle::matches)
                || config.getExcludeFolder().stream().anyMatch(folderTitle::matches);
    }

    private static final List<String> defaultExcludeFolderTitle = Arrays.asList("IWConvertedForms", "_private", "Style Library",
            "_catalogs", "FormServerTemplates", "SiteAssets", "Lists", "_cts", "_vti_pvt", "SitePages", "images");
}
