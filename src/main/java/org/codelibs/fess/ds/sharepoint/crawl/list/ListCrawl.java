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
     */
    public ListCrawl(final SharePointClient client, final String id, final String listName, final int numberPerPage,
            final Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache, final boolean isSubPage,
            final boolean skipRole, final List<String> includeFields, final List<String> excludeFields) {
        super(client);
        this.id = id;
        this.listName = listName;
        this.numberPerPage = numberPerPage;
        this.sharePointGroupCache = sharePointGroupCache;
        this.isSubPage = isSubPage;
        this.skipRole = skipRole;
        this.includeFields = includeFields;
        this.excludeFields = excludeFields;
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
        for (int start = 0;; start += numberPerPage) {
            GetListItemsResponse getListItemsResponse;
            if (listId == null) {
                return null;
            }
            try {
                getListItemsResponse = client.api().list().getListItems().setListId(listId).setSubPage(isSubPage).setNum(numberPerPage)
                        .setStart(start).execute();
            } catch (final SharePointServerException e) {
                if (e.getStatusCode() != 400) {
                    throw e;
                }
                getListItemsResponse = client.api().list().getListItems().setListId(listId).setSubPage(true).setNum(numberPerPage)
                        .setStart(start).execute();
            }
            if (getListItemsResponse.getListItems().isEmpty()) {
                break;
            }
            getListItemsResponse.getListItems().forEach(item -> {
                if (item.getTitle().startsWith("$Resources")) {
                    return;
                }

                final List<String> roles = getItemRoles(listId, item.getId(), sharePointGroupCache, skipRole);
                crawlingQueue.offer(new ItemCrawl(client, listId, listName, item.getId(), roles, isSubPage, includeFields, excludeFields));
                if (item.hasAttachments()) {
                    crawlingQueue.offer(
                            new ItemAttachmentsCrawl(client, listId, listName, item.getId(), item.getCreated(), item.getModified(), roles));
                }
            });
        }
        return null;
    }
}
