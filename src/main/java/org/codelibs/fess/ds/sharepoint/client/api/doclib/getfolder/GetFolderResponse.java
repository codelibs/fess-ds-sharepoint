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
package org.codelibs.fess.ds.sharepoint.client.api.doclib.getfolder;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;

public class GetFolderResponse implements SharePointApiResponse {

    protected String id = null;
    protected String name = null;
    protected boolean exists = false;
    protected String serverRelativeUrl = null;
    protected Date created = null;
    protected Date modified = null;
    protected int itemCount = 0;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isExists() {
        return exists;
    }

    public String getServerRelativeUrl() {
        return serverRelativeUrl;
    }

    public Date getCreated() {
        return created;
    }

    public Date getModified() {
        return modified;
    }

    public int getItemCount() {
        return itemCount;
    }

    public static GetFolderResponse build(final SharePointApi.JsonResponse jsonResponse) {
        final Map<String, Object> jsonMap = jsonResponse.getBodyAsMap();
        return buildFromMap(jsonMap);
    }

    public static GetFolderResponse buildFromMap(final Map<String, Object> jsonMap) {
        @SuppressWarnings("unchecked")

        final GetFolderResponse response = new GetFolderResponse();
        response.id = jsonMap.get("UniqueId").toString();
        response.name = jsonMap.get("Name").toString();
        response.exists = Boolean.parseBoolean(jsonMap.get("Exists").toString());
        response.serverRelativeUrl = jsonMap.get("ServerRelativeUrl").toString();
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        try {
            response.created = sdf.parse(jsonMap.get("TimeCreated").toString());
            response.modified = sdf.parse(jsonMap.get("TimeLastModified").toString());
        } catch (final ParseException e) {
            throw new SharePointClientException(e);
        }
        response.itemCount = Integer.parseInt(jsonMap.get("ItemCount").toString());
        return response;
    }
}
