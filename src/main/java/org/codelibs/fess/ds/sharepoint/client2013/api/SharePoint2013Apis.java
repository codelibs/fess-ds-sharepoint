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
package org.codelibs.fess.ds.sharepoint.client2013.api;

import org.apache.http.impl.client.CloseableHttpClient;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApis;
import org.codelibs.fess.ds.sharepoint.client.oauth.OAuth;
import org.codelibs.fess.ds.sharepoint.client2013.api.doclib.getfiles.GetFiles2013;
import org.codelibs.fess.ds.sharepoint.client2013.api.doclib.getfolder.GetFolder2013;
import org.codelibs.fess.ds.sharepoint.client2013.api.doclib.getfolders.GetFolders2013;
import org.codelibs.fess.ds.sharepoint.client2013.api.doclib.getlistitem.GetDoclibListItem2013;
import org.codelibs.fess.ds.sharepoint.client2013.api.file.getfile.GetFile2013;
import org.codelibs.fess.ds.sharepoint.client2013.api.list.getlistforms.GetForms2013;
import org.codelibs.fess.ds.sharepoint.client2013.api.list.getlistitem.GetListItemAttachments2013;
import org.codelibs.fess.ds.sharepoint.client2013.api.list.getlistitem.GetListItemRole2013;
import org.codelibs.fess.ds.sharepoint.client2013.api.list.getlistitem.GetListItemValue2013;
import org.codelibs.fess.ds.sharepoint.client2013.api.list.getlistitems.GetListItems2013;
import org.codelibs.fess.ds.sharepoint.client2013.api.list.getlists.GetList2013;
import org.codelibs.fess.ds.sharepoint.client2013.api.list.getlists.GetLists2013;

public class SharePoint2013Apis extends SharePointApis {
    public SharePoint2013Apis(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    @Override
    public ListApis list() {
        return new ListApis();
    }

    @Override
    public FileApis file() {
        return new FileApis();
    }

    @Override
    public DocLibApis doclib() {
        return new DocLibApis();
    }

    public class ListApis extends SharePointApis.ListApis {
        @Override
        public GetList2013 getList() {
            return new GetList2013(client, siteUrl, oAuth);
        }

        @Override
        public GetLists2013 getLists() {
            return new GetLists2013(client, siteUrl, oAuth);
        }

        @Override
        public GetListItems2013 getListItems() {
            return new GetListItems2013(client, siteUrl, oAuth);
        }

        @Override
        public GetListItemValue2013 getListItemValue() {
            return new GetListItemValue2013(client, siteUrl, oAuth);
        }

        @Override
        public GetListItemAttachments2013 getListItemAttachments() {
            return new GetListItemAttachments2013(client, siteUrl, oAuth);
        }

        @Override
        public GetListItemRole2013 getListItemRole() {
            return new GetListItemRole2013(client, siteUrl, oAuth);
        }

        @Override
        public GetForms2013 getForms() {
            return new GetForms2013(client, siteUrl, oAuth);
        }
    }

    public class FileApis extends SharePointApis.FileApis {
        @Override
        public GetFile2013 getFile() {
            return new GetFile2013(client, siteUrl, oAuth);
        }
    }

    public class DocLibApis extends SharePointApis.DocLibApis {
        @Override
        public GetFolder2013 getFolder() {
            return new GetFolder2013(client, siteUrl, oAuth);
        }

        @Override
        public GetFolders2013 getFolders() {
            return new GetFolders2013(client, siteUrl, oAuth);
        }

        @Override
        public GetFiles2013 getFiles() {
            return new GetFiles2013(client, siteUrl, oAuth);
        }

        @Override
        public GetDoclibListItem2013 getListItem() {
            return new GetDoclibListItem2013(client, siteUrl, oAuth);
        }
    }
}
