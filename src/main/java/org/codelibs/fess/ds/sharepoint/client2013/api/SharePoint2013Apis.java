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

/**
 * SharePoint 2013 specific API gateway that extends SharePointApis.
 * This class provides access to SharePoint 2013 REST API operations,
 * which require XML parsing instead of JSON responses used in SharePoint Online.
 *
 * <p>All API methods return SharePoint 2013 specific implementations
 * that handle the XML response format used by SharePoint 2013.</p>
 *
 * @see SharePointApis
 */
public class SharePoint2013Apis extends SharePointApis {
    /**
     * Constructs a new SharePoint2013Apis instance.
     *
     * @param client HTTP client for making API requests
     * @param siteUrl base URL of the SharePoint 2013 site
     * @param oAuth OAuth authentication handler for API authentication
     */
    public SharePoint2013Apis(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        super(client, siteUrl, oAuth);
    }

    /**
     * Gets the SharePoint 2013 list API operations handler.
     *
     * @return ListApis instance for SharePoint 2013 list-related operations
     */
    @Override
    public ListApis list() {
        return new ListApis();
    }

    /**
     * Gets the SharePoint 2013 file API operations handler.
     *
     * @return FileApis instance for SharePoint 2013 file-related operations
     */
    @Override
    public FileApis file() {
        return new FileApis();
    }

    /**
     * Gets the SharePoint 2013 document library API operations handler.
     *
     * @return DocLibApis instance for SharePoint 2013 document library operations
     */
    @Override
    public DocLibApis doclib() {
        return new DocLibApis();
    }

    /**
     * SharePoint 2013 specific API handler for list-related operations.
     * Extends the base ListApis to provide SharePoint 2013 implementations
     * that handle XML response parsing.
     */
    public class ListApis extends SharePointApis.ListApis {
        /**
         * Constructs a new SharePoint 2013 ListApis instance.
         * This constructor initializes the API handler for SharePoint 2013 list operations,
         * extending the base functionality to handle XML response parsing specific to SharePoint 2013.
         */
        public ListApis() {
            super();
        }

        /**
         * Gets the SharePoint 2013 API for retrieving list metadata.
         *
         * @return GetList2013 instance for SharePoint 2013 list metadata operations
         */
        @Override
        public GetList2013 getList() {
            return new GetList2013(client, siteUrl, oAuth);
        }

        /**
         * Gets the SharePoint 2013 API for retrieving multiple lists.
         *
         * @return GetLists2013 instance for SharePoint 2013 multiple list operations
         */
        @Override
        public GetLists2013 getLists() {
            return new GetLists2013(client, siteUrl, oAuth);
        }

        /**
         * Gets the SharePoint 2013 API for retrieving list items.
         *
         * @return GetListItems2013 instance for SharePoint 2013 list item retrieval operations
         */
        @Override
        public GetListItems2013 getListItems() {
            return new GetListItems2013(client, siteUrl, oAuth);
        }

        /**
         * Gets the SharePoint 2013 API for retrieving detailed list item values.
         *
         * @return GetListItemValue2013 instance for SharePoint 2013 detailed item value operations
         */
        @Override
        public GetListItemValue2013 getListItemValue() {
            return new GetListItemValue2013(client, siteUrl, oAuth);
        }

        /**
         * Gets the SharePoint 2013 API for retrieving list item attachments.
         *
         * @return GetListItemAttachments2013 instance for SharePoint 2013 attachment operations
         */
        @Override
        public GetListItemAttachments2013 getListItemAttachments() {
            return new GetListItemAttachments2013(client, siteUrl, oAuth);
        }

        /**
         * Gets the SharePoint 2013 API for retrieving list item role assignments.
         *
         * @return GetListItemRole2013 instance for SharePoint 2013 role assignment operations
         */
        @Override
        public GetListItemRole2013 getListItemRole() {
            return new GetListItemRole2013(client, siteUrl, oAuth);
        }

        /**
         * Gets the SharePoint 2013 API for retrieving list forms.
         *
         * @return GetForms2013 instance for SharePoint 2013 list form operations
         */
        @Override
        public GetForms2013 getForms() {
            return new GetForms2013(client, siteUrl, oAuth);
        }
    }

    /**
     * SharePoint 2013 specific API handler for file-related operations.
     * Extends the base FileApis to provide SharePoint 2013 implementations
     * that handle XML response parsing.
     */
    public class FileApis extends SharePointApis.FileApis {
        /**
         * Constructs a new SharePoint 2013 FileApis instance.
         * This constructor initializes the API handler for SharePoint 2013 file operations,
         * extending the base functionality to handle XML response parsing specific to SharePoint 2013.
         */
        public FileApis() {
            super();
        }

        /**
         * Gets the SharePoint 2013 API for retrieving files.
         *
         * @return GetFile2013 instance for SharePoint 2013 file operations
         */
        @Override
        public GetFile2013 getFile() {
            return new GetFile2013(client, siteUrl, oAuth);
        }
    }

    /**
     * SharePoint 2013 specific API handler for document library operations.
     * Extends the base DocLibApis to provide SharePoint 2013 implementations
     * that handle XML response parsing.
     */
    public class DocLibApis extends SharePointApis.DocLibApis {
        /**
         * Constructs a new SharePoint 2013 DocLibApis instance.
         * This constructor initializes the API handler for SharePoint 2013 document library operations,
         * extending the base functionality to handle XML response parsing specific to SharePoint 2013.
         */
        public DocLibApis() {
            super();
        }

        /**
         * Gets the SharePoint 2013 API for retrieving folder information.
         *
         * @return GetFolder2013 instance for SharePoint 2013 folder operations
         */
        @Override
        public GetFolder2013 getFolder() {
            return new GetFolder2013(client, siteUrl, oAuth);
        }

        /**
         * Gets the SharePoint 2013 API for retrieving multiple folders.
         *
         * @return GetFolders2013 instance for SharePoint 2013 multiple folder operations
         */
        @Override
        public GetFolders2013 getFolders() {
            return new GetFolders2013(client, siteUrl, oAuth);
        }

        /**
         * Gets the SharePoint 2013 API for retrieving files from document libraries.
         *
         * @return GetFiles2013 instance for SharePoint 2013 file listing operations
         */
        @Override
        public GetFiles2013 getFiles() {
            return new GetFiles2013(client, siteUrl, oAuth);
        }

        /**
         * Gets the SharePoint 2013 API for retrieving document library list items.
         *
         * @return GetDoclibListItem2013 instance for SharePoint 2013 document library item operations
         */
        @Override
        public GetDoclibListItem2013 getListItem() {
            return new GetDoclibListItem2013(client, siteUrl, oAuth);
        }
    }
}
