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
     * <p>The listing ends when a page comes back empty, or once {@link
     * #MAX_CONSECUTIVE_STALLED_PAGES} requests in a row fail to advance the paging cursor - see
     * that constant for why one such response is tolerated and retried rather than ending the
     * listing outright. A server that never honors the {@code p_ID} paging token is caught
     * within a handful of requests that way, not by this bound.
     *
     * <p>This bound instead protects against a list that is genuinely huge: with the cursor
     * tracking real item IDs, each loop iteration is one real page or one retry of a stalled
     * one, so this is a true bound on requests. At the default 100 items per request it allows
     * up to 1,000,000 items, and raising {@code list.items.number_per_page} raises that ceiling
     * in proportion. Reaching it still truncates the listing - the warning logged at that point
     * is not counted as a crawl failure, so the stale-document cleanup still runs and documents
     * past the bound can be removed from the index - but doing so now takes a list with that
     * many live items, not merely one whose lowest item ID happens to be high.
     */
    private static final int MAX_PAGES = 10000;

    /**
     * How many consecutive page requests may fail to advance the paging cursor before the
     * listing gives up on the list.
     *
     * <p>A single non-advancing response is tolerated and simply retried with the same cursor
     * rather than treated as proof the server is permanently broken: a stale replica, a cache,
     * or a retried request can repeat a page once and then continue correctly, and
     * re-requesting the same cursor is exactly the retry that recovers from that. Only a run of
     * these in a row - never once advancing after this many attempts - means the server is not
     * honoring {@code p_ID} paging at all, and continuing would just keep re-reading the same
     * items. This keeps a permanently broken server to a handful of requests rather than the
     * {@link #MAX_PAGES} bound.
     */
    private static final int MAX_CONSECUTIVE_STALLED_PAGES = 3;

    /** SharePoint list identifier */
    private final String id;
    /** Display name of the SharePoint list */
    private final String listName;
    /** Number of items to retrieve per API call */
    private final int numberPerPage;
    /** Cache for SharePoint group information to avoid repeated API calls */
    private final Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache;
    /** Cache of each list's DISPLAY_FORM server-relative URL, keyed by listId */
    private final Map<String, String> formsCache;
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
     * @param formsCache cache of each list's DISPLAY_FORM server-relative URL, keyed by listId
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
     *            or null - passed on to each item and to each item's attachments, which are matched
     *            separately because an attachment's URL is not the item's {@code FileRef}
     */
    public ListCrawl(final SharePointClient client, final String id, final String listName, final int numberPerPage,
            final Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache, final Map<String, String> formsCache,
            final boolean isSubPage, final boolean skipRole, final List<String> includeFields, final List<String> excludeFields,
            final boolean ignoreError, final String extractorName, final String[] supportedMimeTypes, final long maxContentLength,
            final UrlFilter urlFilter) {
        super(client);
        this.id = id;
        this.listName = listName;
        this.numberPerPage = numberPerPage;
        this.sharePointGroupCache = sharePointGroupCache;
        this.formsCache = formsCache;
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
        // The item-ID cursor GetListItems's $skiptoken resumes from: SharePoint returns only
        // items whose ID is greater than this value. It is not an offset into the list, so it
        // must advance to the highest item ID actually seen, not by a fixed count per page. 0 is
        // a safe "nothing seen yet" sentinel because SharePoint list item IDs are documented to
        // start at 1: no real item can be mistaken for a page that failed to move the cursor off
        // its initial value.
        int start = 0;
        // How many requests in a row have come back without advancing the cursor - see
        // MAX_CONSECUTIVE_STALLED_PAGES for why a small number of these is tolerated and retried
        // rather than ending the listing on the first one.
        int consecutiveStalledPages = 0;
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
            final List<GetListItemsResponse.ListItem> listItems = getListItemsResponse.getListItems();
            if (listItems.isEmpty()) {
                break;
            }

            // The cursor advances to the largest item ID this page returned - the maximum rather
            // than the last element, since that costs nothing and does not assume the server
            // orders items by ascending ID. An item whose ID does not parse as a number cannot
            // contribute to that maximum, but it is still a real item and is queued below like
            // any other - see the forEach beneath this loop.
            int maxId = start;
            boolean sawNumericId = false;
            for (final GetListItemsResponse.ListItem item : listItems) {
                try {
                    final int itemId = Integer.parseInt(item.getId());
                    sawNumericId = true;
                    if (itemId > maxId) {
                        maxId = itemId;
                    }
                } catch (final NumberFormatException e) {
                    // Handled above via sawNumericId: one bad ID alongside otherwise-good ones
                    // does not invalidate the page, and the item itself is still queued below.
                }
            }

            // Queued unconditionally, before the cursor is evaluated below: whether or not this
            // loop can trust this page to have advanced the cursor, it is still a page of real
            // items SharePoint just returned. An ItemCrawl/ItemAttachmentsCrawl is idempotent -
            // the same item ID produces the same request and the same document - so queueing a
            // repeated page again costs a little duplicate work and can never lose anything.
            // Evaluating the cursor first and skipping this queueing on a stalled or
            // unparseable page, as an earlier version of this method did, can: a single
            // non-advancing response does not prove every item still to come is unreachable, and
            // discarding this page's items on that guess throws real data away for nothing.
            listItems.forEach(item -> {
                if (item.getTitle().startsWith("$Resources")) {
                    return;
                }

                final List<String> roles = getItemRoles(listId, item.getId(), sharePointGroupCache, skipRole);
                crawlingQueue.offer(new ItemCrawl(client, listId, listName, item.getId(), item.getCreated(), item.getModified(), roles,
                        isSubPage, includeFields, excludeFields, formsCache, urlFilter));
                if (item.hasAttachments()) {
                    crawlingQueue
                            .offer(new ItemAttachmentsCrawl(client, listId, listName, item.getId(), item.getCreated(), item.getModified(),
                                    roles, ignoreError, extractorName, supportedMimeTypes, maxContentLength, formsCache, urlFilter));
                }
            });

            if (page == MAX_PAGES - 1) {
                logger.warn("Stopped listing the items of list {} after {} pages; the listing may be truncated.", listName, MAX_PAGES);
            }

            if (!sawNumericId) {
                logger.warn("Stopped listing the items of list {} because none of the {} item(s) on its page had a numeric ID; "
                        + "the server is not honoring $skiptoken paging.", listName, listItems.size());
                break;
            }
            if (maxId <= start) {
                consecutiveStalledPages++;
                if (consecutiveStalledPages >= MAX_CONSECUTIVE_STALLED_PAGES) {
                    logger.warn("Stopped listing the items of list {} after {} consecutive requests that did not advance the paging "
                            + "token; the server is ignoring it.", listName, consecutiveStalledPages);
                    break;
                }
                // Retry with the same cursor rather than treating one non-advancing response as
                // proof the server is permanently broken - see MAX_CONSECUTIVE_STALLED_PAGES.
                continue;
            }
            consecutiveStalledPages = 0;
            start = maxId;
        }
        return null;
    }
}
