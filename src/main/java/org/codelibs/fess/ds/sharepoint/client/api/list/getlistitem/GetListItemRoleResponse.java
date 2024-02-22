/*
 * Copyright 2012-2024 CodeLibs Project and the Others.
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
import java.util.Arrays;
import java.util.List;

import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;

public class GetListItemRoleResponse implements SharePointApiResponse {
    private final List<SharePointGroup> sharePointGroups = new ArrayList<>();
    private final List<User> users = new ArrayList<>();
    private final List<SecurityGroup> securityGroups = new ArrayList<>();

    public GetListItemRoleResponse() {
    }

    public List<SharePointGroup> getSharePointGroups() {
        return sharePointGroups;
    }

    public void addSharePointGroup(final SharePointGroup sharePointGroup) {
        sharePointGroups.add(sharePointGroup);
    }

    public List<User> getUsers() {
        return users;
    }

    public void addUser(final User user) {
        users.add(user);
    }

    public List<SecurityGroup> getSecurityGroups() {
        return securityGroups;
    }

    public void addSecurityGroup(final SecurityGroup securityGroup) {
        securityGroups.add(securityGroup);
    }

    public static class SharePointGroup {
        private final String id;
        private final String title;
        private final List<User> users = new ArrayList<>();
        private final List<SecurityGroup> securityGroups = new ArrayList<>();
        private final List<SharePointGroup> sharePointGroups = new ArrayList<>();

        public SharePointGroup(final String id, final String title) {
            this.id = id;
            this.title = title;
        }

        public void addUser(final User user) {
            users.add(user);
        }

        public void addSecurityGroup(final SecurityGroup group) {
            securityGroups.add(group);
        }

        public void addSharePointGroup(final SharePointGroup sharePointGroup) {
            sharePointGroups.add(sharePointGroup);
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public List<User> getUsers() {
            return users;
        }

        public List<SecurityGroup> getSecurityGroups() {
            return securityGroups;
        }

        public List<SharePointGroup> getSharePointGroups() {
            return sharePointGroups;
        }
    }

    public static class User {
        private static final String ACCOUNT_PREFIX = "i:0#.w|";
        private static final String AZURE_ACCOUNT_PREFIX = "i:0#.f|membership|";

        private final String id;
        private final String title;
        private final String account;

        public User(final String id, final String title, final String account) {
            this.id = id;
            this.title = title;
            this.account = account;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public String getAccount() {
            return account.substring(ACCOUNT_PREFIX.length());
        }

        public boolean isAzureAccount() {
            return account.startsWith(AZURE_ACCOUNT_PREFIX);
        }

        public String getAzureAccount() {
            return account.substring(AZURE_ACCOUNT_PREFIX.length());
        }

        public String getAdAccountFromAzureAccount() {
            final String azureAccount = getAzureAccount();
            if (azureAccount.contains("@")) {
                return azureAccount.substring(0, azureAccount.indexOf("@"));
            }
            return azureAccount;
        }
    }

    public static class SecurityGroup {
        private static final String AZURE_GRID_ALL_USERS_ROLE = "spo-grid-all-users";
        private static final String AZURE_GRID_ALL_USERS_PREFIX = "c:0-.f|rolemanager|spo-grid-all-users/";
        private static final String[] AZURE_ACCOUNT_PREFIXES =
                { "c:0o.c|federateddirectoryclaimprovider|", "c:0t.c|tenant|", AZURE_GRID_ALL_USERS_PREFIX };

        private final String id;
        private final String title;
        private final String loginName;

        public SecurityGroup(final String id, final String title, final String loginName) {
            this.id = id;
            this.title = title;
            this.loginName = loginName;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public boolean isAzureAccount() {
            return Arrays.stream(AZURE_ACCOUNT_PREFIXES).anyMatch(prefix -> loginName.startsWith(prefix));
        }

        public String getAzureAccount() {
            String account = loginName;
            if (account.startsWith(AZURE_GRID_ALL_USERS_PREFIX)) {
                account = AZURE_GRID_ALL_USERS_ROLE;
            } else {
                for (final String prefix : AZURE_ACCOUNT_PREFIXES) {
                    if (account.startsWith(prefix)) {
                        account = account.substring(prefix.length());
                        if (account.endsWith("_o")) {
                            account = account.substring(0, account.length() - "_o".length());
                        }
                        break;
                    }
                }
            }
            return account;
        }
    }

}
