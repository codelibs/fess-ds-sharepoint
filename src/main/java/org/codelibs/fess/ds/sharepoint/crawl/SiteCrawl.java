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

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fess.crawler.filter.UrlFilter;
import org.codelibs.fess.ds.sharepoint.SharePointCrawler;
import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.client.api.doclib.getfolder.GetFolderResponse;
import org.codelibs.fess.ds.sharepoint.client.api.doclib.getfolders.GetFoldersResponse;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemRoleResponse;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlists.GetListsResponse;
import org.codelibs.fess.ds.sharepoint.client.api.web.getwebs.GetWebsResponse;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointServerException;
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

    /**
     * The document library name the top-level folder listing already reports under its real,
     * server-supplied, localized name on every site whose site-root listing reports it at all.
     * Kept as a fallback for a farm whose listing does not report it; skipped when the listing
     * already found a folder by this name, so an English-language site does not crawl its default
     * library twice.
     */
    private static final String DEFAULT_DOCUMENT_LIBRARY_NAME = "Shared Documents";

    /** Crawler configuration containing site settings and filters */
    private final SharePointCrawler.CrawlerConfig config;
    /** Cache for SharePoint group information to optimize role lookups */
    private final Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache;
    /** Cache of each list's DISPLAY_FORM server-relative URL, keyed by listId */
    private final Map<String, String> formsCache;
    /** The include_pattern/exclude_pattern filter built once for the whole crawl, or null */
    private final UrlFilter urlFilter;
    /** How many subsite hops this crawl is from the root site; the root site itself is depth 0. */
    private final int depth;
    /**
     * Every site path already queued for a crawl anywhere in this crawl's recursion tree,
     * normalized, shared across the whole tree the same way {@link #formsCache} is. Seeded with
     * the root site's own path when the tree is built, so a subsite that lists an ancestor (or
     * itself) as a child cannot be queued a second time.
     */
    private final Set<String> visitedSitePaths;

    /**
     * Constructs a new SiteCrawl instance for crawling a SharePoint site.
     *
     * @param client SharePoint client for API operations
     * @param config crawler configuration containing site settings and filters
     * @param sharePointGroupCache cache for SharePoint group information
     * @param formsCache cache of each list's DISPLAY_FORM server-relative URL, keyed by listId
     * @param urlFilter the include_pattern/exclude_pattern filter built once for the whole crawl,
     *            or null
     * @param depth how many subsite hops this crawl is from the root site; the root is 0
     * @param visitedSitePaths every site path already queued in this crawl's recursion tree,
     *            shared across the whole tree
     */
    public SiteCrawl(final SharePointClient client, final SharePointCrawler.CrawlerConfig config,
            final Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache, final Map<String, String> formsCache,
            final UrlFilter urlFilter, final int depth, final Set<String> visitedSitePaths) {
        super(client);
        this.config = config;

        this.sharePointGroupCache = sharePointGroupCache;
        this.formsCache = formsCache;
        this.urlFilter = urlFilter;
        this.depth = depth;
        this.visitedSitePaths = visitedSitePaths;
        statsKey = new StatsKeyObject("site#" + describeSite(client, config));
    }

    /**
     * Names the site for a human reading a log line or a crawl statistic.
     *
     * <p>{@code site.name} is optional once {@code site.path} is set, so it can legitimately be
     * absent; the server-relative path identifies the site just as well and is never blank.
     *
     * <p>{@code config} is shared by every {@link SiteCrawl} in a recursion tree, so it always
     * names the <em>root</em> site - {@code config.getSiteName()}/{@code getSiteRelativePath()}
     * cannot tell a subsite's crawl from the root's. {@code client.getSitePath()} can: it equals
     * {@code config.getSiteRelativePath()} only for the root's own client, so a subsite - built by
     * {@link SharePointClient#forSitePath} on a different path - falls through to its own path
     * instead of reporting the root's name.
     *
     * @param client the client this crawl uses, whose site path identifies which site this is
     * @param config the crawler configuration
     * @return the configured site name for the root site, or the site's own server-relative path
     *         for a subsite (or for the root when no name is set)
     */
    private static String describeSite(final SharePointClient client, final SharePointCrawler.CrawlerConfig config) {
        if (client.getSitePath().equals(config.getSiteRelativePath()) && StringUtils.isNotBlank(config.getSiteName())) {
            return config.getSiteName();
        }
        return client.getSitePath();
    }

    /**
     * Performs the crawling of the SharePoint site.
     * Discovers folders and lists within the site and queues specific
     * crawl tasks for detailed processing.
     *
     * <p>A 403 anywhere in a <em>discovered subsite's</em> own discovery is logged and skipped
     * instead of being allowed to escape. {@code _api/web/webinfos} is not security-trimmed - it
     * names every child site whether or not the crawl account can read it - so on a farm with a
     * permission-partitioned subsite this 403 is the expected answer, not evidence that anything
     * is misconfigured. Letting it escape would send it through
     * {@code SharePointCrawler#doCrawl}'s retry loop, which retries it {@code retry_limit} times
     * and then increments the crawl's failure count, and a non-zero failure count makes
     * {@code SharePointDataStore#store} suppress the stale-document cleanup for the entire data
     * config - so a single unreadable subsite would keep every deleted document in the index on
     * every subsequent run. Skipping here happens before the first retry is spent.
     *
     * <p>Two deliberate limits on what this swallows:
     * <ul>
     * <li><b>Only a discovered subsite ({@code depth > 0}).</b> A 403 on the root site the
     * operator configured is a misconfiguration - a wrong {@code site.path}, or an account
     * without access to the site it was pointed at - and stays a crawl failure, so the operator
     * sees it and the stale-document cleanup is suppressed rather than the whole site being
     * silently dropped from the index.</li>
     * <li><b>Only 403, not 401.</b> A 403 means the request <em>was</em> authenticated and this
     * account simply may not read this web, which is exactly the per-site partition
     * {@code webinfos} exposes. A 401 means the request was not authenticated at all, and since
     * one set of credentials serves every site in the crawl, that is a crawl-wide problem - an
     * expired Kerberos ticket, a rejected password, an OAuth token that could not be refreshed -
     * which every remaining target will hit too. It must stay a failure so it is retried, counted
     * and reported instead of being reported as a per-site permission boundary.</li>
     * </ul>
     *
     * @param dataConfig data source configuration
     * @param crawlingQueue queue for additional crawl tasks (folders and lists)
     * @return null (this crawler only queues other tasks, doesn't create documents directly)
     */
    @Override
    public Map<String, Object> doCrawl(final DataConfig dataConfig, final Queue<SharePointCrawl> crawlingQueue) {
        try {
            return discover(crawlingQueue);
        } catch (final SharePointServerException e) {
            if (depth > 0 && e.getStatusCode() == 403) {
                logger.warn("Skipping subsite {}: the crawl account cannot read it (403).", client.getSitePath());
                return null;
            }
            throw e;
        }
    }

    /**
     * Discovers this site's folders, lists and - when enabled - subsites, queueing a crawl unit
     * for each.
     *
     * @param crawlingQueue queue for additional crawl tasks (folders and lists)
     * @return null (this crawler only queues other tasks, doesn't create documents directly)
     */
    private Map<String, Object> discover(final Queue<SharePointCrawl> crawlingQueue) {
        if (logger.isInfoEnabled()) {
            logger.info("[Crawling Site] [site:{}]", describeSite(client, config));
        }
        final Set<String> targetFolderName = new HashSet<>();
        final String siteFolderUrl = client.getSitePath();
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
                        formsCache, config.isIgnoreError(), config.getExtractorName(), config.getSupportedMimeTypes(),
                        config.getMaxContentLength(), urlFilter));
            });
            if (folders.size() < PAGE_SIZE) {
                break;
            }
            if (page == MAX_PAGES - 1) {
                // Truncating here is not counted as a crawl failure, so the stale-document
                // cleanup still runs and documents past the bound can be removed from the index.
                logger.warn("Stopped listing the top-level folders of site {} after {} pages; the listing may be truncated.",
                        describeSite(client, config), MAX_PAGES);
            }
        }
        final GetListsResponse getListsResponse = client.api().list().getLists().execute();
        getListsResponse.getLists()
                .stream()
                .filter(list -> !list.isNoCrawl() && !list.isHidden())
                .filter(list -> !targetFolderName.contains(list.getListName()))
                .filter(list -> !isExcludeList(list.getEntityTypeName()))
                .forEach(
                        list -> crawlingQueue.offer(new ListCrawl(client, list.getId(), list.getListName(), config.getListItemNumPerPages(),
                                sharePointGroupCache, formsCache, isSubPageList(list.getEntityTypeName()), config.isSkipRole(),
                                config.getListContentIncludeFields(), config.getListContentExcludeFields(), config.isIgnoreError(),
                                config.getExtractorName(), config.getSupportedMimeTypes(), config.getMaxContentLength(), urlFilter)));
        // The top-level folder listing above already reports every document library's root folder
        // under its real, server-supplied name, so on an English-language site this fallback would
        // crawl the default library a second time. It is kept for a farm whose site-root listing
        // does not report it, and skipped when the listing already did.
        if (!targetFolderName.contains(DEFAULT_DOCUMENT_LIBRARY_NAME)) {
            crawlingQueue.offer(new FolderCrawl(client, client.getSitePath() + DEFAULT_DOCUMENT_LIBRARY_NAME, config.isSkipRole(),
                    sharePointGroupCache, formsCache, config.isIgnoreError(), config.getExtractorName(), config.getSupportedMimeTypes(),
                    config.getMaxContentLength(), urlFilter));
        }
        // Disabled by default (config.isCrawlSubsites() == false): with it unset, doCrawl above
        // is the entire method and issues exactly the same requests it always has - including
        // never requesting webinfos at all.
        if (config.isCrawlSubsites() && depth < config.getMaxDepth()) {
            crawlSubsites(crawlingQueue);
        }
        return null;
    }

    /**
     * Discovers this site's direct child sites via {@code _api/web/webinfos} and queues a
     * {@link SiteCrawl} for each one not already visited elsewhere in this crawl's recursion tree.
     *
     * <p>{@code webinfos} is not security-trimmed: it names every child site regardless of
     * whether the crawl account can read it. A 403 on the listing itself - this site's own
     * children cannot be enumerated - is logged and skipped here; a 403 on a listed child, which
     * is the far more common shape of the same permission boundary, arrives later when that
     * child's own {@link SiteCrawl} runs and is skipped there (see
     * {@link #doCrawl(DataConfig, Queue)}). Neither increments the crawl's failure count:
     * counting either would suppress the stale-document cleanup for the entire data config (see
     * {@code SharePointDataStore#store}) over something that is expected, not evidence that
     * anything went wrong.
     *
     * <p>A child is queued only if its server-relative path really lies beneath this site's own.
     * The path comes from the server, and {@link SharePointClient#normalizeSitePath} only fixes
     * up slashes, so without this check a farm answering {@code "sub"}, {@code "/"} or
     * {@code "../.."} would steer the crawl to a site collection this data config was never
     * pointed at. The configured host cannot be changed either way - the site URL is always built
     * by prefixing the configured {@code url} - so this bounds the recursion to what its own
     * contract promises, the site's direct child sites.
     *
     * @param crawlingQueue the queue to add a {@link SiteCrawl} for each newly discovered child to
     */
    private void crawlSubsites(final Queue<SharePointCrawl> crawlingQueue) {
        final GetWebsResponse getWebsResponse;
        try {
            getWebsResponse = client.api().web().getWebs().execute();
        } catch (final SharePointServerException e) {
            if (e.getStatusCode() == 403) {
                logger.warn("Skipping the subsites of {}: the crawl account cannot list them (403).", describeSite(client, config));
                return;
            }
            throw e;
        }
        final String parentPath = client.getSitePath();
        getWebsResponse.getSubSites().forEach(subSite -> {
            final String childPath = SharePointClient.normalizeSitePath(subSite.getServerRelativeUrl(), config.getSiteName());
            if (!isUnderSite(parentPath, childPath)) {
                logger.warn("Skipping subsite {} reported by {}: it does not lie below the site it was discovered from.", childPath,
                        parentPath);
                return;
            }
            if (!visitedSitePaths.add(childPath)) {
                // Already queued elsewhere in this recursion tree - as an ancestor, a sibling
                // already discovered through another path, or (seeded up front) the root itself.
                return;
            }
            crawlingQueue.offer(new SiteCrawl(client.forSitePath(childPath), config, sharePointGroupCache, formsCache, urlFilter, depth + 1,
                    visitedSitePaths));
        });
    }

    /**
     * Whether a discovered child's normalized site path really lies below the site it was
     * discovered from.
     *
     * <p>Both paths are {@link SharePointClient#normalizeSitePath normalized} to start and end
     * with a slash, so a plain prefix test is a whole-segment test: {@code /sites/test/} is a
     * prefix of {@code /sites/test/sub/} but not of {@code /sites/testing/}. A path equal to the
     * parent's is rejected as well - it is the same site, not a child of it. A path containing a
     * {@code ..} segment is rejected outright: it is sent on the wire with the dot segments
     * intact for the server to resolve, so a prefix test alone would not tell where it lands.
     *
     * @param parentPath the normalized site path of the site that reported the child
     * @param childPath the normalized site path reported for the child
     * @return true if {@code childPath} is strictly below {@code parentPath}
     */
    private static boolean isUnderSite(final String parentPath, final String childPath) {
        return childPath.length() > parentPath.length() && childPath.startsWith(parentPath) && !childPath.contains("/../");
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
