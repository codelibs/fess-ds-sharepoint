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
package org.codelibs.fess.ds.sharepoint.client2013.api.list.getlistitem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemRole;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;
import org.codelibs.fess.util.DocumentUtil;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * SharePoint 2013 implementation for retrieving list item role assignments.
 * This class extends GetListItemRole to provide SharePoint 2013-specific XML-based API functionality.
 */
public class GetListItemRole2013 extends GetListItemRole {
    private static final Logger logger = LogManager.getLogger(GetListItemRole2013.class);

    /** What each listing asks for per request, matching {@code GetListItemRole}. */
    private static final int PAGE_SIZE = 200;

    /**
     * Upper bound on the number of pages the role assignment and group member listings may fetch.
     * See {@code FolderCrawl} for the reasoning; both were read with a single unpaged request,
     * silently truncating an item's permissions past the server's own page size.
     */
    private static final int MAX_PAGES = 100;

    private String listId = null;
    private String itemId = null;
    private Map<String, GetListItemRole2013Response.SharePointGroup> sharePointGroupCache = null;

    /**
     * Constructs a new GetListItemRole2013 instance.
     *
     * @param client the HTTP client for making requests
     * @param siteUrl the SharePoint site URL
     * @param oAuth the OAuth authentication object
     */
    public GetListItemRole2013(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    @Override
    public GetListItemRole2013 setId(final String listId, final String itemId) {
        this.listId = listId;
        this.itemId = itemId;
        return this;
    }

    @Override
    public GetListItemRole2013 setSharePointGroupCache(
            final Map<String, GetListItemRole2013Response.SharePointGroup> sharePointGroupCache) {
        this.sharePointGroupCache = sharePointGroupCache;
        return this;
    }

    @Override
    public GetListItemRole2013Response execute() {
        if (listId == null || itemId == null) {
            throw new SharePointClientException("listId/itemId is required.");
        }

        final GetListItemRole2013Response response = new GetListItemRole2013Response();
        final List<Map<String, Object>> values = new ArrayList<>();
        int start = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            final HttpGet httpGet = new HttpGet(buildRoleAssignmentsUrl() + "?" + getPagingParam(start, PAGE_SIZE));
            final XmlResponse xmlResponse = doXmlRequest(httpGet);
            final GetListItemRoleDocHandler getListItemRoleDocHandler = new GetListItemRoleDocHandler();
            xmlResponse.parseXml(getListItemRoleDocHandler);
            final Map<String, Object> bodyMap = getListItemRoleDocHandler.getDataMap();
            @SuppressWarnings("unchecked")
            final List<Map<String, Object>> pageValues = (List<Map<String, Object>>) bodyMap.get("value");
            if (pageValues.isEmpty()) {
                break;
            }
            start += PAGE_SIZE;
            values.addAll(pageValues);
            if (pageValues.size() < PAGE_SIZE) {
                break;
            }
            if (page == MAX_PAGES - 1) {
                // Truncating here is not counted as a crawl failure, so the item is still indexed
                // with whatever permissions were read - some permissions may be missing.
                logger.warn("Stopped listing the role assignments of item {} after {} pages; the listing may be truncated.", itemId,
                        MAX_PAGES);
            }
        }
        values.stream()
                .map(value -> (DocumentUtil.getValue(value, "PrincipalId", String.class)))
                .filter(principalId -> !isLimitedAccessOnly(principalId))
                .forEach(principalId -> {
                    if (sharePointGroupCache != null && sharePointGroupCache.containsKey(principalId)) {
                        response.addSharePointGroup(sharePointGroupCache.get(principalId));
                        return;
                    }
                    final HttpGet memberRequest = new HttpGet(buildMemberUrl(principalId));
                    final XmlResponse memberResponse = doXmlRequest(memberRequest);
                    final MemberDocHandler memberDocHandler = new MemberDocHandler();
                    memberResponse.parseXml(memberDocHandler);
                    final Map<String, Object> memberResponseMap = memberDocHandler.getDataMap();
                    final String id = DocumentUtil.getValue(memberResponseMap, "Id", String.class);
                    final int principalType = DocumentUtil.getValue(memberResponseMap, "PrincipalType", Integer.class, 0);
                    switch (principalType) {
                    case 1: {
                        // User
                        final GetListItemRole2013Response.User user =
                                new GetListItemRole2013Response.User(id, DocumentUtil.getValue(memberResponseMap, "Title", String.class),
                                        DocumentUtil.getValue(memberResponseMap, "LoginName", String.class));
                        response.addUser(user);
                        break;
                    }
                    case 4: {
                        // Security Group. Nested members already handled this type; permissions
                        // assigned straight to the item did not, so an item whose only permission
                        // was an AD group was indexed with no role.
                        final GetListItemRole2013Response.SecurityGroup securityGroup = new GetListItemRole2013Response.SecurityGroup(id,
                                DocumentUtil.getValue(memberResponseMap, "Title", String.class),
                                DocumentUtil.getValue(memberResponseMap, "LoginName", String.class));
                        response.addSecurityGroup(securityGroup);
                        break;
                    }
                    case 8: {
                        final GetListItemRole2013Response.SharePointGroup sharePointGroup = new GetListItemRole2013Response.SharePointGroup(
                                id, DocumentUtil.getValue(memberResponseMap, "Title", String.class));
                        cacheAndFillSharePointGroup(principalId, sharePointGroup, id);
                        response.addSharePointGroup(sharePointGroup);
                        break;
                    }
                    default:
                        break;
                    }
                });
        return response;
    }

    @Override
    protected String buildBaseUrl() {
        return siteUrl + "_api/Web/Lists(guid'" + listId + "')/";
    }

    @Override
    protected String buildRoleAssignmentsUrl() {
        return buildBaseUrl() + "Items(" + itemId + ")/RoleAssignments";
    }

    /**
     * Builds the URL for retrieving member information by principal ID (SharePoint 2013 version).
     *
     * @param principalId the principal ID of the member
     * @return the complete URL for member API
     */
    protected String buildMemberUrl(final String principalId) {
        return buildBaseUrl() + "RoleAssignments/GetByPrincipalId(" + principalId + ")/Member";
    }

    @Override
    protected String buildUsersUrl(final String memberId) {
        return siteUrl + "_api/Web/SiteGroups/GetById(" + memberId + ")/Users";
    }

    /**
     * Builds the URL for retrieving role definition bindings by principal ID.
     *
     * @param principalId the principal ID to get role definition bindings for
     * @return the complete URL for role definition bindings API
     */
    protected String buildRoleDefinitionBindingsUrl(final String principalId) {
        return buildBaseUrl() + "Items(" + itemId + ")/RoleAssignments/GetByPrincipalId(" + principalId + ")/RoleDefinitionBindings";
    }

    boolean isLimitedAccessOnly(final String principalId) {
        final HttpGet httpGet = new HttpGet(buildRoleDefinitionBindingsUrl(principalId));
        final XmlResponse xmlResponse = doXmlRequest(httpGet);
        final RoleDefinitionBindingsDocHandler docHandler = new RoleDefinitionBindingsDocHandler();
        xmlResponse.parseXml(docHandler);
        final List<Integer> roleTypeKinds = docHandler.getRoleTypeKinds();
        if (roleTypeKinds.isEmpty()) {
            return true;
        }
        for (final int roleTypeKind : roleTypeKinds) {
            if (roleTypeKind > 1) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected GetListItemRole2013Response.SharePointGroup buildSharePointGroup(final String id, final String title) {
        // SharePointGroup
        final GetListItemRole2013Response.SharePointGroup sharePointGroup = new GetListItemRole2013Response.SharePointGroup(id, title);
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
    private void cacheAndFillSharePointGroup(final String cacheKey, final GetListItemRole2013Response.SharePointGroup sharePointGroup,
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
    private void fillSharePointGroup(final GetListItemRole2013Response.SharePointGroup sharePointGroup, final String id) {
        final List<Map<String, Object>> usersList = new ArrayList<>();
        int start = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            final HttpGet usersRequest = new HttpGet(buildUsersUrl(id) + "?" + getPagingParam(start, PAGE_SIZE));
            final XmlResponse usersResponse = doXmlRequest(usersRequest);
            final UsersDocHandler usersDocHandler = new UsersDocHandler();
            usersResponse.parseXml(usersDocHandler);
            final Map<String, Object> usersResponseMap = usersDocHandler.getDataMap();
            @SuppressWarnings("unchecked")
            final List<Map<String, Object>> users = (List<Map<String, Object>>) usersResponseMap.get("value");
            if (users == null || users.isEmpty()) {
                break;
            }
            start += PAGE_SIZE;
            usersList.addAll(users);
            if (users.size() < PAGE_SIZE) {
                break;
            }
            if (page == MAX_PAGES - 1) {
                logger.warn("Stopped listing the members of SharePoint group {} after {} pages; the listing may be truncated.", id,
                        MAX_PAGES);
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
                final GetListItemRole2013Response.User userUser = new GetListItemRole2013Response.User(userId, userTitle, loginName);
                sharePointGroup.addUser(userUser);
                break;
            case 4:
                // Security Group
                final GetListItemRole2013Response.SecurityGroup securityGroup =
                        new GetListItemRole2013Response.SecurityGroup(userId, userTitle, loginName);
                sharePointGroup.addSecurityGroup(securityGroup);
                break;
            case 8:
                if (sharePointGroupCache != null && sharePointGroupCache.containsKey(userId)) {
                    sharePointGroup.addSharePointGroup(sharePointGroupCache.get(userId));
                } else {
                    final GetListItemRole2013Response.SharePointGroup userSharePointGroup =
                            new GetListItemRole2013Response.SharePointGroup(userId, userTitle);
                    cacheAndFillSharePointGroup(userId, userSharePointGroup, userId);
                    sharePointGroup.addSharePointGroup(userSharePointGroup);
                }
                break;
            default:
                break;
            }
        });
    }

    private static class GetListItemRoleDocHandler extends DefaultHandler {
        private final Map<String, Object> dataMap = new HashMap<>();
        private Map<String, Object> resultMap = null;

        private String fieldName;

        private final StringBuilder buffer = new StringBuilder(1000);

        @Override
        public void startDocument() {
            dataMap.clear();
            dataMap.put("value", new ArrayList<>());
        }

        @Override
        public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) {
            if ("entry".equals(qName)) {
                resultMap = new HashMap<>();
            } else if ("d:PrincipalId".equals(qName)) {
                fieldName = "PrincipalId";
                buffer.setLength(0);
            }
        }

        @Override
        public void characters(final char[] ch, final int offset, final int length) {
            if (fieldName != null) {
                buffer.append(new String(ch, offset, length));
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public void endElement(final String uri, final String localName, final String qName) {
            if ("entry".equals(qName)) {
                if (resultMap != null) {
                    ((List) dataMap.get("value")).add(resultMap);
                }
                resultMap = null;
            } else if (resultMap != null && fieldName != null) {
                if (!resultMap.containsKey(fieldName)) {
                    resultMap.put(fieldName, buffer.toString());
                }
                fieldName = null;
            }
        }

        @Override
        public void endDocument() {
            // nothing
        }

        public Map<String, Object> getDataMap() {
            return dataMap;
        }
    }

    private static class MemberDocHandler extends DefaultHandler {
        private final Map<String, Object> dataMap = new HashMap<>();

        private String fieldName;

        private final StringBuilder buffer = new StringBuilder(1000);

        @Override
        public void startDocument() {
            dataMap.clear();
            dataMap.put("value", new ArrayList<>());
        }

        @Override
        public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) {
            if ("d:Id".equals(qName)) {
                fieldName = "Id";
                buffer.setLength(0);
            } else if ("d:Title".equals(qName)) {
                fieldName = "Title";
                buffer.setLength(0);
            } else if ("d:PrincipalType".equals(qName)) {
                fieldName = "PrincipalType";
                buffer.setLength(0);
            } else if ("d:LoginName".equals(qName)) {
                fieldName = "LoginName";
                buffer.setLength(0);
            }
        }

        @Override
        public void characters(final char[] ch, final int offset, final int length) {
            if (fieldName != null) {
                buffer.append(new String(ch, offset, length));
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public void endElement(final String uri, final String localName, final String qName) {
            if (fieldName != null) {
                if (!dataMap.containsKey(fieldName)) {
                    dataMap.put(fieldName, buffer.toString());
                }
                fieldName = null;
            }
        }

        @Override
        public void endDocument() {
            // nothing
        }

        public Map<String, Object> getDataMap() {
            return dataMap;
        }
    }

    private static class UsersDocHandler extends DefaultHandler {
        private final Map<String, Object> dataMap = new HashMap<>();
        private Map<String, Object> resultMap = null;

        private String fieldName;

        private final StringBuilder buffer = new StringBuilder(1000);

        @Override
        public void startDocument() {
            dataMap.clear();
            dataMap.put("value", new ArrayList<>());
        }

        @Override
        public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) {
            if ("entry".equals(qName)) {
                resultMap = new HashMap<>();
            } else if ("d:Id".equals(qName)) {
                fieldName = "Id";
                buffer.setLength(0);
            } else if ("d:Title".equals(qName)) {
                fieldName = "Title";
                buffer.setLength(0);
            } else if ("d:PrincipalType".equals(qName)) {
                fieldName = "PrincipalType";
                buffer.setLength(0);
            } else if ("d:LoginName".equals(qName)) {
                fieldName = "LoginName";
                buffer.setLength(0);
            }
        }

        @Override
        public void characters(final char[] ch, final int offset, final int length) {
            if (fieldName != null) {
                buffer.append(new String(ch, offset, length));
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public void endElement(final String uri, final String localName, final String qName) {
            if ("entry".equals(qName)) {
                if (resultMap != null) {
                    ((List) dataMap.get("value")).add(resultMap);
                }
                resultMap = null;
            } else if (resultMap != null && fieldName != null) {
                if (!resultMap.containsKey(fieldName)) {
                    resultMap.put(fieldName, buffer.toString());
                }
                fieldName = null;
            }
        }

        @Override
        public void endDocument() {
            // nothing
        }

        public Map<String, Object> getDataMap() {
            return dataMap;
        }
    }

    static class RoleDefinitionBindingsDocHandler extends DefaultHandler {
        private final List<Integer> roleTypeKinds = new ArrayList<>();

        private String fieldName;

        private final StringBuilder buffer = new StringBuilder(1000);

        @Override
        public void startElement(final String uri, final String localName, final String qName, final Attributes attributes) {
            if ("d:RoleTypeKind".equals(qName)) {
                fieldName = "RoleTypeKind";
                buffer.setLength(0);
            }
        }

        @Override
        public void characters(final char[] ch, final int offset, final int length) {
            if (fieldName != null) {
                buffer.append(new String(ch, offset, length));
            }
        }

        @Override
        public void endElement(final String uri, final String localName, final String qName) {
            if ("d:RoleTypeKind".equals(qName) && fieldName != null) {
                try {
                    roleTypeKinds.add(Integer.parseInt(buffer.toString().trim()));
                } catch (final NumberFormatException e) {
                    // ignore
                }
                fieldName = null;
            }
        }

        public List<Integer> getRoleTypeKinds() {
            return roleTypeKinds;
        }
    }
}
