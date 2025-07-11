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
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemRole;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;
import org.codelibs.fess.util.DocumentUtil;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

public class GetListItemRole2013 extends GetListItemRole {
    private String listId = null;
    private String itemId = null;
    private Map<String, GetListItemRole2013Response.SharePointGroup> sharePointGroupCache = null;

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

        final HttpGet httpGet = new HttpGet(buildRoleAssignmentsUrl());
        final XmlResponse xmlResponse = doXmlRequest(httpGet);

        final GetListItemRole2013Response response = new GetListItemRole2013Response();
        final GetListItemRoleDocHandler getListItemRoleDocHandler = new GetListItemRoleDocHandler();
        xmlResponse.parseXml(getListItemRoleDocHandler);
        final Map<String, Object> bodyMap = getListItemRoleDocHandler.getDataMap();
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> values = (List<Map<String, Object>>) bodyMap.get("value");
        values.stream().map(value -> (DocumentUtil.getValue(value, "PrincipalId", String.class))).forEach(principalId -> {
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
            if (principalType == 1) {
                // User
                final GetListItemRole2013Response.User user =
                        new GetListItemRole2013Response.User(id, DocumentUtil.getValue(memberResponseMap, "Title", String.class),
                                DocumentUtil.getValue(memberResponseMap, "LoginName", String.class));
                response.addUser(user);
            } else if (principalType == 8) {
                final GetListItemRole2013Response.SharePointGroup sharePointGroup =
                        buildSharePointGroup(id, DocumentUtil.getValue(memberResponseMap, "Title", String.class));
                response.addSharePointGroup(sharePointGroup);
                if (sharePointGroupCache != null) {
                    sharePointGroupCache.put(principalId, sharePointGroup);
                }
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

    protected String buildMemberUrl(final String principalId) {
        return buildBaseUrl() + "RoleAssignments/GetByPrincipalId(" + principalId + ")/Member";
    }

    @Override
    protected String buildUsersUrl(final String memberId) {
        return siteUrl + "_api/Web/SiteGroups/GetById(" + memberId + ")/Users";
    }

    @Override
    protected GetListItemRole2013Response.SharePointGroup buildSharePointGroup(final String id, final String title) {
        // SharePointGroup
        final GetListItemRole2013Response.SharePointGroup sharePointGroup = new GetListItemRole2013Response.SharePointGroup(id, title);
        final HttpGet usersRequest = new HttpGet(buildUsersUrl(id));
        final XmlResponse usersResponse = doXmlRequest(usersRequest);
        final UsersDocHandler usersDocHandler = new UsersDocHandler();
        usersResponse.parseXml(usersDocHandler);
        final Map<String, Object> usersResponseMap = usersDocHandler.getDataMap();
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> usersList = (List<Map<String, Object>>) usersResponseMap.get("value");
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
                    final GetListItemRole2013Response.SharePointGroup userSharePointGroup = buildSharePointGroup(userId, title);
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
}
