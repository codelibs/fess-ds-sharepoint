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
package org.codelibs.fess.ds.sharepoint.client.api;

import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.doclib.getfiles.GetFiles;
import org.codelibs.fess.ds.sharepoint.client.api.doclib.getfolder.GetFolder;
import org.codelibs.fess.ds.sharepoint.client.api.doclib.getfolders.GetFolders;
import org.codelibs.fess.ds.sharepoint.client.api.doclib.getlistitem.GetDoclibListItem;
import org.codelibs.fess.ds.sharepoint.client.api.file.getfile.GetFile;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistforms.GetForms;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemAttachments;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemRole;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemValue;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitems.GetListItems;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlists.GetList;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlists.GetLists;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;

public class SharePointApis {
    protected final CloseableHttpClient client;
    protected final String siteUrl;
    protected final OAuth oAuth;

    public SharePointApis(CloseableHttpClient client, String siteUrl, OAuth oAuth) {
        this.client = client;
        this.siteUrl = siteUrl;
        this.oAuth = oAuth;
    }

    public ListApis list() {
        return new ListApis();
    }

    public FileApis file() {
        return new FileApis();
    }

    public DocLibApis doclib() {
        return new DocLibApis();
    }

    public class ListApis {
        public GetList getList() {
            return new GetList(client, siteUrl, oAuth);
        }

        public GetLists getLists() {
            return new GetLists(client, siteUrl, oAuth);
        }

        public GetListItems getListItems() {
            return new GetListItems(client, siteUrl, oAuth);
        }

        public GetListItemValue getListItemValue() {
            return new GetListItemValue(client, siteUrl, oAuth);
        }

        public GetListItemAttachments getListItemAttachments() {
            return new GetListItemAttachments(client, siteUrl, oAuth);
        }

        public GetListItemRole getListItemRole() {
            return new GetListItemRole(client, siteUrl, oAuth);
        }

        public GetForms getForms() {
            return new GetForms(client, siteUrl, oAuth);
        }
    }

    public class FileApis {
        public GetFile getFile() {
            return new GetFile(client, siteUrl, oAuth);
        }
    }

    public class DocLibApis {
        public GetFolder getFolder() {
            return new GetFolder(client, siteUrl, oAuth);
        }

        public GetFolders getFolders() {
            return new GetFolders(client, siteUrl, oAuth);
        }

        public GetFiles getFiles() {
            return new GetFiles(client, siteUrl, oAuth);
        }

        public GetDoclibListItem getListItem() {
            return new GetDoclibListItem(client, siteUrl, oAuth);
        }
    }
}
