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
package org.codelibs.fess.ds.sharepoint.crawl;

import org.apache.commons.lang3.StringUtils;
import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemRoleResponse;
import org.codelibs.fess.helper.SystemHelper;
import org.codelibs.fess.util.ComponentUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public abstract class SharePointCrawl {
    private final Logger logger = LoggerFactory.getLogger(SharePointCrawl.class);

    protected final SharePointClient client;

    public SharePointCrawl(SharePointClient client) {
        this.client = client;
    }

    abstract public Map<String, Object> doCrawl(final Queue<SharePointCrawl> crawlingQueue);

    protected List<String> getItemRoles(String listId, String itemId,
            Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache, boolean skipRole) {
        if (skipRole) {
            return new ArrayList<>();
        }
        final GetListItemRoleResponse getListItemRoleResponse =
                client.api().list().getListItemRole().setId(listId, itemId).setSharePointGroupCache(sharePointGroupCache).execute();
        SystemHelper systemHelper = ComponentUtil.getSystemHelper();
        final Set<String> roles = new HashSet<>();
        // AD
        getListItemRoleResponse.getUsers().stream().filter(user -> !user.isAzureAccount()).map(GetListItemRoleResponse.User::getAccount)
                .filter(title -> title.contains("\\")).map(systemHelper::getSearchRoleByUser).forEach(roles::add);
        getListItemRoleResponse.getSecurityGroups().stream().map(GetListItemRoleResponse.SecurityGroup::getTitle)
                .filter(title -> title.contains("\\")).map(systemHelper::getSearchRoleByGroup).forEach(roles::add);
        // AzureAD
        getListItemRoleResponse.getUsers().stream().filter(GetListItemRoleResponse.User::isAzureAccount)
                .map(GetListItemRoleResponse.User::getAccount).map(systemHelper::getSearchRoleByUser).forEach(roles::add);
        getListItemRoleResponse.getUsers().stream().filter(GetListItemRoleResponse.User::isAzureAccount)
                .map(GetListItemRoleResponse.User::getAdAccountFromAzureAccount).map(systemHelper::getSearchRoleByUser).forEach(roles::add);
        getListItemRoleResponse.getSecurityGroups().stream().filter(GetListItemRoleResponse.SecurityGroup::isAzureAccount)
                .map(GetListItemRoleResponse.SecurityGroup::getAzureAccount).map(systemHelper::getSearchRoleByGroup).forEach(roles::add);
        getListItemRoleResponse.getSecurityGroups().stream().filter(GetListItemRoleResponse.SecurityGroup::isAzureAccount)
                .map(GetListItemRoleResponse.SecurityGroup::getTitle).map(systemHelper::getSearchRoleByGroup).forEach(roles::add);
        getListItemRoleResponse.getSharePointGroups().stream()
                .flatMap(group -> getSharePointGroupTitles(group, sharePointGroupCache).stream()).forEach(roles::add);
        return roles.stream().collect(Collectors.toUnmodifiableList());
    }

    private Set<String> getSharePointGroupTitles(final GetListItemRoleResponse.SharePointGroup sharePointGroup,
            Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache) {
        SystemHelper systemHelper = ComponentUtil.getSystemHelper();
        final Set<String> titles = new HashSet<>();
        // AD
        sharePointGroup.getUsers().stream().filter(user -> !user.isAzureAccount()).map(GetListItemRoleResponse.User::getAccount)
                .filter(title -> title.contains("\\")).map(systemHelper::getSearchRoleByUser).forEach(titles::add);
        sharePointGroup.getSecurityGroups().stream().map(GetListItemRoleResponse.SecurityGroup::getTitle)
                .filter(title -> title.contains("\\")).map(systemHelper::getSearchRoleByGroup).forEach(titles::add);
        // AzureAD
        sharePointGroup.getUsers().stream().filter(GetListItemRoleResponse.User::isAzureAccount)
                .map(GetListItemRoleResponse.User::getAzureAccount).map(systemHelper::getSearchRoleByUser).forEach(titles::add);
        sharePointGroup.getUsers().stream().filter(GetListItemRoleResponse.User::isAzureAccount)
                .map(GetListItemRoleResponse.User::getAdAccountFromAzureAccount).map(systemHelper::getSearchRoleByUser)
                .forEach(titles::add);
        sharePointGroup.getSecurityGroups().stream().filter(GetListItemRoleResponse.SecurityGroup::isAzureAccount)
                .map(GetListItemRoleResponse.SecurityGroup::getAzureAccount).map(systemHelper::getSearchRoleByGroup).forEach(titles::add);
        sharePointGroup.getSecurityGroups().stream().filter(GetListItemRoleResponse.SecurityGroup::isAzureAccount)
                .map(GetListItemRoleResponse.SecurityGroup::getTitle).map(systemHelper::getSearchRoleByGroup).forEach(titles::add);

        sharePointGroup.getSharePointGroups().stream().flatMap(group -> getSharePointGroupTitles(group, sharePointGroupCache).stream())
                .forEach(titles::add);
        return titles;
    }

    protected String buildDigest(final String content) {
        final int maxLength = ComponentUtil.getFessConfig().getCrawlerDocumentFileMaxDigestLengthAsInteger();
        return StringUtils.abbreviate(content, maxLength);
    }
}
