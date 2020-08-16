package org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem;

import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;

import java.util.ArrayList;
import java.util.List;

public class GetListItemRoleResponse implements SharePointApiResponse {
    private final List<SharePointGroup> sharePointGroups = new ArrayList<>();
    private final List<User> users = new ArrayList<>();

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

    public static class SharePointGroup {
        private final String id;
        private final String title;
        private List<User> users = new ArrayList<>();
        private List<SecurityGroup> securityGroups = new ArrayList<>();
        private List<SharePointGroup> sharePointGroups = new ArrayList<>();

        public SharePointGroup(String id, String title) {
            this.id = id;
            this.title = title;
        }

        public void addUser(User user) {
            users.add(user);
        }

        public void addSecurityGroup(SecurityGroup group) {
            securityGroups.add(group);
        }

        public void addSharePointGroup(SharePointGroup sharePointGroup) {
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
        private final String id;
        private final String title;

        public User(String id, String title) {
            this.id = id;
            this.title = title;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }
    }

    public static class SecurityGroup {
        private final String id;
        private final String title;

        public SecurityGroup(String id, String title) {
            this.id = id;
            this.title = title;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }
    }

}
