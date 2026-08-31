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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fess.crawler.filter.UrlFilter;
import org.codelibs.fess.ds.sharepoint.SharePointCrawler;
import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.client.api.doclib.getfolder.GetFolderResponse;
import org.codelibs.fess.ds.sharepoint.client.api.doclib.getfolders.GetFoldersResponse;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemRoleResponse;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlists.GetListsResponse;
import org.codelibs.fess.ds.sharepoint.crawl.doclib.FolderCrawl;
import org.codelibs.fess.ds.sharepoint.crawl.list.ListCrawl;
import org.codelibs.fess.helper.CrawlerStatsHelper.StatsKeyObject;
import org.codelibs.fess.opensearch.config.exentity.DataConfig;

/**
 * Crawler implementation for SharePoint sites.
 * This class handles the top-level crawling of SharePoint sites by discovering
 * and queuing crawl tasks for folders, document libraries, and lists.
 *
 * <p>The crawler applies exclusion filters to skip system folders and lists
 * that should not be indexed, and manages the overall site crawling workflow.</p>
 *
 * @see SharePointCrawl
 * @see FolderCrawl
 * @see ListCrawl
 */
public class SiteCrawl extends SharePointCrawl {
    /** Logger for site crawling operations */
    private static final Logger logger = LogManager.getLogger(SiteCrawl.class);

    /** What the top-level folder listing asks for per request. */
    private static final int PAGE_SIZE = 100;

    /**
     * Upper bound on the number of pages the top-level folder listing may fetch.
     *
     * <p>The listing ends when a page comes back empty or shorter than {@link #PAGE_SIZE}. A
     * server that ignores the skip parameter answers the same full first page instead, so without
     * this bound the loop would neither finish nor stay within memory - the same hazard
     * {@link FolderCrawl} guards against for the folders and files inside one folder, applied here
     * to a site's top-level folders. At {@link #PAGE_SIZE} entries per page it allows 10,000
     * top-level folders, twice SharePoint's own 5,000-item list view threshold.
     */
    private static final int MAX_PAGES = 100;

    /** Crawler configuration containing site settings and filters */
    private final SharePointCrawler.CrawlerConfig config;
    /** Cache for SharePoint group information to optimize role lookups */
    private final Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache;
    /** The include_pattern/exclude_pattern filter built once for the whole crawl, or null */
    private final UrlFilter urlFilter;

    /**
     * Constructs a new SiteCrawl instance for crawling a SharePoint site.
     *
     * @param client SharePoint client for API operations
     * @param config crawler configuration containing site settings and filters
     * @param sharePointGroupCache cache for SharePoint group information
     * @param urlFilter the include_pattern/exclude_pattern filter built once for the whole crawl,
     *            or null
     */
    public SiteCrawl(final SharePointClient client, final SharePointCrawler.CrawlerConfig config,
            final Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache, final UrlFilter urlFilter) {
        super(client);
        this.config = config;

        this.sharePointGroupCache = sharePointGroupCache;
        this.urlFilter = urlFilter;
        statsKey = new StatsKeyObject("site#" + config.getSiteName());
    }

    /**
     * Performs the crawling of the SharePoint site.
     * Discovers folders and lists within the site and queues specific
     * crawl tasks for detailed processing.
     *
     * @param dataConfig data source configuration
     * @param crawlingQueue queue for additional crawl tasks (folders and lists)
     * @return null (this crawler only queues other tasks, doesn't create documents directly)
     */
    @Override
    public Map<String, Object> doCrawl(final DataConfig dataConfig, final Queue<SharePointCrawl> crawlingQueue) {
        if (logger.isInfoEnabled()) {
            logger.info("[Crawling Site] [siteName:{}]", config.getSiteName());
        }
        final Set<String> targetFolderName = new HashSet<>();
        final String siteFolderUrl = "/sites/" + config.getSiteName() + "/";
        int foldersStart = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            final GetFoldersResponse getFoldersResponse = client.api()
                    .doclib()
                    .getFolders()
                    .setServerRelativeUrl(siteFolderUrl)
                    .setStart(foldersStart)
                    .setNum(PAGE_SIZE)
                    .execute();
            final List<GetFolderResponse> folders = getFoldersResponse.getFolders();
            if (folders.isEmpty()) {
                break;
            }
            foldersStart += PAGE_SIZE;
            folders.stream().filter(folder -> !isExcludeFolder(folder.getName())).forEach(folder -> {
                targetFolderName.add(folder.getName());
                crawlingQueue.offer(new FolderCrawl(client, folder.getServerRelativeUrl(), config.isSkipRole(), sharePointGroupCache,
                        config.isIgnoreError(), config.getExtractorName(), config.getSupportedMimeTypes(), config.getMaxContentLength(),
                        urlFilter));
            });
            if (folders.size() < PAGE_SIZE) {
                break;
            }
            if (page == MAX_PAGES - 1) {
                // Truncating here is not counted as a crawl failure, so the stale-document
                // cleanup still runs and documents past the bound can be removed from the index.
                logger.warn("Stopped listing the top-level folders of site {} after {} pages; the listing may be truncated.",
                        config.getSiteName(), MAX_PAGES);
            }
        }
        final GetListsResponse getListsResponse = client.api().list().getLists().execute();
        getListsResponse.getLists()
                .stream()
                .filter(list -> !list.isNoCrawl() && !list.isHidden())
                .filter(list -> !targetFolderName.contains(list.getListName()))
                .filter(list -> !isExcludeList(list.getEntityTypeName()))
                .forEach(list -> crawlingQueue.offer(new ListCrawl(client, list.getId(), list.getListName(),
                        config.getListItemNumPerPages(), sharePointGroupCache, isSubPageList(list.getEntityTypeName()), config.isSkipRole(),
                        config.getListContentIncludeFields(), config.getListContentExcludeFields(), config.isIgnoreError(),
                        config.getExtractorName(), config.getSupportedMimeTypes(), config.getMaxContentLength(), urlFilter)));
        crawlingQueue.offer(new FolderCrawl(client, "/sites/" + config.getSiteName() + "/Shared Documents", false, sharePointGroupCache,
                config.isIgnoreError(), config.getExtractorName(), config.getSupportedMimeTypes(), config.getMaxContentLength(),
                urlFilter));
        return null;
    }

    /**
     * Checks if a list should be excluded from crawling based on its entity type name.
     * Uses both default exclusion patterns and configuration-specific patterns.
     *
     * @param listEntityName the entity type name of the SharePoint list
     * @return true if the list should be excluded from crawling
     */
    private boolean isExcludeList(final String listEntityName) {
        return defaultExcludeListEntityTypes.stream().anyMatch(listEntityName::matches)
                || config.getExcludeList().stream().anyMatch(listEntityName::matches);
    }

    /**
     * Checks if a list contains items that should be treated as subpages.
     * Site pages typically require special handling for URL generation.
     *
     * @param listEntityName the entity type name of the SharePoint list
     * @return true if the list contains subpage items
     */
    private boolean isSubPageList(final String listEntityName) {
        return "SitePages".equals(listEntityName);
    }

    /**
     * Default list entity types to exclude from crawling.
     * These are typically system lists that don't contain user content.
     */
    private static final List<String> defaultExcludeListEntityTypes = Arrays.asList("OData__.*", "TaxonomyHiddenListList", "SiteAssets",
            "Style_x0020_Library", "FormServerTemplates", "UserInfo", "IWConvertedForms", "Shared_x0020_Documents");

    /**
     * Checks if a folder should be excluded from crawling based on its title.
     * Uses both default exclusion patterns and configuration-specific patterns.
     *
     * @param folderTitle the title of the SharePoint folder
     * @return true if the folder should be excluded from crawling
     */
    private boolean isExcludeFolder(final String folderTitle) {
        return defaultExcludeFolderTitle.stream().anyMatch(folderTitle::matches)
                || config.getExcludeFolder().stream().anyMatch(folderTitle::matches);
    }

    /**
     * Default folder titles to exclude from crawling.
     * These are typically system folders that don't contain user content.
     */
    private static final List<String> defaultExcludeFolderTitle = Arrays.asList("IWConvertedForms", "_private", "Style Library",
            "_catalogs", "FormServerTemplates", "SiteAssets", "Lists", "_cts", "_vti_pvt", "SitePages", "images");
}
