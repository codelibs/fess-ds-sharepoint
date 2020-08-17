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
package org.codelibs.fess.ds.sharepoint.crawl;

import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemRoleResponse;

import java.util.*;
import java.util.stream.Collectors;

public abstract class SharePointCrawl {
    protected final SharePointClient client;

    public SharePointCrawl(SharePointClient client) {
        this.client = client;
    }

    abstract public Map<String, Object> doCrawl(final Queue<SharePointCrawl> crawlingQueue);

    protected List<String> getItemRoles(String listId, String itemId, Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache) {
        final GetListItemRoleResponse getListItemRoleResponse = client.api().list().getListItemRole()
                .setId(listId, itemId)
                .setSharePointGroupCache(sharePointGroupCache)
                .execute();
        final Set<String> roles = new HashSet<>();
        getListItemRoleResponse.getUsers().stream().map(GetListItemRoleResponse.User::getTitle)
                .filter(title -> title.contains("\\"))
                .map(title -> "1" + title.substring(title.indexOf("\\") + 1))
                .forEach(roles::add);
        getListItemRoleResponse.getSharePointGroups().stream()
                .flatMap(group -> getSharePointGroupTitles(group, sharePointGroupCache).stream())
                .forEach(roles::add);
        return roles.stream().collect(Collectors.toUnmodifiableList());
    }

    private Set<String> getSharePointGroupTitles(final GetListItemRoleResponse.SharePointGroup sharePointGroup, Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache) {
        final Set<String> titles = new HashSet<>();
        sharePointGroup.getUsers().stream().map(GetListItemRoleResponse.User::getTitle)
                .filter(title -> title.contains("\\"))
                .map(title -> "1" + title.substring(title.indexOf("\\") + 1))
                .forEach(titles::add);
        sharePointGroup.getSecurityGroups().stream().map(GetListItemRoleResponse.SecurityGroup::getTitle)
                .filter(title -> title.contains("\\"))
                .map(title -> "2" + title.substring(title.indexOf("\\") + 1))
                .forEach(titles::add);
        sharePointGroup.getSharePointGroups().stream().flatMap(group -> getSharePointGroupTitles(group, sharePointGroupCache).stream())
                .forEach(titles::add);
        return titles;
    }


}
