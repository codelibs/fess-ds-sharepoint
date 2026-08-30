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
package org.codelibs.fess.ds.sharepoint.crawl.list;

import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fess.crawler.filter.UrlFilter;
import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemRoleResponse;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitems.GetListItemsResponse;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlists.GetListResponse;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlists.GetListsResponse;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointServerException;
import org.codelibs.fess.ds.sharepoint.crawl.SharePointCrawl;
import org.codelibs.fess.helper.CrawlerStatsHelper.StatsKeyObject;
import org.codelibs.fess.opensearch.config.exentity.DataConfig;

/**
 * Crawler implementation for SharePoint lists.
 * This class handles crawling SharePoint lists by retrieving list metadata
 * and queuing individual items and attachments for detailed crawling.
 *
 * <p>The crawler paginates through list items, applying role-based access control
 * and field filtering, then creates specific crawl tasks for each item and its attachments.</p>
 *
 * @see SharePointCrawl
 * @see ItemCrawl
 * @see ItemAttachmentsCrawl
 */
public class ListCrawl extends SharePointCrawl {
    /** Logger for list crawling operations */
    private static final Logger logger = LogManager.getLogger(ListCrawl.class);

    /**
     * Upper bound on the number of pages of list items this crawl may fetch.
     *
     * <p>The listing ends when a page comes back empty. A server that ignores the paging token
     * answers the same first page instead, so without this bound the loop would neither finish
     * nor stay within memory - it keeps offering the same items to the crawling queue. This is
     * the same hazard {@link org.codelibs.fess.ds.sharepoint.crawl.doclib.FolderCrawl} guards
     * against for the files inside one folder.
     *
     * <p>Unlike that guard, this one does not cap an item count. {@link
     * org.codelibs.fess.ds.sharepoint.client.api.list.getlistitems.GetListItems}'s paging token
     * advances by item ID ({@code p_ID}), not by an offset into the list, so this bound caps an
     * ID range instead: at the default 100 items per request it is an ID range of 1,000,000, and
     * raising {@code list.items.number_per_page} raises the covered ID range in proportion. A
     * real list's item IDs only grow, including past deleted items, so a long-lived list can run
     * past this ceiling with far fewer live items than the ceiling number suggests - a 3,000-item
     * list whose IDs have reached 50,000 after years of adds and deletes is nowhere near it. The
     * value here gives that kind of list 20x headroom over that example while still bounding a
     * runaway server, one that never stops paging, to a finite number of requests (at most 10,000
     * against the default page size).
     */
    private static final int MAX_PAGES = 10000;

    /** SharePoint list identifier */
    private final String id;
    /** Display name of the SharePoint list */
    private final String listName;
    /** Number of items to retrieve per API call */
    private final int numberPerPage;
    /** Cache for SharePoint group information to avoid repeated API calls */
    private final Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache;
    /** Flag indicating if items should be treated as subpages */
    private final Boolean isSubPage;
    /** Flag to skip role-based access control processing */
    private final Boolean skipRole;
    /** Fields to include in content extraction for list items */
    private final List<String> includeFields;
    /** Fields to exclude from content extraction for list items */
    private final List<String> excludeFields;
    /** Whether an attachment's content extraction failure is logged instead of failing its crawl target */
    private final boolean ignoreError;
    /** The name of the extractor component used to extract an attachment's content */
    private final String extractorName;
    /** Regular expressions an attachment's MIME type must match at least one of to be crawled */
    private final String[] supportedMimeTypes;
    /** The maximum attachment size in bytes, or a negative number for no limit */
    private final long maxContentLength;
    /** The include_pattern/exclude_pattern filter built once for the whole crawl, or null */
    private final UrlFilter urlFilter;

    /**
     * Constructs a new ListCrawl instance for crawling a SharePoint list.
     *
     * @param client SharePoint client for API operations
     * @param id unique identifier of the SharePoint list
     * @param listName display name of the SharePoint list
     * @param numberPerPage number of items to retrieve per API call for pagination
     * @param sharePointGroupCache cache for SharePoint group information
     * @param isSubPage flag indicating if items should be treated as subpages
     * @param skipRole flag to skip role-based access control processing
     * @param includeFields list of field names to include in content extraction
     * @param excludeFields list of field name patterns to exclude from content extraction
     * @param ignoreError whether an attachment's content extraction failure is logged instead of
     *            failing its crawl target
     * @param extractorName the name of the extractor component used to extract an attachment's
     *            content
     * @param supportedMimeTypes regular expressions an attachment's MIME type must match at least
     *            one of to be crawled
     * @param maxContentLength the maximum attachment size in bytes, or a negative number for no
     *            limit
     * @param urlFilter the include_pattern/exclude_pattern filter built once for the whole crawl,
     *            or null - passed on to each item, not to its attachments
     */
    public ListCrawl(final SharePointClient client, final String id, final String listName, final int numberPerPage,
            final Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache, final boolean isSubPage,
            final boolean skipRole, final List<String> includeFields, final List<String> excludeFields, final boolean ignoreError,
            final String extractorName, final String[] supportedMimeTypes, final long maxContentLength, final UrlFilter urlFilter) {
        super(client);
        this.id = id;
        this.listName = listName;
        this.numberPerPage = numberPerPage;
        this.sharePointGroupCache = sharePointGroupCache;
        this.isSubPage = isSubPage;
        this.skipRole = skipRole;
        this.includeFields = includeFields;
        this.excludeFields = excludeFields;
        this.ignoreError = ignoreError;
        this.extractorName = extractorName;
        this.supportedMimeTypes = supportedMimeTypes;
        this.maxContentLength = maxContentLength;
        this.urlFilter = urlFilter;
        statsKey = new StatsKeyObject("list#" + listName + ":" + id);
    }

    /**
     * Performs the crawling of the SharePoint list.
     * Retrieves list metadata, paginates through all list items,
     * and queues individual item crawl tasks for processing.
     *
     * @param dataConfig data source configuration
     * @param crawlingQueue queue for additional crawl tasks (items and attachments)
     * @return null (this crawler only queues other tasks, doesn't create documents directly)
     */
    @Override
    public Map<String, Object> doCrawl(final DataConfig dataConfig, final Queue<SharePointCrawl> crawlingQueue) {
        if (logger.isInfoEnabled()) {
            logger.info("[Crawling List] [id:{}] [listName:{}]", id, listName);
        }

        final GetListResponse getListResponse = client.api().list().getList().setListId(id).setListName(listName).execute();
        final GetListsResponse.SharePointList sharePointList = getListResponse.getList();
        final String listId = sharePointList.getId();
        final String listName = sharePointList.getListName();
        int start = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            GetListItemsResponse getListItemsResponse;
            if (listId == null) {
                return null;
            }
            try {
                getListItemsResponse = client.api()
                        .list()
                        .getListItems()
                        .setListId(listId)
                        .setSubPage(isSubPage)
                        .setNum(numberPerPage)
                        .setStart(start)
                        .execute();
            } catch (final SharePointServerException e) {
                if (e.getStatusCode() != 400) {
                    throw e;
                }
                getListItemsResponse = client.api()
                        .list()
                        .getListItems()
                        .setListId(listId)
                        .setSubPage(true)
                        .setNum(numberPerPage)
                        .setStart(start)
                        .execute();
            }
            if (getListItemsResponse.getListItems().isEmpty()) {
                break;
            }
            start += numberPerPage;
            getListItemsResponse.getListItems().forEach(item -> {
                if (item.getTitle().startsWith("$Resources")) {
                    return;
                }

                final List<String> roles = getItemRoles(listId, item.getId(), sharePointGroupCache, skipRole);
                crawlingQueue.offer(new ItemCrawl(client, listId, listName, item.getId(), item.getCreated(), item.getModified(), roles,
                        isSubPage, includeFields, excludeFields, urlFilter));
                if (item.hasAttachments()) {
                    crawlingQueue.offer(new ItemAttachmentsCrawl(client, listId, listName, item.getId(), item.getCreated(),
                            item.getModified(), roles, ignoreError, extractorName, supportedMimeTypes, maxContentLength));
                }
            });
            if (page == MAX_PAGES - 1) {
                // "Pages" here is really an item-ID range: GetListItems's paging token advances
                // by item ID (p_ID), not by an offset, so this stops at an ID ceiling rather than
                // an item count. Truncating here is not counted as a crawl failure, so the
                // stale-document cleanup still runs and documents past the bound can be removed
                // from the index.
                logger.warn("Stopped listing the items of list {} after {} pages; the listing may be truncated.", listName, MAX_PAGES);
            }
        }
        return null;
    }
}
