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
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.crawl.SharePointCrawl;
import org.codelibs.fess.ds.sharepoint.crawl.file.FileCrawl;
import org.codelibs.fess.ds.sharepoint.util.SharePointMockServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;

/**
 * Exercises the paging cursor of the list item listing, the plugin's primary crawl path.
 *
 * <p>SharePoint's {@code $skiptoken=Paged=TRUE&p_ID={{start}}} is a cursor over item IDs -
 * "return items whose ID is greater than this" - not an offset. The listing ends when a page
 * comes back empty, or once a small number of requests in a row all fail to move the cursor
 * forward. A single non-advancing response alone is retried with the same cursor rather than
 * ending the listing outright - a stale or repeated response does not prove every item still to
 * come is unreachable, and every page fetched, including a repeated or otherwise untrustworthy
 * one, is still queued rather than dropped, since a real item that exists must still be indexed.
 */
public class ListCrawlTest extends UnitDsTestCase {

    private static final String SITE_NAME = "test";

    private static final String JSON = "application/json";

    private static final String LIST_ID = "11111111-1111-1111-1111-111111111111";

    /** Items per request, small enough to keep multi-page fixtures cheap to write. */
    private static final int NUMBER_PER_PAGE = 2;

    /** Must match {@code ListCrawl.MAX_CONSECUTIVE_STALLED_PAGES}, which is private. */
    private static final int MAX_CONSECUTIVE_STALLED_PAGES = 3;

    private static final String LIST_API = "/sites/test/_api/web/lists(guid'" + LIST_ID + "')";

    private static final String ITEMS_API = "/sites/test/_api/Web/Lists(guid'" + LIST_ID + "')/Items";

    private static final String LIST_FIXTURE =
            "{\"Title\":\"Announcements\",\"Id\":\"" + LIST_ID + "\",\"EntityTypeName\":\"AnnouncementsList\"}";

    private static final String EMPTY_PAGE = "{\"value\":[]}";

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_listItemListingStopsWhenTheServerIgnoresThePagingToken() throws Exception {
        // How many times this mock answers with the same never-advancing page before the listing
        // gives up: the first, genuine request plus MAX_CONSECUTIVE_STALLED_PAGES retries that
        // each fail to move the cursor forward.
        final int totalRequests = 1 + MAX_CONSECUTIVE_STALLED_PAGES;
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(LIST_API, 200, JSON, LIST_FIXTURE);
            // A stub registered by path answers every query identically, so this mock ignores the
            // paging token exactly the way a permanently broken SharePoint server does: every
            // request gets the same two items back, regardless of p_ID. The cursor advances past
            // them once, on the first request; every request after that asks for items past that
            // cursor and gets the same two items again, which is retried a small, bounded number
            // of times - tolerating a one-off stale response - before the listing gives up on a
            // server that never advances at all.
            server.onPathStatus(ITEMS_API, 200, JSON, itemPage(NUMBER_PER_PAGE));
            server.start();

            try (SharePointClient client = SharePointClient.builder().setUrl(server.getBaseUrl()).setSite(SITE_NAME).build()) {
                final Queue<SharePointCrawl> crawlingQueue = new ConcurrentLinkedQueue<>();
                new ListCrawl(client, LIST_ID, "Announcements", NUMBER_PER_PAGE, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(),
                        false, true, List.of(), List.of(), true, FileCrawl.DEFAULT_EXTRACTOR_NAME, FileCrawl.DEFAULT_SUPPORTED_MIMETYPES,
                        FileCrawl.DEFAULT_MAX_CONTENT_LENGTH, null).doCrawl(null, crawlingQueue);

                assertEquals("a permanently stalled cursor must be caught within a handful of requests, not run to the page bound",
                        totalRequests,
                        server.getRecordedRequests().stream().filter(request -> ITEMS_API.equals(request.getPath())).count());
                // Every one of those requests' items is queued, including the repeats: a page
                // that cannot be trusted to have advanced the cursor is still a page of real
                // items, and queueing the same item again is a little duplicate work, never lost
                // data.
                assertEquals("every page fetched - the genuine one and every retried repeat - must still be queued",
                        NUMBER_PER_PAGE * totalRequests, crawlingQueue.size());
            }
        }
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_sparseItemIdsAreEachQueuedExactlyOnce() throws Exception {
        // A list whose item IDs have gaps - the shape any list takes on after a deletion. The
        // pre-fix cursor advanced by numberPerPage regardless of the IDs actually returned, so it
        // fell behind the true maximum and re-requested (and re-queued) items an earlier page had
        // already returned - here, that would re-fetch starting at p_ID=2, a query this fixture
        // does not even answer.
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(LIST_API, 200, JSON, LIST_FIXTURE);
            server.onPathQuery(ITEMS_API, itemsQuery(NUMBER_PER_PAGE, 0), JSON, itemPageWithIds(1, 50));
            server.onPathQuery(ITEMS_API, itemsQuery(NUMBER_PER_PAGE, 50), JSON, itemPageWithIds(900, 1500));
            server.onPathQuery(ITEMS_API, itemsQuery(NUMBER_PER_PAGE, 1500), JSON, EMPTY_PAGE);
            server.start();

            try (SharePointClient client = SharePointClient.builder().setUrl(server.getBaseUrl()).setSite(SITE_NAME).build()) {
                final Queue<SharePointCrawl> crawlingQueue = new ConcurrentLinkedQueue<>();
                new ListCrawl(client, LIST_ID, "Announcements", NUMBER_PER_PAGE, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(),
                        false, true, List.of(), List.of(), true, FileCrawl.DEFAULT_EXTRACTOR_NAME, FileCrawl.DEFAULT_SUPPORTED_MIMETYPES,
                        FileCrawl.DEFAULT_MAX_CONTENT_LENGTH, null).doCrawl(null, crawlingQueue);

                assertEquals("every item must be queued exactly once, none skipped and none repeated", List.of("1", "50", "900", "1500"),
                        queuedItemIds(crawlingQueue));
                assertEquals("two real pages plus the empty page that ends the listing", 3,
                        server.getRecordedRequests().stream().filter(request -> ITEMS_API.equals(request.getPath())).count());
            }
        }
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_itemIdsStartingWellAboveNumberPerPageCostOnlyOnePage() throws Exception {
        // A list that was recreated, bulk-imported, or heavily churned can have item IDs starting
        // far above 1. The pre-fix cursor always started at 0 and only ever advanced by
        // numberPerPage, so it spent firstId / numberPerPage pages re-reading the same first page
        // before it caught up - here, that would mean 2500 wasted requests before reaching p_ID
        // 5000, none of which this fixture answers.
        final int firstId = 5000;
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(LIST_API, 200, JSON, LIST_FIXTURE);
            server.onPathQuery(ITEMS_API, itemsQuery(NUMBER_PER_PAGE, 0), JSON, itemPageWithIds(firstId, firstId + 1));
            server.onPathQuery(ITEMS_API, itemsQuery(NUMBER_PER_PAGE, firstId + 1), JSON, EMPTY_PAGE);
            server.start();

            try (SharePointClient client = SharePointClient.builder().setUrl(server.getBaseUrl()).setSite(SITE_NAME).build()) {
                final Queue<SharePointCrawl> crawlingQueue = new ConcurrentLinkedQueue<>();
                new ListCrawl(client, LIST_ID, "Announcements", NUMBER_PER_PAGE, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(),
                        false, true, List.of(), List.of(), true, FileCrawl.DEFAULT_EXTRACTOR_NAME, FileCrawl.DEFAULT_SUPPORTED_MIMETYPES,
                        FileCrawl.DEFAULT_MAX_CONTENT_LENGTH, null).doCrawl(null, crawlingQueue);

                assertEquals("both items must be queued exactly once", List.of(String.valueOf(firstId), String.valueOf(firstId + 1)),
                        queuedItemIds(crawlingQueue));
                assertEquals("one real page plus the empty page that ends it - not firstId / numberPerPage wasted pages first", 2,
                        server.getRecordedRequests().stream().filter(request -> ITEMS_API.equals(request.getPath())).count());
            }
        }
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_numberPerPageOfOneQueuesEachSparseItemExactlyOnce() throws Exception {
        // list.items.number_per_page defaults to 100 but is user-configurable down to 1. The
        // cursor logic must not assume a page ever holds more than one item.
        final int onePerPage = 1;
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(LIST_API, 200, JSON, LIST_FIXTURE);
            server.onPathQuery(ITEMS_API, itemsQuery(onePerPage, 0), JSON, itemPageWithIds(3));
            server.onPathQuery(ITEMS_API, itemsQuery(onePerPage, 3), JSON, itemPageWithIds(7));
            server.onPathQuery(ITEMS_API, itemsQuery(onePerPage, 7), JSON, itemPageWithIds(42));
            server.onPathQuery(ITEMS_API, itemsQuery(onePerPage, 42), JSON, EMPTY_PAGE);
            server.start();

            try (SharePointClient client = SharePointClient.builder().setUrl(server.getBaseUrl()).setSite(SITE_NAME).build()) {
                final Queue<SharePointCrawl> crawlingQueue = new ConcurrentLinkedQueue<>();
                new ListCrawl(client, LIST_ID, "Announcements", onePerPage, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), false,
                        true, List.of(), List.of(), true, FileCrawl.DEFAULT_EXTRACTOR_NAME, FileCrawl.DEFAULT_SUPPORTED_MIMETYPES,
                        FileCrawl.DEFAULT_MAX_CONTENT_LENGTH, null).doCrawl(null, crawlingQueue);

                assertEquals("each item queued exactly once, one item per page", List.of("3", "7", "42"), queuedItemIds(crawlingQueue));
                assertEquals("three real pages plus the empty page that ends the listing", 4,
                        server.getRecordedRequests().stream().filter(request -> ITEMS_API.equals(request.getPath())).count());
            }
        }
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_numberPerPageLargerThanTheListStillTerminatesOnTheEmptyPage() throws Exception {
        // list.items.number_per_page defaults to 100; a list with fewer live items than that must
        // still stop after the one page that returns them, on the following empty page - not loop,
        // and not misjudge the short page itself as a stall.
        final int numberPerPage = 100;
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(LIST_API, 200, JSON, LIST_FIXTURE);
            server.onPathQuery(ITEMS_API, itemsQuery(numberPerPage, 0), JSON, itemPageWithIds(10, 20));
            server.onPathQuery(ITEMS_API, itemsQuery(numberPerPage, 20), JSON, EMPTY_PAGE);
            server.start();

            try (SharePointClient client = SharePointClient.builder().setUrl(server.getBaseUrl()).setSite(SITE_NAME).build()) {
                final Queue<SharePointCrawl> crawlingQueue = new ConcurrentLinkedQueue<>();
                new ListCrawl(client, LIST_ID, "Announcements", numberPerPage, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), false,
                        true, List.of(), List.of(), true, FileCrawl.DEFAULT_EXTRACTOR_NAME, FileCrawl.DEFAULT_SUPPORTED_MIMETYPES,
                        FileCrawl.DEFAULT_MAX_CONTENT_LENGTH, null).doCrawl(null, crawlingQueue);

                assertEquals("both items must be queued exactly once", List.of("10", "20"), queuedItemIds(crawlingQueue));
                assertEquals("one short page plus the empty page that ends it", 2,
                        server.getRecordedRequests().stream().filter(request -> ITEMS_API.equals(request.getPath())).count());
            }
        }
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_nonNumericItemIdsLogAndStopInsteadOfLooping() throws Exception {
        // p_ID paging only means anything if item IDs are numeric. A server returning a
        // non-numeric ID is not honoring it, so the listing must stop paging further rather than
        // running to the page bound the way the ignored-token case above used to - but the item
        // itself is still real and must still be queued; only the cursor can't use it.
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(LIST_API, 200, JSON, LIST_FIXTURE);
            // A stub registered by path answers every query identically; with a non-numeric ID the
            // fix must not rely on that to end the loop the way the ignored-token test does.
            server.onPathStatus(ITEMS_API, 200, JSON, itemPageWithRawId("item-abc"));
            server.start();

            try (SharePointClient client = SharePointClient.builder().setUrl(server.getBaseUrl()).setSite(SITE_NAME).build()) {
                final Queue<SharePointCrawl> crawlingQueue = new ConcurrentLinkedQueue<>();
                new ListCrawl(client, LIST_ID, "Announcements", NUMBER_PER_PAGE, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(),
                        false, true, List.of(), List.of(), true, FileCrawl.DEFAULT_EXTRACTOR_NAME, FileCrawl.DEFAULT_SUPPORTED_MIMETYPES,
                        FileCrawl.DEFAULT_MAX_CONTENT_LENGTH, null).doCrawl(null, crawlingQueue);

                assertEquals("a page with no parseable numeric ID must stop paging further after its first request", 1,
                        server.getRecordedRequests().stream().filter(request -> ITEMS_API.equals(request.getPath())).count());
                assertEquals("the item itself still exists and must still be queued, even though it cannot advance the cursor",
                        List.of("item-abc"), queuedItemIds(crawlingQueue));
            }
        }
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_nonNumericIdAlongsideNumericIdsIsQueuedButDoesNotContributeToTheCursor() throws Exception {
        // A page mixing a non-numeric ID with numeric ones must not be treated as unpageable -
        // that is only for a page where every ID fails to parse. The non-numeric item is queued
        // like any other item, and paging continues using only the numeric maximum.
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(LIST_API, 200, JSON, LIST_FIXTURE);
            server.onPathQuery(ITEMS_API, itemsQuery(NUMBER_PER_PAGE, 0), JSON, itemPageWithRawIds("weird", "1"));
            server.onPathQuery(ITEMS_API, itemsQuery(NUMBER_PER_PAGE, 1), JSON, itemPageWithRawIds("5"));
            server.onPathQuery(ITEMS_API, itemsQuery(NUMBER_PER_PAGE, 5), JSON, EMPTY_PAGE);
            server.start();

            try (SharePointClient client = SharePointClient.builder().setUrl(server.getBaseUrl()).setSite(SITE_NAME).build()) {
                final Queue<SharePointCrawl> crawlingQueue = new ConcurrentLinkedQueue<>();
                new ListCrawl(client, LIST_ID, "Announcements", NUMBER_PER_PAGE, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(),
                        false, true, List.of(), List.of(), true, FileCrawl.DEFAULT_EXTRACTOR_NAME, FileCrawl.DEFAULT_SUPPORTED_MIMETYPES,
                        FileCrawl.DEFAULT_MAX_CONTENT_LENGTH, null).doCrawl(null, crawlingQueue);

                assertEquals("the non-numeric item must be queued alongside the numeric one, and paging must continue past it",
                        List.of("weird", "1", "5"), queuedItemIds(crawlingQueue));
                assertEquals("the numeric item's ID (1), not the non-numeric one, must drive the cursor for the next request", 3,
                        server.getRecordedRequests().stream().filter(request -> ITEMS_API.equals(request.getPath())).count());
            }
        }
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_transientStallMidListingStillCrawlsItemsPastIt() throws Exception {
        // A list with items 1, 2, 3, 4 at numberPerPage=2, where the second request comes back a
        // stale repeat of the first page ([1, 2]) instead of the real next page - the kind of
        // one-off a stale replica, a cache, or a retried request can produce - while the real
        // next page ([3, 4]) was reachable the whole time. Losing 3 and 4 here is exactly the
        // failure this whole change exists to eliminate: it must not be reintroduced by treating
        // one non-advancing response as proof nothing past it is reachable.
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathStatus(LIST_API, 200, JSON, LIST_FIXTURE);
            // The first two requests to ITEMS_API are answered in order, regardless of their
            // query string: the genuine first page, then the stale repeat of it. Once both are
            // consumed, requests fall through to the query-exact stubs below, which is what lets
            // the retry (same cursor as the stale repeat) reach the real second page.
            server.onPathOnce(ITEMS_API, 200, JSON, itemPageWithIds(1, 2));
            server.onPathOnce(ITEMS_API, 200, JSON, itemPageWithIds(1, 2));
            server.onPathQuery(ITEMS_API, itemsQuery(NUMBER_PER_PAGE, 2), JSON, itemPageWithIds(3, 4));
            server.onPathQuery(ITEMS_API, itemsQuery(NUMBER_PER_PAGE, 4), JSON, EMPTY_PAGE);
            server.start();

            try (SharePointClient client = SharePointClient.builder().setUrl(server.getBaseUrl()).setSite(SITE_NAME).build()) {
                final Queue<SharePointCrawl> crawlingQueue = new ConcurrentLinkedQueue<>();
                new ListCrawl(client, LIST_ID, "Announcements", NUMBER_PER_PAGE, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(),
                        false, true, List.of(), List.of(), true, FileCrawl.DEFAULT_EXTRACTOR_NAME, FileCrawl.DEFAULT_SUPPORTED_MIMETYPES,
                        FileCrawl.DEFAULT_MAX_CONTENT_LENGTH, null).doCrawl(null, crawlingQueue);

                assertEquals(
                        "items past a transient repeat must still be crawled - 1 and 2 queued twice (the genuine page and the "
                                + "stale repeat), 3 and 4 once each once the retry reaches the real next page",
                        List.of("1", "2", "1", "2", "3", "4"), queuedItemIds(crawlingQueue));
                assertEquals("the genuine page, the stale repeat, the recovered real page, and the empty page that ends it", 4,
                        server.getRecordedRequests().stream().filter(request -> ITEMS_API.equals(request.getPath())).count());
            }
        }
    }

    /** The exact raw query string GetListItems builds for one page request. */
    private static String itemsQuery(final int num, final int start) {
        return "%24top=" + num + "&%24skiptoken=Paged=TRUE%26p_ID=" + start + "&%24select=Title,Id,Attachments,Created,Modified";
    }

    /** A page of {@code count} items with no attachments, so nothing else is queued for them. */
    private static String itemPage(final int count) {
        final int[] ids = new int[count];
        for (int i = 0; i < count; i++) {
            ids[i] = i;
        }
        return itemPageWithIds(ids);
    }

    /** A page holding exactly the given item IDs, with no attachments. */
    private static String itemPageWithIds(final int... ids) {
        final StringBuilder buf = new StringBuilder("{\"value\":[");
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) {
                buf.append(',');
            }
            buf.append(itemJson(String.valueOf(ids[i])));
        }
        return buf.append("]}").toString();
    }

    /** A single-item page whose Id is the given raw (possibly non-numeric) string. */
    private static String itemPageWithRawId(final String rawId) {
        return itemPageWithRawIds(rawId);
    }

    /** A page holding items with the given raw (possibly non-numeric) Id strings, in order. */
    private static String itemPageWithRawIds(final String... rawIds) {
        final StringBuilder buf = new StringBuilder("{\"value\":[");
        for (int i = 0; i < rawIds.length; i++) {
            if (i > 0) {
                buf.append(',');
            }
            buf.append(itemJson(rawIds[i]));
        }
        return buf.append("]}").toString();
    }

    private static String itemJson(final String id) {
        return "{\"Id\":\"" + id + "\",\"Title\":\"Item " + id + "\",\"Attachments\":false,\"odata.editLink\":\"Web/Lists(guid'" + LIST_ID
                + "')/Items(" + id + ")\",\"Created\":\"2026-01-01T00:00:00Z\",\"Modified\":\"2026-01-02T00:00:00Z\"}";
    }

    /**
     * Extracts the item IDs of every {@link ItemCrawl} queued, in queue order, from its stats key
     * ({@code "item#" + listName + ":" + itemId} - see {@link ItemCrawl}'s constructor), since
     * neither it nor {@link SharePointCrawl} exposes the item ID through a dedicated accessor.
     */
    private static List<String> queuedItemIds(final Queue<SharePointCrawl> crawlingQueue) {
        return crawlingQueue.stream().map(crawl -> {
            final String key = crawl.getStatsKey().getId();
            return key.substring(key.lastIndexOf(':') + 1);
        }).collect(Collectors.toList());
    }
}
