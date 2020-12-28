/*
 * Copyright 2012-2020 CodeLibs Project and the Others.
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
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemRoleResponse;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitems.GetListItemsResponse;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlists.GetListResponse;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlists.GetListsResponse;
import org.codelibs.fess.ds.sharepoint.crawl.SharePointCrawl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Queue;

public class ListCrawl extends SharePointCrawl {
    private static final Logger logger = LoggerFactory.getLogger(ListCrawl.class);

    private final String id;
    private final String listName;
    private final int numberPerPage;
    private final Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache;
    private final Boolean isSubPage;
    private final List<String> includeFields;
    private final List<String> excludeFields;

    public ListCrawl(SharePointClient client,
                     String id,
                     String listName,
                     int numberPerPage,
                     Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache,
                     boolean isSubPage,
                     List<String> includeFields,
                     List<String> excludeFields
                     ) {
        super(client);
        this.id = id;
        this.listName = listName;
        this.numberPerPage = numberPerPage;
        this.sharePointGroupCache = sharePointGroupCache;
        this.isSubPage = isSubPage;
        this.includeFields = includeFields;
        this.excludeFields = excludeFields;
    }

    @Override
    public Map<String, Object> doCrawl(Queue<SharePointCrawl> crawlingQueue) {
        if (logger.isInfoEnabled()) {
            logger.info("[Crawling List] [id:{}] [listName:{}]", id, listName);
        }

        final GetListResponse getListResponse = client.api().list().getList().setListId(id).setListName(listName).execute();
        final GetListsResponse.SharePointList sharePointList = getListResponse.getList();
        final String listId = sharePointList.getId();
        final String listName = sharePointList.getListName();
        for (int start=0; ;start += numberPerPage) {
            final GetListItemsResponse getListItemsResponse;
            if (listId != null) {
                getListItemsResponse = client.api().list().getListItems().setListId(listId).setSubPage(isSubPage).setNum(numberPerPage).setStart(start).execute();
            } else {
                return null;
            }
            if (getListItemsResponse.getListItems().isEmpty()) {
                break;
            }
            getListItemsResponse.getListItems().forEach(item -> {
                if (item.getTitle().startsWith("$Resources")) {
                    return;
                }

                final List<String> roles = getItemRoles(listId, item.getId(), sharePointGroupCache);
                crawlingQueue.offer(new ItemCrawl(client,
                        listId,
                        listName,
                        item.getId(),
                        roles,
                        isSubPage,
                        includeFields,
                        excludeFields));
                if (item.hasAttachments()) {
                    crawlingQueue.offer(new ItemAttachmentsCrawl(client, listId, listName, item.getId(), item.getCreated(), item.getModified(), roles));
                }
            });
        }
        return null;
    }
}
