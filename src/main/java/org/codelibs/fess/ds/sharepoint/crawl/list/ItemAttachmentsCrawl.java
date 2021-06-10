/*
 * Copyright 2012-2021 CodeLibs Project and the Others.
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

import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.client.api.list.PageType;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistforms.GetForms;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistforms.GetFormsResponse;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemAttachmentsResponse;
import org.codelibs.fess.ds.sharepoint.crawl.SharePointCrawl;
import org.codelibs.fess.ds.sharepoint.crawl.file.FileCrawl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ItemAttachmentsCrawl extends SharePointCrawl {
    private static final Logger logger = LoggerFactory.getLogger(ItemAttachmentsCrawl.class);

    private final String listId;
    private final String listName;
    private final String itemId;
    private final Date created;
    private final Date modified;
    private final List<String> roles;

    public ItemAttachmentsCrawl(SharePointClient client, String listId, String listName, String itemId, Date created, Date modified,
            List<String> roles) {
        super(client);
        this.itemId = itemId;
        this.listId = listId;
        this.listName = listName;
        this.created = created;
        this.modified = modified;
        this.roles = roles;
    }

    @Override
    public Map<String, Object> doCrawl(Queue<SharePointCrawl> crawlingQueue) {
        if (logger.isInfoEnabled()) {
            logger.info("[Crawling ListItem Attachments] [listName:{}] [itemId:{}]", listName, itemId);
        }

        GetListItemAttachmentsResponse response = client.api().list().getListItemAttachments().setId(listId, itemId).execute();
        response.getFiles().forEach(file -> {
            FileCrawl fileCrawl = new FileCrawl(client, file.getFileName(), getWebLink(file.getFileName()), file.getServerRelativeUrl(),
                    created, modified, roles);
            fileCrawl.addProperty("list_name", listName);
            fileCrawl.addProperty("list_id", listId);
            fileCrawl.addProperty("item_id", itemId);
            crawlingQueue.offer(fileCrawl);
        });
        return null;
    }

    private String getWebLink(String fileName) {
        final GetForms getForms = client.api().list().getForms();
        if (listId != null) {
            getForms.setListId(listId);
        }
        final GetFormsResponse getFormsResponse = getForms.execute();
        GetFormsResponse.Form form =
                getFormsResponse.getForms().stream().filter(f -> f.getType() == PageType.DISPLAY_FORM).findFirst().orElse(null);
        if (form == null) {
            return null;
        }
        String serverRelativeUrl = form.getServerRelativeUrl();
        return client.getUrl() + serverRelativeUrl.substring(1) + "?ID=" + itemId + "&attachments="
                + URLEncoder.encode(fileName, StandardCharsets.UTF_8);
    }
}
