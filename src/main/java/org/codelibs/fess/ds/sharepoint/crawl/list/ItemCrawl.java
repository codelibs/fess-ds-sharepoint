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
import org.codelibs.fess.ds.sharepoint.client.api.list.PageType;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistforms.GetForms;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistforms.GetFormsResponse;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemValueResponse;
import org.codelibs.fess.ds.sharepoint.crawl.SharePointCrawl;
import org.codelibs.fess.mylasta.direction.FessConfig;
import org.codelibs.fess.util.ComponentUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ItemCrawl extends SharePointCrawl {
    private static final Logger logger = LoggerFactory.getLogger(ItemCrawl.class);

    private static final String ITEM_VALUE_PREFIX = "val_";

    private final String listId;
    private final String listName;
    private final String itemId;
    private final List<String> roles;

    public ItemCrawl(SharePointClient client, String listId, String listName, String itemId, List<String> roles) {
        super(client);
        this.listId = listId;
        this.listName = listName;
        this.itemId = itemId;
        this.roles = roles;
    }

    @Override
    public Map<String, Object> doCrawl(Queue<SharePointCrawl> crawlingQueue) {
        if (logger.isInfoEnabled()) {
            logger.info("[Crawling ListItem] [listName:{}] [itemId:{}]", listName, itemId);
        }

        final GetListItemValueResponse response = client.api().list().getListItemValue().setListId(listId).setItemId(itemId).execute();
        final String content = buildContent(response);

        final String webLink = getWebLink();
        final FessConfig fessConfig = ComponentUtil.getFessConfig();
        final Map<String, Object> dataMap = new HashMap<>();
        dataMap.put(fessConfig.getIndexFieldUrl(), webLink);
        dataMap.put(fessConfig.getIndexFieldHost(), client.helper().getHostName());
        dataMap.put(fessConfig.getIndexFieldSite(), webLink.replace("http://", "").replace("https://", ""));

        dataMap.put(fessConfig.getIndexFieldTitle(), response.getTitle());
        dataMap.put(fessConfig.getIndexFieldContent(), content);
        dataMap.put(fessConfig.getIndexFieldDigest(), content);
        dataMap.put(fessConfig.getIndexFieldContentLength(), content.length());
        dataMap.put(fessConfig.getIndexFieldLastModified(), response.getModified());
        dataMap.put(fessConfig.getIndexFieldCreated(), response.getCreated());
        dataMap.put(fessConfig.getIndexFieldMimetype(), "text/html");
        dataMap.put(fessConfig.getIndexFieldFiletype(), ComponentUtil.getFileTypeHelper().get("text/html"));
        for (Map.Entry<String, String> entry: response.getValues().entrySet()) {
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
        final StringBuilder sb = new StringBuilder();
        response.getValues().entrySet().stream().forEach(entry -> {
            sb.append(normalizeKey(entry.getKey())).append(' ').append(entry.getValue()).append('\n');
        });
        return sb.toString();
    }

    private String getWebLink() {
        final GetForms getForms = client.api().list().getForms();
        if (listId != null) {
            getForms.setListId(listId);
        }
        final GetFormsResponse getFormsResponse = getForms.execute();
        GetFormsResponse.Form form = getFormsResponse.getForms().stream().filter(f -> f.getType() == PageType.DISPLAY_FORM).findFirst().orElse(null);
        if (form == null) {
            return null;
        }
        String serverRelativeUrl = form.getServerRelativeUrl();
        return client.getUrl() + serverRelativeUrl.substring(1) + "?ID=" + itemId;
    }

    private String normalizeKey(final String key) {
        return key.replace("_x005f_", "_");
    }
}
