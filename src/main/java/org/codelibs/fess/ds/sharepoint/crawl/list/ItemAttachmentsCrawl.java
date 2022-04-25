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
package org.codelibs.fess.ds.sharepoint.crawl.list;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.client.api.list.PageType;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistforms.GetForms;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistforms.GetFormsResponse;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemAttachmentsResponse;
import org.codelibs.fess.ds.sharepoint.crawl.SharePointCrawl;
import org.codelibs.fess.ds.sharepoint.crawl.file.FileCrawl;
import org.codelibs.fess.helper.CrawlerStatsHelper.StatsKeyObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ItemAttachmentsCrawl extends SharePointCrawl {
    private static final Logger logger = LoggerFactory.getLogger(ItemAttachmentsCrawl.class);

    private final String listId;
    private final String listName;
    private final String itemId;
    private final Date created;
    private final Date modified;
    private final List<String> roles;

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

    @Override
    public Map<String, Object> doCrawl(final Queue<SharePointCrawl> crawlingQueue) {
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
