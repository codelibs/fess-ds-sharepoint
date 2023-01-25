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
package org.codelibs.fess.ds.sharepoint.crawl.list;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.apache.commons.lang3.StringUtils;
import org.codelibs.core.lang.StringUtil;
import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.client.api.list.PageType;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistforms.GetForms;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistforms.GetFormsResponse;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemValueResponse;
import org.codelibs.fess.ds.sharepoint.crawl.SharePointCrawl;
import org.codelibs.fess.helper.CrawlerStatsHelper.StatsKeyObject;
import org.codelibs.fess.mylasta.direction.FessConfig;
import org.codelibs.fess.util.ComponentUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ItemCrawl extends SharePointCrawl {
    private static final Logger logger = LoggerFactory.getLogger(ItemCrawl.class);

    private static final String ITEM_VALUE_PREFIX = "val_";
    private final boolean showFieldNameAtContent = false;

    private final String listId;
    private final String listName;
    private final String itemId;
    private final List<String> roles;
    private final boolean isSubPage;
    private final List<String> includeFields;
    private final List<String> excludeFields;

    public ItemCrawl(final SharePointClient client, final String listId, final String listName, final String itemId,
            final List<String> roles, final boolean isSubPage, final List<String> includeFields, final List<String> excludeFields) {
        super(client);
        this.listId = listId;
        this.listName = listName != null ? listName : StringUtil.EMPTY;
        this.itemId = itemId;
        this.roles = roles;
        this.isSubPage = isSubPage;
        this.includeFields = includeFields;
        final List<String> exList = new ArrayList<>(excludeFields);
        exList.addAll(EXCLUDE_FIELDS);
        this.excludeFields = exList;
        statsKey = new StatsKeyObject("item#" + listName + ":" + itemId);
    }

    @Override
    public Map<String, Object> doCrawl(final Queue<SharePointCrawl> crawlingQueue) {
        if (logger.isInfoEnabled()) {
            logger.info("[Crawling ListItem] [listName:{}] [itemId:{}]", listName, itemId);
        }

        final GetListItemValueResponse response = client.api().list().getListItemValue().setListId(listId).setItemId(itemId).execute();
        final String content = buildContent(response);
        final String webLink = getWebLink(response);
        final FessConfig fessConfig = ComponentUtil.getFessConfig();
        final Map<String, Object> dataMap = new HashMap<>();
        dataMap.put(fessConfig.getIndexFieldUrl(), webLink);
        dataMap.put(fessConfig.getIndexFieldHost(), client.helper().getHostName());
        dataMap.put(fessConfig.getIndexFieldSite(), response.getFileRef());
        dataMap.put(fessConfig.getIndexFieldTitle(), getTitle(response));
        dataMap.put(fessConfig.getIndexFieldTitle() + "WithListName", "[" + listName + "] " + getTitle(response));
        dataMap.put("listName", listName);
        dataMap.put(fessConfig.getIndexFieldContent(), content);
        dataMap.put(fessConfig.getIndexFieldDigest(), buildDigest(content));
        dataMap.put(fessConfig.getIndexFieldContentLength(), content.length());
        dataMap.put(fessConfig.getIndexFieldLastModified(), response.getModified());
        dataMap.put(fessConfig.getIndexFieldCreated(), response.getCreated());
        dataMap.put(fessConfig.getIndexFieldMimetype(), "text/html");
        dataMap.put(fessConfig.getIndexFieldFiletype(), ComponentUtil.getFileTypeHelper().get("text/html"));
        for (final Map.Entry<String, String> entry : response.getValues().entrySet()) {
            if (!dataMap.containsKey(entry.getKey())) {
                dataMap.put(normalizeKey(entry.getKey()), entry.getValue());
            }
        }
        response.getValues().entrySet().stream()
                .forEach(entry -> dataMap.put(ITEM_VALUE_PREFIX + normalizeKey(entry.getKey()), entry.getValue()));

        if (roles != null && !roles.isEmpty()) {
            dataMap.put(fessConfig.getIndexFieldRole(), roles);
        }
        dataMap.put("list_name", listName);
        dataMap.put("list_id", listId);
        dataMap.put("item_id", itemId);
        return dataMap;
    }

    private String buildContent(final GetListItemValueResponse response) {
        final StringBuilder sb = new StringBuilder(1000);
        response.getValues().entrySet().stream().filter(entry -> StringUtils.isNotBlank(entry.getValue()))
                .filter(entry -> (includeFields.size() == 0 || includeFields.contains(entry.getKey()))
                        && !excludeFields.stream().anyMatch(exField -> entry.getKey().matches(exField)))
                .forEach(entry -> {
                    if (showFieldNameAtContent) {
                        sb.append('[').append(normalizeKey(entry.getKey())).append("] ");
                    }
                    sb.append(entry.getValue()).append(" ");
                });
        return sb.toString();
    }

    private String getTitle(final GetListItemValueResponse response) {
        if (response.getTitle().length() > 0) {
            return response.getTitle();
        }
        if (response.getFileLeafRef().length() > 0) {
            return response.getFileLeafRef();
        }
        return StringUtil.EMPTY;
    }

    private String getWebLink(final GetListItemValueResponse response) {
        if (isSubPage && StringUtils.isNotBlank(response.getFileRef())) {
            final String siteRef = response.getFileRef();
            final StringBuilder sb = new StringBuilder(siteRef.length() * 2);

            for (final String part : siteRef.split("/")) {
                if (part.length() == 0) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append('/');
                }
                sb.append(URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20"));
            }
            return client.getUrl() + sb.toString();
        }
        final String formUrl = getFormUrl();
        if (formUrl == null) {
            return null;
        }
        if (response.getFsObjType() == 0 && StringUtils.isNotBlank(response.getParentItemId())) {
            final String dirRef = response.getFileDirRef();
            final String serverRelativeUrl = formUrl.replace("DispForm.aspx", "Flat.aspx");
            return client.getUrl() + serverRelativeUrl.substring(1) + "?ID=" + itemId + "&RootFolder="
                    + URLEncoder.encode(dirRef, StandardCharsets.UTF_8);
        }
        if (response.getFsObjType() == 1) {
            final String serverRelativeUrl = formUrl.replace("DispForm.aspx", "Flat.aspx");
            return client.getUrl() + serverRelativeUrl.substring(1) + "?ID=" + itemId + "&RootFolder="
                    + URLEncoder.encode(response.getFileRef(), StandardCharsets.UTF_8);
        }
        final String serverRelativeUrl = formUrl;
        return client.getUrl() + serverRelativeUrl.substring(1) + "?ID=" + itemId;
    }

    private String normalizeKey(final String key) {
        //return key.replace("_x005f_", "_");
        return key;
    }

    private String getFormUrl() {
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
        return form.getServerRelativeUrl();
    }

    private static final List<String> EXCLUDE_FIELDS = Arrays.asList("odata.*", "OData__.*", "ContentTypeId", "Title",
            "File_x005f_x0020_x005f_Type", "ComplianceAssetId", "ID", "Modified", "Created", "Author", "Editor", "owshiddenversion",
            "WorkflowVersion", "Attachments", "InstanceID", "Order", "GUID", "WorkflowInstanceID", "FileRef", "FileDirRef",
            "Last_x005f_x0020_x005f_Modified", "Created_x005f_x0020_x005f_Date", "FSObjType", "SortBehavior", "FileLeafRef", "UniqueId",
            "SyncClientId", "ProgId", "ScopeId", "MetaInfo", "ItemChildCount", "FolderChildCount", "Restricted", "OriginatorId",
            "NoExecute", "ContentVersion", "AccessPolicy", "AppAuthor", "AppEditor", "SMTotalSize", "SMLastModifiedDate",
            "SMTotalFileStreamSize", "SMTotalFileCount", "Exists", "ParentItemID", "ParentFolderID", "Expires",
            "Created_x005f_x0020_x005f_By", "PageLayoutType", "StreamHash", "PromotedState", "DocConcurrencyNumber", "ParentUniqueId",
            "Modified_x005f_x0020_x005f_By", "VirusStatus", "IsCheckedoutToLocal", "File_x0020_Size", "BannerImageUrl",
            "ClientSideApplicationId", "File_x005f_x0020_x005f_Size", "FirstPublishedDate");
}
