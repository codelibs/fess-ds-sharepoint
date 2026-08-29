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

    /**
     * Upper bound on the number of member pages fetched for one SharePoint group. A server that
     * ignores the paging parameters would otherwise keep returning the same full page forever.
     */
    private static final int MAX_MEMBER_PAGES = 100;

    /**
     * Upper bound on the number of pages the role assignment listing may fetch, for the same
     * reason as {@link #MAX_MEMBER_PAGES}. At {@link #PAGE_SISE} assignments per request it allows
     * 20,000 role assignments on a single item.
     */
    private static final int MAX_PAGES = 100;

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
        final List<Map<String, Object>> values = new ArrayList<>();
        int start = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            final List<Map<String, Object>> pageValues = getRoleAssignmentPage(start, PAGE_SISE);
            if (pageValues.isEmpty()) {
                break;
            }
            start += PAGE_SISE;
            values.addAll(pageValues);
            if (pageValues.size() < PAGE_SISE) {
                break;
            }
            if (page == MAX_PAGES - 1) {
                logger.warn("Stopped listing the role assignments of item {} after {} pages; the listing may be truncated.", itemId,
                        MAX_PAGES);
            }
        }
        addRoleAssignments(values, response);
        return response;
    }

    /**
     * Fetches and resolves one page of the item's role assignments.
     *
     * <p>{@link #execute()} no longer calls this method: it now tells an empty raw page (the true
     * end of the listing) apart from a page that is merely empty after {@link
     * #isLimitedAccessOnly} filtering, by fetching raw pages with {@link
     * #getRoleAssignmentPage(int, int)} directly and resolving them with {@link
     * #addRoleAssignments(List, GetListItemRoleResponse)} only once the whole raw listing is
     * collected. This method is kept, unchanged in behavior, because it is {@code protected} and
     * therefore part of this class's contract for subclasses and external callers.
     *
     * @param start the starting index for pagination
     * @param num the number of items to retrieve
     * @return a GetListItemRoleResponse containing the resolved role assignments for this page
     */
    protected GetListItemRoleResponse executeInternal(final int start, final int num) {
        final GetListItemRoleResponse r = new GetListItemRoleResponse();
        addRoleAssignments(getRoleAssignmentPage(start, num), r);
        return r;
    }

    /**
     * Fetches one page of the item's role assignments, as returned by the server.
     *
     * <p>The listing ends on this raw page rather than on what survives
     * {@link #isLimitedAccessOnly}: a whole page of Limited Access assignments is routine on a
     * list with broken inheritance, and treating it as the end of the listing dropped every role
     * assignment on the pages after it.
     *
     * @param start the starting index for pagination
     * @param num the number of assignments to retrieve
     * @return the role assignments on this page, unfiltered
     */
    private List<Map<String, Object>> getRoleAssignmentPage(final int start, final int num) {
        final String buildUrl = buildRoleAssignmentsUrl() + "?" + getPagingParam(start, num) + "&%24expand=RoleDefinitionBindings";
        if (logger.isDebugEnabled()) {
            logger.debug("buildUrl: {}", buildUrl);
        }
        final HttpGet httpGet = new HttpGet(buildUrl);
        final JsonResponse jsonResponse = doJsonRequest(httpGet);
        final Map<String, Object> bodyMap = jsonResponse.getBodyAsMap();
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> values = (List<Map<String, Object>>) bodyMap.get("value");
        return values;
    }

    /**
     * Resolves the principals named by the given role assignments and adds them to the response.
     *
     * @param values the role assignments to resolve, as returned by the server
     * @param response the response to add the resolved principals to
     */
    private void addRoleAssignments(final List<Map<String, Object>> values, final GetListItemRoleResponse response) {
        values.stream()
                .filter(value -> !isLimitedAccessOnly(value))
                .map(value -> (value.get("PrincipalId").toString()))
                .forEach(principalId -> {
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
                        final GetListItemRoleResponse.SecurityGroup securityGroup = new GetListItemRoleResponse.SecurityGroup(id,
                                DocumentUtil.getValue(memberResponseMap, "Title", String.class),
                                DocumentUtil.getValue(memberResponseMap, "LoginName", String.class));
                        response.addSecurityGroup(securityGroup);
                        break;
                    case 8:
                        final GetListItemRoleResponse.SharePointGroup sharePointGroup = new GetListItemRoleResponse.SharePointGroup(id,
                                DocumentUtil.getValue(memberResponseMap, "Title", String.class));
                        cacheAndFillSharePointGroup(principalId, sharePointGroup, id);
                        response.addSharePointGroup(sharePointGroup);
                        break;
                    default:
                        break;
                    }
                });
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

    @SuppressWarnings("unchecked")
    boolean isLimitedAccessOnly(final Map<String, Object> roleAssignment) {
        final List<Map<String, Object>> bindings = (List<Map<String, Object>>) roleAssignment.get("RoleDefinitionBindings");
        if (bindings == null || bindings.isEmpty()) {
            return true;
        }
        for (final Map<String, Object> binding : bindings) {
            final Object roleTypeKindObj = binding.get("RoleTypeKind");
            if (roleTypeKindObj != null) {
                final int roleTypeKind = (roleTypeKindObj instanceof Number) ? ((Number) roleTypeKindObj).intValue()
                        : Integer.parseInt(roleTypeKindObj.toString());
                if (roleTypeKind > 1) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Builds a SharePointGroup object with its nested users and groups.
     *
     * <p>This class now creates the group and calls {@link #fillSharePointGroup} itself so that
     * the group reaches the cache before its members are read. The method is kept because the
     * SharePoint 2013 API overrides it and calls it in place of that split.
     *
     * @param id the ID of the SharePoint group
     * @param title the title of the SharePoint group
     * @return a fully populated SharePointGroup object
     */
    protected GetListItemRoleResponse.SharePointGroup buildSharePointGroup(final String id, final String title) {
        // SharePointGroup
        final GetListItemRoleResponse.SharePointGroup sharePointGroup = new GetListItemRoleResponse.SharePointGroup(id, title);
        fillSharePointGroup(sharePointGroup, id);
        return sharePointGroup;
    }

    /**
     * Registers a still-empty SharePoint group in the cache and then reads its members into it.
     *
     * <p>The group has to reach the cache before the descent, because SharePoint lets groups
     * contain each other and the cache is what cuts the cycle. It must not stay there if the
     * descent fails: the cache is shared by the whole crawl, and the crawler retries a failed
     * crawl with that same cache, so a half-read group left behind would be handed to the retry
     * and to every later item the group protects, each of them silently indexed without any of
     * this group's permissions. Removing the entry costs one rebuild; keeping it costs the roles.
     *
     * @param cacheKey the key this group is cached under
     * @param sharePointGroup the group to register and populate
     * @param id the ID of the SharePoint group, used to read its members
     */
    private void cacheAndFillSharePointGroup(final String cacheKey, final GetListItemRoleResponse.SharePointGroup sharePointGroup,
            final String id) {
        if (sharePointGroupCache != null) {
            sharePointGroupCache.put(cacheKey, sharePointGroup);
        }
        try {
            fillSharePointGroup(sharePointGroup, id);
        } catch (final RuntimeException e) {
            if (sharePointGroupCache != null) {
                sharePointGroupCache.remove(cacheKey);
            }
            throw e;
        }
    }

    /**
     * Fetches the members of a SharePoint group and adds them to it.
     *
     * <p>Split out from {@link #buildSharePointGroup(String, String)} so that a caller can put the
     * still-empty group into {@code sharePointGroupCache} before descending into its members. A
     * SharePoint group may contain another group that contains the first one again, and the cache
     * is the only thing that stops that cycle, so an entry added after the descent is added too
     * late to help.
     *
     * @param sharePointGroup the group to populate
     * @param id the ID of the SharePoint group
     */
    private void fillSharePointGroup(final GetListItemRoleResponse.SharePointGroup sharePointGroup, final String id) {
        final List<Map<String, Object>> usersList = new ArrayList<>();
        int start = 0;
        for (int page = 0; page < MAX_MEMBER_PAGES; page++) {
            final String buildUsersUrl = buildUsersUrl(id) + "?" + getPagingParam(start, PAGE_SISE);
            if (logger.isDebugEnabled()) {
                logger.debug("buildUsersUrl: {}", buildUsersUrl);
            }
            final HttpGet usersRequest = new HttpGet(buildUsersUrl);
            final JsonResponse usersResponse = doJsonRequest(usersRequest);
            final Map<String, Object> usersResponseMap = usersResponse.getBodyAsMap();
            @SuppressWarnings("unchecked")
            final List<Map<String, Object>> users = (List<Map<String, Object>>) usersResponseMap.get("value");
            if (users == null || users.isEmpty()) {
                break;
            }
            start += PAGE_SISE;
            usersList.addAll(users);
            if (users.size() < PAGE_SISE) {
                break;
            }
            if (page == MAX_MEMBER_PAGES - 1) {
                logger.warn("Stopped listing the members of SharePoint group {} after {} pages; the listing may be truncated.", id,
                        MAX_MEMBER_PAGES);
            }
        }
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
                    final GetListItemRoleResponse.SharePointGroup userSharePointGroup =
                            new GetListItemRoleResponse.SharePointGroup(userId, userTitle);
                    cacheAndFillSharePointGroup(userId, userSharePointGroup, userId);
                    sharePointGroup.addSharePointGroup(userSharePointGroup);
                }
                break;
            default:
                break;
            }
        });
    }
}
