/*
 * Copyright 2012-2023 CodeLibs Project and the Others.
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
package org.codelibs.fess.ds.sharepoint.crawl.doclib;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.client.api.doclib.getfiles.GetFilesResponse;
import org.codelibs.fess.ds.sharepoint.client.api.doclib.getfolder.GetFolderResponse;
import org.codelibs.fess.ds.sharepoint.client.api.doclib.getfolders.GetFoldersResponse;
import org.codelibs.fess.ds.sharepoint.client.api.doclib.getlistitem.GetDoclibListItemResponse;
import org.codelibs.fess.ds.sharepoint.client.api.list.PageType;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistforms.GetForms;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistforms.GetFormsResponse;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemRoleResponse;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemValueResponse;
import org.codelibs.fess.ds.sharepoint.crawl.SharePointCrawl;
import org.codelibs.fess.ds.sharepoint.crawl.file.FileCrawl;
import org.codelibs.fess.es.config.exentity.DataConfig;
import org.codelibs.fess.helper.CrawlerStatsHelper.StatsKeyObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FolderCrawl extends SharePointCrawl {
    private static final Logger logger = LoggerFactory.getLogger(FolderCrawl.class);
    private static final int PAGE_SIZE = 100;

    private final String serverRelativeUrl;
    private final Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache;
    private final boolean skipRole;

    public FolderCrawl(final SharePointClient client, final String serverRelativeUrl, final boolean skipRole,
            final Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache) {
        super(client);
        this.serverRelativeUrl = serverRelativeUrl;
        this.sharePointGroupCache = sharePointGroupCache;
        this.skipRole = skipRole;
        statsKey = new StatsKeyObject("folder#" + serverRelativeUrl);
    }

    @Override
    public Map<String, Object> doCrawl(final DataConfig dataConfig, final Queue<SharePointCrawl> crawlingQueue) {
        if (logger.isInfoEnabled()) {
            logger.info("[Crawling DocLib Folder] serverRelativeUrl:{}", serverRelativeUrl);
        }

        final GetFolderResponse getFolderResponse = client.api().doclib().getFolder().setServerRelativeUrl(serverRelativeUrl).execute();
        if (getFolderResponse.getItemCount() > 0) {
            int foldersStart = 0;
            while (true) {
                final GetFoldersResponse getFoldersResponse = client.api().doclib().getFolders().setServerRelativeUrl(serverRelativeUrl)
                        .setStart(foldersStart).setNum(PAGE_SIZE).execute();
                if (getFoldersResponse.getFolders().size() == 0) {
                    break;
                }
                foldersStart += PAGE_SIZE;
                getFoldersResponse.getFolders().forEach(subFolder -> {
                    crawlingQueue.offer(new FolderCrawl(client, subFolder.getServerRelativeUrl(), skipRole, sharePointGroupCache));
                });
            }

            int filesStart = 0;
            while (true) {
                final GetFilesResponse getFilesResponse = client.api().doclib().getFiles().setServerRelativeUrl(serverRelativeUrl)
                        .setStart(filesStart).setNum(PAGE_SIZE).execute();
                if (getFilesResponse.getFiles().size() == 0) {
                    break;
                }
                filesStart += PAGE_SIZE;
                getFilesResponse.getFiles().forEach(file -> {
                    final GetDoclibListItemResponse getDoclibListItemResponse =
                            client.api().doclib().getListItem().setServerRelativeUrl(file.getServerRelativeUrl()).execute();
                    final List<String> roles = getItemRoles(getDoclibListItemResponse.getListId(), getDoclibListItemResponse.getItemId(),
                            sharePointGroupCache, skipRole);
                    final GetListItemValueResponse getListItemValueResponse = client.api().list().getListItemValue()
                            .setListId(getDoclibListItemResponse.getListId()).setItemId(getDoclibListItemResponse.getItemId()).execute();
                    final Map<String, String> listValues = getListItemValueResponse.getValues();
                    final String webLink =
                            getWebLink(getDoclibListItemResponse.getListId(), file.getServerRelativeUrl(), serverRelativeUrl);
                    crawlingQueue.offer(new FileCrawl(client, file.getFileName(), webLink, file.getServerRelativeUrl(), file.getCreated(),
                            file.getModified(), roles, listValues, null));
                });
            }
        }
        return null;
    }

    private String getWebLink(final String listId, final String filePath, final String parentUrl) {
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
        return client.getUrl() + serverRelativeUrl.substring(1).replace("DispForm", "AllItems") + "?id=" + filePath + "&parent="
                + URLEncoder.encode(parentUrl, StandardCharsets.UTF_8);
    }
}
