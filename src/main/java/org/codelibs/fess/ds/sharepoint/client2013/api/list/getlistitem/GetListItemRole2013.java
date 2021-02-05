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
package org.codelibs.fess.ds.sharepoint.client2013.api.list.getlistitem;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemRole;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GetListItemRole2013 extends GetListItemRole {
    private String listId = null;
    private String itemId = null;
    private Map<String, GetListItemRole2013Response.SharePointGroup> sharePointGroupCache = null;

    public GetListItemRole2013(CloseableHttpClient client, String siteUrl, OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    public GetListItemRole2013 setId(String listId, String itemId) {
        this.listId = listId;
        this.itemId = itemId;
        return this;
    }

    public GetListItemRole2013 setSharePointGroupCache(Map<String, GetListItemRole2013Response.SharePointGroup> sharePointGroupCache) {
        this.sharePointGroupCache = sharePointGroupCache;
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
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
        List<Map<String, Object>> values = (List)bodyMap.get("value");
        values.stream().map(value -> (value.get("PrincipalId").toString())).forEach(principalId -> {
            if (sharePointGroupCache != null && sharePointGroupCache.containsKey(principalId)) {
                response.addSharePointGroup(sharePointGroupCache.get(principalId));
                return;
            }
            final HttpGet memberRequest = new HttpGet(buildMemberUrl(principalId));
            final XmlResponse memberResponse = doXmlRequest(memberRequest);
            MemberDocHandler memberDocHandler = new MemberDocHandler();
            memberResponse.parseXml(memberDocHandler);
            final Map<String, Object> memberResponseMap = memberDocHandler.getDataMap();
            final String id = memberResponseMap.get("Id").toString();
            final int principalType = Integer.valueOf(memberResponseMap.get("PrincipalType").toString());
            if (principalType == 1) {
                // User
                GetListItemRole2013Response.User user = new GetListItemRole2013Response.User(id, memberResponseMap.get("Title").toString(), memberResponseMap.get("LoginName").toString());
                response.addUser(user);
            } else if (principalType == 8) {
                GetListItemRole2013Response.SharePointGroup sharePointGroup = buildSharePointGroup(id, memberResponseMap.get("Title").toString());
                response.addSharePointGroup(sharePointGroup);
                if (sharePointGroupCache != null) {
                    sharePointGroupCache.put(principalId, sharePointGroup);
                }
            }
        });
        return response;
    }

    private String buildBaseUrl() {
        return siteUrl + "_api/Web/Lists(guid'" + listId + "')/";
    }

    private String buildRoleAssignmentsUrl() {
        return buildBaseUrl() + "Items(" + itemId + ")/RoleAssignments";
    }

    private String buildMemberUrl(String principalId) {
        return buildBaseUrl() + "RoleAssignments/GetByPrincipalId(" + principalId + ")/Member";
    }

    private String buildUsersUrl(String memberId) {
        return siteUrl + "_api/Web/SiteGroups/GetById(" + memberId + ")/Users";
    }

    private GetListItemRole2013Response.SharePointGroup buildSharePointGroup(String id, String title) {
        // SharePointGroup
        final GetListItemRole2013Response.SharePointGroup sharePointGroup = new GetListItemRole2013Response.SharePointGroup(id, title);
        final HttpGet usersRequest = new HttpGet(buildUsersUrl(id));
        final XmlResponse usersResponse = doXmlRequest(usersRequest);
        final UsersDocHandler usersDocHandler = new UsersDocHandler();
        usersResponse.parseXml(usersDocHandler);
        final Map<String, Object> usersResponseMap = usersDocHandler.getDataMap();
        List<Map<String, Object>> usersList = (List)usersResponseMap.get("value");
        usersList.forEach(user -> {
            String userId = user.get("Id").toString();
            String userTitle = user.get("Title").toString();
            String loginName = user.get("LoginName").toString();
            int userPrincipalType = Integer.valueOf(user.get("PrincipalType").toString());
            if (userPrincipalType == 1) {
                // user
                GetListItemRole2013Response.User userUser = new GetListItemRole2013Response.User(userId, userTitle, loginName);
                sharePointGroup.addUser(userUser);
            } else if (userPrincipalType == 4) {
                // Security Group
                GetListItemRole2013Response.SecurityGroup securityGroup = new GetListItemRole2013Response.SecurityGroup(userId, userTitle);
                sharePointGroup.addSecurityGroup(securityGroup);
            } else if (userPrincipalType == 8) {
                if (sharePointGroupCache != null && sharePointGroupCache.containsKey(userId)) {
                    sharePointGroup.addSharePointGroup(sharePointGroupCache.get(userId));
                } else {
                    GetListItemRole2013Response.SharePointGroup userSharePointGroup = buildSharePointGroup(userId, title);
                    sharePointGroup.addSharePointGroup(userSharePointGroup);
                    if (sharePointGroupCache != null) {
                        sharePointGroupCache.put(userId, userSharePointGroup);
                    }
                }
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
            } else {
                if ("d:PrincipalId".equals(qName)) {
                    fieldName = "PrincipalId";
                    buffer.setLength(0);
                }
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
                    ((List)dataMap.get("value")).add(resultMap);
                }
                resultMap = null;
            } else {
                if (resultMap != null && fieldName != null) {
                    if (!resultMap.containsKey(fieldName)) {
                        resultMap.put(fieldName, buffer.toString());
                    }
                    fieldName = null;
                }
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
            } else {
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
                    ((List)dataMap.get("value")).add(resultMap);
                }
                resultMap = null;
            } else {
                if (resultMap != null && fieldName != null) {
                    if (!resultMap.containsKey(fieldName)) {
                        resultMap.put(fieldName, buffer.toString());
                    }
                    fieldName = null;
                }
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
