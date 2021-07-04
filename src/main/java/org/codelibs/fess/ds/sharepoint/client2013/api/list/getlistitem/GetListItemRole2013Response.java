/*
 * Copyright 2012-2021 CodeLibs Project and the Others.
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
import java.util.List;

import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemRoleResponse;

public class GetListItemRole2013Response extends GetListItemRoleResponse {
    private final List<SharePointGroup> sharePointGroups = new ArrayList<>();
    private final List<User> users = new ArrayList<>();

    public GetListItemRole2013Response() {
    }

    @Override
    public List<SharePointGroup> getSharePointGroups() {
        return sharePointGroups;
    }

    @Override
    public void addSharePointGroup(final SharePointGroup sharePointGroup) {
        sharePointGroups.add(sharePointGroup);
    }

    @Override
    public List<User> getUsers() {
        return users;
    }

    @Override
    public void addUser(final User user) {
        users.add(user);
    }
}
