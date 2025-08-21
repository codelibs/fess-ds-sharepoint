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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.client.api.list.PageType;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistforms.GetForms;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistforms.GetFormsResponse;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemAttachmentsResponse;
import org.codelibs.fess.ds.sharepoint.crawl.SharePointCrawl;
import org.codelibs.fess.ds.sharepoint.crawl.file.FileCrawl;
import org.codelibs.fess.helper.CrawlerStatsHelper.StatsKeyObject;
import org.codelibs.fess.opensearch.config.exentity.DataConfig;

/**
 * Crawler implementation for SharePoint list item attachments.
 * This class handles crawling file attachments associated with SharePoint list items,
 * creating individual file crawl tasks for each attachment found.
 *
 * <p>The crawler retrieves attachment metadata and queues FileCrawl tasks
 * for processing each attachment file with proper access control and metadata.</p>
 *
 * @see SharePointCrawl
 * @see ItemCrawl
 * @see FileCrawl
 */
public class ItemAttachmentsCrawl extends SharePointCrawl {
    /** Logger for attachment crawling operations */
    private static final Logger logger = LogManager.getLogger(ItemAttachmentsCrawl.class);

    /** SharePoint list identifier */
    private final String listId;
    /** Display name of the SharePoint list */
    private final String listName;
    /** Unique identifier of the parent list item */
    private final String itemId;
    /** Creation date of the parent list item */
    private final Date created;
    /** Last modification date of the parent list item */
    private final Date modified;
    /** Access roles inherited from the parent list item */
    private final List<String> roles;

    /**
     * Constructs a new ItemAttachmentsCrawl instance for crawling list item attachments.
     *
     * @param client SharePoint client for API operations
     * @param listId unique identifier of the SharePoint list
     * @param listName display name of the SharePoint list
     * @param itemId unique identifier of the parent list item
     * @param created creation date of the parent list item
     * @param modified last modification date of the parent list item
     * @param roles access roles inherited from the parent list item
     */
    public ItemAttachmentsCrawl(final SharePointClient client, final String listId, final String listName, final String itemId,
            final Date created, final Date modified, final List<String> roles) {
        super(client);
        this.itemId = itemId;
        this.listId = listId;
        this.listName = listName;
        this.created = created;
        this.modified = modified;
        this.roles = roles;
        statsKey = new StatsKeyObject("item_attachment#" + listName + ":" + itemId);
    }

    /**
     * Performs the crawling of SharePoint list item attachments.
     * Retrieves attachment information and creates FileCrawl tasks
     * for each attachment file found.
     *
     * @param dataConfig data source configuration
     * @param crawlingQueue queue for additional crawl tasks (file crawl tasks)
     * @return null (this crawler only queues other tasks, doesn't create documents directly)
     */
    @Override
    public Map<String, Object> doCrawl(final DataConfig dataConfig, final Queue<SharePointCrawl> crawlingQueue) {
        if (logger.isInfoEnabled()) {
            logger.info("[Crawling ListItem Attachments] [listName:{}] [itemId:{}]", listName, itemId);
        }

        final GetListItemAttachmentsResponse response = client.api().list().getListItemAttachments().setId(listId, itemId).execute();
        response.getFiles().forEach(file -> {
            final FileCrawl fileCrawl = new FileCrawl(client, file.getFileName(), getWebLink(file.getFileName()),
                    file.getServerRelativeUrl(), created, modified, roles, Collections.emptyMap(), listName);
            fileCrawl.addProperty("list_name", listName);
            fileCrawl.addProperty("list_id", listId);
            fileCrawl.addProperty("item_id", itemId);
            crawlingQueue.offer(fileCrawl);
        });
        return null;
    }

    /**
     * Generates the web link URL for accessing an attachment file.
     * Creates a URL that points to the attachment within the SharePoint list item's display form.
     *
     * @param fileName name of the attachment file
     * @return web URL for accessing the attachment, or null if unable to generate
     */
    private String getWebLink(final String fileName) {
        final GetForms getForms = client.api().list().getForms();
        if (listId != null) {
            getForms.setListId(listId);
        }
        final GetFormsResponse getFormsResponse = getForms.execute();
        final GetFormsResponse.Form form =
                getFormsResponse.getForms().stream().filter(f -> f.getType() == PageType.DISPLAY_FORM).findFirst().orElse(null);
        if (form == null) {
            return null;
        }
        final String serverRelativeUrl = form.getServerRelativeUrl();
        return client.getUrl() + serverRelativeUrl.substring(1) + "?ID=" + itemId + "&attachments="
                + URLEncoder.encode(fileName, StandardCharsets.UTF_8);
    }
}
