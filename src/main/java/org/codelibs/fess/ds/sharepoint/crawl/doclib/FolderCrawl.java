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
package org.codelibs.fess.ds.sharepoint.crawl.doclib;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fess.crawler.filter.UrlFilter;
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
import org.codelibs.fess.helper.CrawlerStatsHelper.StatsKeyObject;
import org.codelibs.fess.opensearch.config.exentity.DataConfig;

/**
 * Crawling class for SharePoint document library folders.
 * Recursively crawls folders and files within a SharePoint document library.
 */
public class FolderCrawl extends SharePointCrawl {
    private static final Logger logger = LogManager.getLogger(FolderCrawl.class);
    private static final int PAGE_SIZE = 100;

    /**
     * Upper bound on the number of pages either listing below may fetch.
     *
     * <p>Both loops end when a page comes back empty or shorter than the page size. A server that
     * ignores the skip parameter answers the same full first page instead, and every pass queues
     * that page's entries again, so the listing neither finishes nor stays within memory. This
     * bound turns that into a truncated listing with a warning.
     *
     * <p>At {@link #PAGE_SIZE} entries per page it allows 10,000 subfolders and 10,000 files
     * directly inside one folder, twice SharePoint's own 5,000-item list view threshold.
     */
    private static final int MAX_PAGES = 100;

    private final String serverRelativeUrl;
    private final Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache;
    private final boolean skipRole;
    private final boolean ignoreError;
    private final String extractorName;
    private final String[] supportedMimeTypes;
    private final long maxContentLength;
    private final UrlFilter urlFilter;

    /**
     * Constructs a FolderCrawl instance for crawling a SharePoint document library folder.
     *
     * @param client the SharePoint client for API communication
     * @param serverRelativeUrl the server-relative URL of the folder to crawl
     * @param skipRole whether to skip role/permission checking
     * @param sharePointGroupCache cache for SharePoint group information
     * @param ignoreError whether a file's content extraction failure should be logged instead of
     *            failing its crawl target
     * @param extractorName the name of the extractor component used to extract a file's content
     * @param supportedMimeTypes regular expressions a file's MIME type must match at least one of
     *            to be crawled
     * @param maxContentLength the maximum file size in bytes, or a negative number for no limit
     * @param urlFilter the include_pattern/exclude_pattern filter built once for the whole crawl,
     *            or null
     */
    public FolderCrawl(final SharePointClient client, final String serverRelativeUrl, final boolean skipRole,
            final Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache, final boolean ignoreError,
            final String extractorName, final String[] supportedMimeTypes, final long maxContentLength, final UrlFilter urlFilter) {
        super(client);
        this.serverRelativeUrl = serverRelativeUrl;
        this.sharePointGroupCache = sharePointGroupCache;
        this.skipRole = skipRole;
        this.ignoreError = ignoreError;
        this.extractorName = extractorName;
        this.supportedMimeTypes = supportedMimeTypes;
        this.maxContentLength = maxContentLength;
        this.urlFilter = urlFilter;
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
            for (int page = 0; page < MAX_PAGES; page++) {
                final GetFoldersResponse getFoldersResponse = client.api()
                        .doclib()
                        .getFolders()
                        .setServerRelativeUrl(serverRelativeUrl)
                        .setStart(foldersStart)
                        .setNum(PAGE_SIZE)
                        .execute();
                final List<GetFolderResponse> folders = getFoldersResponse.getFolders();
                if (folders.isEmpty()) {
                    break;
                }
                foldersStart += PAGE_SIZE;
                folders.forEach(subFolder -> {
                    crawlingQueue.offer(new FolderCrawl(client, subFolder.getServerRelativeUrl(), skipRole, sharePointGroupCache,
                            ignoreError, extractorName, supportedMimeTypes, maxContentLength, urlFilter));
                });
                if (folders.size() < PAGE_SIZE) {
                    break;
                }
                if (page == MAX_PAGES - 1) {
                    // Truncating here is not counted as a crawl failure, so the stale-document
                    // cleanup still runs and documents past the bound can be removed from the
                    // index.
                    logger.warn("Stopped listing the subfolders of {} after {} pages; the listing may be truncated.", serverRelativeUrl,
                            MAX_PAGES);
                }
            }

            int filesStart = 0;
            for (int page = 0; page < MAX_PAGES; page++) {
                final GetFilesResponse getFilesResponse = client.api()
                        .doclib()
                        .getFiles()
                        .setServerRelativeUrl(serverRelativeUrl)
                        .setStart(filesStart)
                        .setNum(PAGE_SIZE)
                        .execute();
                final List<GetFilesResponse.DocLibFile> files = getFilesResponse.getFiles();
                if (files.isEmpty()) {
                    break;
                }
                filesStart += PAGE_SIZE;
                files.forEach(file -> {
                    // Checked here, before the four requests below, precisely because a file's
                    // server-relative URL is already in hand at this point - unlike a list item,
                    // whose URL-ish value only exists after fetching its value (see ItemCrawl).
                    if (!isUrlAllowed(urlFilter, file.getServerRelativeUrl())) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("{} is not matched with include_pattern/exclude_pattern.", file.getServerRelativeUrl());
                        }
                        return;
                    }
                    final GetDoclibListItemResponse getDoclibListItemResponse =
                            client.api().doclib().getListItem().setServerRelativeUrl(file.getServerRelativeUrl()).execute();
                    final List<String> roles = getItemRoles(getDoclibListItemResponse.getListId(), getDoclibListItemResponse.getItemId(),
                            sharePointGroupCache, skipRole);
                    final GetListItemValueResponse getListItemValueResponse = client.api()
                            .list()
                            .getListItemValue()
                            .setListId(getDoclibListItemResponse.getListId())
                            .setItemId(getDoclibListItemResponse.getItemId())
                            .execute();
                    final Map<String, String> listValues = getListItemValueResponse.getValues();
                    final String webLink =
                            getWebLink(getDoclibListItemResponse.getListId(), file.getServerRelativeUrl(), serverRelativeUrl);
                    crawlingQueue.offer(new FileCrawl(client, file.getFileName(), webLink, file.getServerRelativeUrl(), file.getCreated(),
                            file.getModified(), roles, listValues, null, ignoreError, extractorName, supportedMimeTypes, maxContentLength));
                });
                if (files.size() < PAGE_SIZE) {
                    break;
                }
                if (page == MAX_PAGES - 1) {
                    // Truncating here is not counted as a crawl failure, so the stale-document
                    // cleanup still runs and documents past the bound can be removed from the
                    // index.
                    logger.warn("Stopped listing the files of {} after {} pages; the listing may be truncated.", serverRelativeUrl,
                            MAX_PAGES);
                }
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
