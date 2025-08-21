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
import java.util.Arrays;
import java.util.List;

import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;

/**
 * Response object containing SharePoint list item role assignment data.
 * This class represents the response from the GetListItemRole API call and contains
 * information about users, SharePoint groups, and security groups that have access to a list item.
 */
public class GetListItemRoleResponse implements SharePointApiResponse {
    private final List<SharePointGroup> sharePointGroups = new ArrayList<>();
    private final List<User> users = new ArrayList<>();
    private final List<SecurityGroup> securityGroups = new ArrayList<>();

    /**
     * Constructs a new GetListItemRoleResponse.
     */
    public GetListItemRoleResponse() {
    }

    /**
     * Gets the list of SharePoint groups that have access to the list item.
     *
     * @return a list of SharePointGroup objects
     */
    public List<SharePointGroup> getSharePointGroups() {
        return sharePointGroups;
    }

    /**
     * Adds a SharePoint group to the response.
     *
     * @param sharePointGroup the SharePoint group to add
     */
    public void addSharePointGroup(final SharePointGroup sharePointGroup) {
        sharePointGroups.add(sharePointGroup);
    }

    /**
     * Gets the list of users that have direct access to the list item.
     *
     * @return a list of User objects
     */
    public List<User> getUsers() {
        return users;
    }

    /**
     * Adds a user to the response.
     *
     * @param user the user to add
     */
    public void addUser(final User user) {
        users.add(user);
    }

    /**
     * Gets the list of security groups that have access to the list item.
     *
     * @return a list of SecurityGroup objects
     */
    public List<SecurityGroup> getSecurityGroups() {
        return securityGroups;
    }

    /**
     * Adds a security group to the response.
     *
     * @param securityGroup the security group to add
     */
    public void addSecurityGroup(final SecurityGroup securityGroup) {
        securityGroups.add(securityGroup);
    }

    /**
     * Represents a SharePoint group that has access to a list item.
     * A SharePoint group can contain users, security groups, and other SharePoint groups.
     */
    public static class SharePointGroup {
        private final String id;
        private final String title;
        private final List<User> users = new ArrayList<>();
        private final List<SecurityGroup> securityGroups = new ArrayList<>();
        private final List<SharePointGroup> sharePointGroups = new ArrayList<>();

        /**
         * Constructs a new SharePointGroup.
         *
         * @param id the unique identifier of the SharePoint group
         * @param title the display name of the SharePoint group
         */
        public SharePointGroup(final String id, final String title) {
            this.id = id;
            this.title = title;
        }

        /**
         * Adds a user to this SharePoint group.
         *
         * @param user the user to add
         */
        public void addUser(final User user) {
            users.add(user);
        }

        /**
         * Adds a security group to this SharePoint group.
         *
         * @param group the security group to add
         */
        public void addSecurityGroup(final SecurityGroup group) {
            securityGroups.add(group);
        }

        /**
         * Adds a nested SharePoint group to this SharePoint group.
         *
         * @param sharePointGroup the SharePoint group to add
         */
        public void addSharePointGroup(final SharePointGroup sharePointGroup) {
            sharePointGroups.add(sharePointGroup);
        }

        /**
         * Gets the SharePoint group ID.
         *
         * @return the unique identifier of the SharePoint group
         */
        public String getId() {
            return id;
        }

        /**
         * Gets the SharePoint group title.
         *
         * @return the display name of the SharePoint group
         */
        public String getTitle() {
            return title;
        }

        /**
         * Gets the users in this SharePoint group.
         *
         * @return a list of User objects
         */
        public List<User> getUsers() {
            return users;
        }

        /**
         * Gets the security groups in this SharePoint group.
         *
         * @return a list of SecurityGroup objects
         */
        public List<SecurityGroup> getSecurityGroups() {
            return securityGroups;
        }

        /**
         * Gets the nested SharePoint groups in this SharePoint group.
         *
         * @return a list of SharePointGroup objects
         */
        public List<SharePointGroup> getSharePointGroups() {
            return sharePointGroups;
        }
    }

    /**
     * Represents a user that has access to a list item.
     * Contains information about the user's identity and account details.
     */
    public static class User {
        private static final String ACCOUNT_PREFIX = "i:0#.w|";
        private static final String AZURE_ACCOUNT_PREFIX = "i:0#.f|membership|";

        private final String id;
        private final String title;
        private final String account;

        /**
         * Constructs a new User.
         *
         * @param id the unique identifier of the user
         * @param title the display name of the user
         * @param account the account name of the user (including prefixes)
         */
        public User(final String id, final String title, final String account) {
            this.id = id;
            this.title = title;
            this.account = account;
        }

        /**
         * Gets the user ID.
         *
         * @return the unique identifier of the user
         */
        public String getId() {
            return id;
        }

        /**
         * Gets the user title.
         *
         * @return the display name of the user
         */
        public String getTitle() {
            return title;
        }

        /**
         * Gets the user account name without the prefix.
         *
         * @return the account name without the "i:0#.w|" prefix
         */
        public String getAccount() {
            return account.substring(ACCOUNT_PREFIX.length());
        }

        /**
         * Checks if this is an Azure account.
         *
         * @return true if the account is an Azure account, false otherwise
         */
        public boolean isAzureAccount() {
            return account.startsWith(AZURE_ACCOUNT_PREFIX);
        }

        /**
         * Gets the Azure account name without the prefix.
         *
         * @return the Azure account name without the prefix
         */
        public String getAzureAccount() {
            return account.substring(AZURE_ACCOUNT_PREFIX.length());
        }

        /**
         * Extracts the AD account portion from an Azure account.
         *
         * @return the AD account name (part before @) from an Azure account
         */
        public String getAdAccountFromAzureAccount() {
            final String azureAccount = getAzureAccount();
            if (azureAccount.contains("@")) {
                return azureAccount.substring(0, azureAccount.indexOf("@"));
            }
            return azureAccount;
        }
    }

    /**
     * Represents a security group that has access to a list item.
     * Security groups can be traditional Windows groups or Azure-based groups.
     */
    public static class SecurityGroup {
        private static final String AZURE_GRID_ALL_USERS_ROLE = "spo-grid-all-users";
        private static final String AZURE_GRID_ALL_USERS_PREFIX = "c:0-.f|rolemanager|spo-grid-all-users/";
        private static final String[] AZURE_ACCOUNT_PREFIXES =
                { "c:0o.c|federateddirectoryclaimprovider|", "c:0t.c|tenant|", AZURE_GRID_ALL_USERS_PREFIX };

        private final String id;
        private final String title;
        private final String loginName;

        /**
         * Constructs a new SecurityGroup.
         *
         * @param id the unique identifier of the security group
         * @param title the display name of the security group
         * @param loginName the login name of the security group (including prefixes)
         */
        public SecurityGroup(final String id, final String title, final String loginName) {
            this.id = id;
            this.title = title;
            this.loginName = loginName;
        }

        /**
         * Gets the security group ID.
         *
         * @return the unique identifier of the security group
         */
        public String getId() {
            return id;
        }

        /**
         * Gets the security group title.
         *
         * @return the display name of the security group
         */
        public String getTitle() {
            return title;
        }

        /**
         * Checks if this is an Azure-based security group.
         *
         * @return true if the group is Azure-based, false otherwise
         */
        public boolean isAzureAccount() {
            return Arrays.stream(AZURE_ACCOUNT_PREFIXES).anyMatch(prefix -> loginName.startsWith(prefix));
        }

        /**
         * Gets the Azure account name without prefixes.
         *
         * @return the cleaned Azure account name
         */
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
