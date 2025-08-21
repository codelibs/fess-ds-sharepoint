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
package org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;
import org.codelibs.fess.util.DocumentUtil;

/**
 * API class for retrieving SharePoint list item role assignments.
 * This class handles REST API calls to get role assignment information for a specific list item,
 * including users, SharePoint groups, and security groups that have access to the item.
 */
public class GetListItemRole extends SharePointApi<GetListItemRoleResponse> {
    private static final Logger logger = LogManager.getLogger(GetListItemRole.class);

    private static final String PAGING_PARAM = "%24skip={{start}}&%24top={{num}}";
    private static final int PAGE_SISE = 200;

    private String listId = null;
    private String itemId = null;
    private Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache = null;

    /**
     * Constructs a new GetListItemRole instance.
     *
     * @param client the HTTP client for making requests
     * @param siteUrl the SharePoint site URL
     * @param oAuth the OAuth authentication object
     */
    public GetListItemRole(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    /**
     * Sets the list ID and item ID for the role assignments to retrieve.
     *
     * @param listId the GUID of the SharePoint list
     * @param itemId the ID of the list item
     * @return this GetListItemRole instance for method chaining
     */
    public GetListItemRole setId(final String listId, final String itemId) {
        this.listId = listId;
        this.itemId = itemId;
        return this;
    }

    /**
     * Sets the SharePoint group cache to improve performance by avoiding duplicate API calls.
     *
     * @param sharePointGroupCache a map of SharePoint groups keyed by principal ID
     * @return this GetListItemRole instance for method chaining
     */
    public GetListItemRole setSharePointGroupCache(final Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache) {
        this.sharePointGroupCache = sharePointGroupCache;
        return this;
    }

    @Override
    public GetListItemRoleResponse execute() {
        if (listId == null || itemId == null) {
            throw new SharePointClientException("listId/itemId is required.");
        }
        final GetListItemRoleResponse response = new GetListItemRoleResponse();
        int start = 0;
        while (true) {
            final GetListItemRoleResponse getListItemRoleResponse = executeInternal(start, PAGE_SISE);
            if (getListItemRoleResponse.getUsers().isEmpty() && getListItemRoleResponse.getSharePointGroups().isEmpty()
                    && getListItemRoleResponse.getSecurityGroups().isEmpty()) {
                break;
            }
            getListItemRoleResponse.getUsers().stream().forEach(response::addUser);
            getListItemRoleResponse.getSharePointGroups().stream().forEach(response::addSharePointGroup);
            getListItemRoleResponse.getSecurityGroups().stream().forEach(response::addSecurityGroup);
            start += PAGE_SISE;
        }
        return response;
    }

    /**
     * Executes the role assignment retrieval for a specific page of results.
     *
     * @param start the starting index for pagination
     * @param num the number of items to retrieve
     * @return a GetListItemRoleResponse containing the role assignments for this page
     */
    protected GetListItemRoleResponse executeInternal(final int start, final int num) {
        final String buildUrl = buildRoleAssignmentsUrl() + "?" + getPagingParam(start, num);
        if (logger.isDebugEnabled()) {
            logger.debug("buildUrl: {}", buildUrl);
        }
        final HttpGet httpGet = new HttpGet(buildUrl);
        final JsonResponse jsonResponse = doJsonRequest(httpGet);

        final GetListItemRoleResponse response = new GetListItemRoleResponse();
        final Map<String, Object> bodyMap = jsonResponse.getBodyAsMap();
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> values = (List<Map<String, Object>>) bodyMap.get("value");
        values.stream().map(value -> (value.get("PrincipalId").toString())).forEach(principalId -> {
            if (sharePointGroupCache != null && sharePointGroupCache.containsKey(principalId)) {
                response.addSharePointGroup(sharePointGroupCache.get(principalId));
                return;
            }
            final String buildMemberUrl = buildMemberUrl(itemId, principalId);
            if (logger.isDebugEnabled()) {
                logger.debug("buildMemberUrl: {}", buildMemberUrl);
            }
            final HttpGet memberRequest = new HttpGet(buildMemberUrl);
            final JsonResponse memberResponse = doJsonRequest(memberRequest);
            final Map<String, Object> memberResponseMap = memberResponse.getBodyAsMap();
            final String id = DocumentUtil.getValue(memberResponseMap, "Id", String.class);
            final int principalType = DocumentUtil.getValue(memberResponseMap, "PrincipalType", Integer.class, 0);
            switch (principalType) {
            case 1:
                // User
                final GetListItemRoleResponse.User user =
                        new GetListItemRoleResponse.User(id, DocumentUtil.getValue(memberResponseMap, "Title", String.class),
                                DocumentUtil.getValue(memberResponseMap, "LoginName", String.class));
                response.addUser(user);
                break;
            case 4:
                // Security Group
                final GetListItemRoleResponse.SecurityGroup securityGroup =
                        new GetListItemRoleResponse.SecurityGroup(id, DocumentUtil.getValue(memberResponseMap, "Title", String.class),
                                DocumentUtil.getValue(memberResponseMap, "LoginName", String.class));
                response.addSecurityGroup(securityGroup);
                break;
            case 8:
                final GetListItemRoleResponse.SharePointGroup sharePointGroup =
                        buildSharePointGroup(id, DocumentUtil.getValue(memberResponseMap, "Title", String.class));
                response.addSharePointGroup(sharePointGroup);
                if (sharePointGroupCache != null) {
                    sharePointGroupCache.put(principalId, sharePointGroup);
                }
                break;
            default:
                break;
            }
        });
        return response;
    }

    /**
     * Builds the base URL for list-related API calls.
     *
     * @return the base URL for the list API
     */
    protected String buildBaseUrl() {
        return siteUrl + "/_api/Web/Lists(guid'" + listId + "')/";
    }

    /**
     * Builds the URL for retrieving role assignments of the list item.
     *
     * @return the complete URL for role assignments API
     */
    protected String buildRoleAssignmentsUrl() {
        return buildBaseUrl() + "Items(" + itemId + ")/RoleAssignments";
    }

    /**
     * Builds the URL for retrieving member information by principal ID.
     *
     * @param itemId the ID of the list item
     * @param principalId the principal ID of the member
     * @return the complete URL for member API
     */
    protected String buildMemberUrl(final String itemId, final String principalId) {
        return buildBaseUrl() + "Items(" + itemId + ")/RoleAssignments/GetByPrincipalId(" + principalId + ")/Member";
    }

    /**
     * Builds the URL for retrieving users in a SharePoint group.
     *
     * @param memberId the ID of the SharePoint group
     * @return the complete URL for group users API
     */
    protected String buildUsersUrl(final String memberId) {
        return siteUrl + "/_api/Web/SiteGroups/GetById(" + memberId + ")/Users";
    }

    /**
     * Gets the pagination parameters for API calls.
     *
     * @param start the starting index
     * @param num the number of items to retrieve
     * @return the pagination parameter string
     */
    protected String getPagingParam(final int start, final int num) {
        return PAGING_PARAM.replace("{{start}}", String.valueOf(start)).replace("{{num}}", String.valueOf(num));
    }

    /**
     * Builds a SharePointGroup object with its nested users and groups.
     *
     * @param id the ID of the SharePoint group
     * @param title the title of the SharePoint group
     * @return a fully populated SharePointGroup object
     */
    protected GetListItemRoleResponse.SharePointGroup buildSharePointGroup(final String id, final String title) {
        // SharePointGroup
        final GetListItemRoleResponse.SharePointGroup sharePointGroup = new GetListItemRoleResponse.SharePointGroup(id, title);
        final List<Map<String, Object>> usersList = new ArrayList<>();

        /* TODO need paging?
        int start = 0;
        while(true) {
            final HttpGet usersRequest = new HttpGet(buildUsersUrl(id) + getPagingParam(start, PAGE_SISE));
            final JsonResponse usersResponse = doJsonRequest(usersRequest);
            final Map<String, Object> usersResponseMap = usersResponse.getBodyAsMap();
            List<Map<String, Object>> users = (List) usersResponseMap.get("value");
            if (users.size() == 0) {
                break;
            }
            usersList.addAll(users);
            start += PAGE_SISE;
        }
         */
        final String buildUsersUrl = buildUsersUrl(id);
        if (logger.isDebugEnabled()) {
            logger.debug("buildUsersUrl: {}", buildUsersUrl);
        }
        final HttpGet usersRequest = new HttpGet(buildUsersUrl);
        final JsonResponse usersResponse = doJsonRequest(usersRequest);
        final Map<String, Object> usersResponseMap = usersResponse.getBodyAsMap();
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> users = (List<Map<String, Object>>) usersResponseMap.get("value");
        usersList.addAll(users);
        usersList.forEach(user -> {
            final String userId = DocumentUtil.getValue(user, "Id", String.class);
            final String userTitle = DocumentUtil.getValue(user, "Title", String.class);
            final String loginName = DocumentUtil.getValue(user, "LoginName", String.class);
            final int userPrincipalType = DocumentUtil.getValue(user, "PrincipalType", Integer.class, 0);
            switch (userPrincipalType) {
            case 1:
                // user
                final GetListItemRoleResponse.User userUser = new GetListItemRoleResponse.User(userId, userTitle, loginName);
                sharePointGroup.addUser(userUser);
                break;
            case 4:
                // Security Group
                final GetListItemRoleResponse.SecurityGroup securityGroup =
                        new GetListItemRoleResponse.SecurityGroup(userId, userTitle, loginName);
                sharePointGroup.addSecurityGroup(securityGroup);
                break;
            case 8:
                // SharePoint Group
                if (sharePointGroupCache != null && sharePointGroupCache.containsKey(userId)) {
                    sharePointGroup.addSharePointGroup(sharePointGroupCache.get(userId));
                } else {
                    final GetListItemRoleResponse.SharePointGroup userSharePointGroup = buildSharePointGroup(userId, title);
                    sharePointGroup.addSharePointGroup(userSharePointGroup);
                    if (sharePointGroupCache != null) {
                        sharePointGroupCache.put(userId, userSharePointGroup);
                    }
                }
                break;
            default:
                break;
            }
        });
        return sharePointGroup;
    }
}
