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
