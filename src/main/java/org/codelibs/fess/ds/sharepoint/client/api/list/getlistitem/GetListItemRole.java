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
package org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;

import java.util.List;
import java.util.Map;

public class GetListItemRole extends SharePointApi<GetListItemRoleResponse> {
    private String listId = null;
    private String itemId = null;
    private Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache = null;

    public GetListItemRole(CloseableHttpClient client, String siteUrl) {
        super(client, siteUrl);
    }

    public GetListItemRole setId(String listId, String itemId) {
        this.listId = listId;
        this.itemId = itemId;
        return this;
    }

    public GetListItemRole setSharePointGroupCache(Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache) {
        this.sharePointGroupCache = sharePointGroupCache;
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public GetListItemRoleResponse execute() {
        if (listId == null || itemId == null) {
            throw new SharePointClientException("listId/itemId is required.");
        }

        final HttpGet httpGet = new HttpGet(buildRoleAssignmentsUrl());
        final JsonResponse jsonResponse = doJsonRequest(httpGet);

        final GetListItemRoleResponse response = new GetListItemRoleResponse();
        final Map<String, Object> bodyMap = jsonResponse.getBodyAsMap();
        List<Map<String, Object>> values = (List)bodyMap.get("value");
        values.stream().map(value -> (value.get("PrincipalId").toString())).forEach(principalId -> {
            if (sharePointGroupCache != null && sharePointGroupCache.containsKey(principalId)) {
                response.addSharePointGroup(sharePointGroupCache.get(principalId));
                return;
            }
            final HttpGet memberRequest = new HttpGet(buildMemberUrl(principalId));
            final JsonResponse memberResponse = doJsonRequest(memberRequest);
            final Map<String, Object> memberResponseMap = memberResponse.getBodyAsMap();
            final String id = memberResponseMap.get("Id").toString();
            final int principalType = Integer.valueOf(memberResponseMap.get("PrincipalType").toString());
            if (principalType == 1) {
                // User
                GetListItemRoleResponse.User user = new GetListItemRoleResponse.User(id, memberResponseMap.get("Title").toString());
                response.addUser(user);
            } else if (principalType == 8) {
                GetListItemRoleResponse.SharePointGroup sharePointGroup = buildSharePointGroup(id, memberResponseMap.get("Title").toString());
                response.addSharePointGroup(sharePointGroup);
                if (sharePointGroupCache != null) {
                    sharePointGroupCache.put(principalId, sharePointGroup);
                }
            }
        });
        return response;
    }

    private String buildBaseUrl() {
        return siteUrl + "/_api/Web/Lists(guid'" + listId + "')/";
    }

    private String buildRoleAssignmentsUrl() {
        return buildBaseUrl() + "Items(" + itemId + ")/RoleAssignments";
    }

    private String buildMemberUrl(String principalId) {
        return buildBaseUrl() + "RoleAssignments/GetByPrincipalId(" + principalId + ")/Member";
    }

    private String buildUsersUrl(String memberId) {
        return siteUrl + "/_api/Web/SiteGroups/GetById(" + memberId + ")/Users";
    }

    private GetListItemRoleResponse.SharePointGroup buildSharePointGroup(String id, String title) {
        // SharePointGroup
        final GetListItemRoleResponse.SharePointGroup sharePointGroup = new GetListItemRoleResponse.SharePointGroup(id, title);
        final HttpGet usersRequest = new HttpGet(buildUsersUrl(id));
        final JsonResponse usersResponse = doJsonRequest(usersRequest);
        final Map<String, Object> usersResponseMap = usersResponse.getBodyAsMap();
        List<Map<String, Object>> usersList = (List)usersResponseMap.get("value");
        usersList.forEach(user -> {
            String userId = user.get("Id").toString();
            String userTitle = user.get("Title").toString();
            int userPrincipalType = Integer.valueOf(user.get("PrincipalType").toString());
            if (userPrincipalType == 1) {
                // user
                GetListItemRoleResponse.User userUser = new GetListItemRoleResponse.User(userId, userTitle);
                sharePointGroup.addUser(userUser);
            } else if (userPrincipalType == 4) {
                // Security Group
                GetListItemRoleResponse.SecurityGroup securityGroup = new GetListItemRoleResponse.SecurityGroup(userId, userTitle);
                sharePointGroup.addSecurityGroup(securityGroup);
            } else if (userPrincipalType == 8) {
                // SharePoint Group
                if (sharePointGroupCache != null && sharePointGroupCache.containsKey(userId)) {
                    sharePointGroup.addSharePointGroup(sharePointGroupCache.get(userId));
                } else {
                    GetListItemRoleResponse.SharePointGroup userSharePointGroup = buildSharePointGroup(userId, title);
                    sharePointGroup.addSharePointGroup(userSharePointGroup);
                    if (sharePointGroupCache != null) {
                        sharePointGroupCache.put(userId, userSharePointGroup);
                    }
                }
            }
        });
        return sharePointGroup;
    }
}
