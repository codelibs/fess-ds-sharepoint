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

/**
 * Main API gateway for SharePoint Online REST API operations.
 * This class provides access to different API categories including list operations,
 * file operations, and document library operations. Each category is accessible
 * through dedicated API classes that handle the specific SharePoint endpoints.
 *
 * <p>This class requires an HTTP client, site URL, and OAuth authentication
 * to communicate with SharePoint Online services.</p>
 *
 * @see ListApis
 * @see FileApis
 * @see DocLibApis
 */
public class SharePointApis {
    /** HTTP client for making REST API calls */
    protected final CloseableHttpClient client;
    /** Base URL of the SharePoint site */
    protected final String siteUrl;
    /** OAuth authentication handler */
    protected final OAuth oAuth;

    /**
     * Constructs a new SharePointApis instance.
     *
     * @param client HTTP client for making API requests
     * @param siteUrl base URL of the SharePoint site
     * @param oAuth OAuth authentication handler for API authentication
     */
    public SharePointApis(final CloseableHttpClient client, final String siteUrl, final OAuth oAuth) {
        this.client = client;
        this.siteUrl = siteUrl;
        this.oAuth = oAuth;
    }

    /**
     * Gets the list API operations handler.
     *
     * @return ListApis instance for list-related operations
     */
    public ListApis list() {
        return new ListApis();
    }

    /**
     * Gets the file API operations handler.
     *
     * @return FileApis instance for file-related operations
     */
    public FileApis file() {
        return new FileApis();
    }

    /**
     * Gets the document library API operations handler.
     *
     * @return DocLibApis instance for document library operations
     */
    public DocLibApis doclib() {
        return new DocLibApis();
    }

    /**
     * API handler for SharePoint list-related operations.
     * Provides access to list metadata, list items, attachments, and form operations.
     */
    public class ListApis {
        /**
         * Constructs a new ListApis instance.
         * This constructor initializes the API handler for SharePoint list operations,
         * providing access to list metadata, list items, attachments, and forms.
         */
        public ListApis() {
            // Default constructor for list API operations
        }

        /**
         * Gets the API for retrieving list metadata.
         *
         * @return GetList instance for list metadata operations
         */
        public GetList getList() {
            return new GetList(client, siteUrl, oAuth);
        }

        /**
         * Gets the API for retrieving multiple lists.
         *
         * @return GetLists instance for multiple list operations
         */
        public GetLists getLists() {
            return new GetLists(client, siteUrl, oAuth);
        }

        /**
         * Gets the API for retrieving list items.
         *
         * @return GetListItems instance for list item retrieval operations
         */
        public GetListItems getListItems() {
            return new GetListItems(client, siteUrl, oAuth);
        }

        /**
         * Gets the API for retrieving detailed list item values.
         *
         * @return GetListItemValue instance for detailed item value operations
         */
        public GetListItemValue getListItemValue() {
            return new GetListItemValue(client, siteUrl, oAuth);
        }

        /**
         * Gets the API for retrieving list item attachments.
         *
         * @return GetListItemAttachments instance for attachment operations
         */
        public GetListItemAttachments getListItemAttachments() {
            return new GetListItemAttachments(client, siteUrl, oAuth);
        }

        /**
         * Gets the API for retrieving list item role assignments.
         *
         * @return GetListItemRole instance for role assignment operations
         */
        public GetListItemRole getListItemRole() {
            return new GetListItemRole(client, siteUrl, oAuth);
        }

        /**
         * Gets the API for retrieving list forms.
         *
         * @return GetForms instance for list form operations
         */
        public GetForms getForms() {
            return new GetForms(client, siteUrl, oAuth);
        }
    }

    /**
     * API handler for SharePoint file-related operations.
     * Provides access to file content and metadata operations.
     */
    public class FileApis {
        /**
         * Constructs a new FileApis instance.
         * This constructor initializes the API handler for SharePoint file operations,
         * providing access to file content and metadata.
         */
        public FileApis() {
            // Default constructor for file API operations
        }

        /**
         * Gets the API for retrieving files.
         *
         * @return GetFile instance for file operations
         */
        public GetFile getFile() {
            return new GetFile(client, siteUrl, oAuth);
        }
    }

    /**
     * API handler for SharePoint document library operations.
     * Provides access to folder operations, file listings, and document library metadata.
     */
    public class DocLibApis {
        /**
         * Constructs a new DocLibApis instance.
         * This constructor initializes the API handler for SharePoint document library operations,
         * providing access to folders, file listings, and document library metadata.
         */
        public DocLibApis() {
            // Default constructor for document library API operations
        }

        /**
         * Gets the API for retrieving folder information.
         *
         * @return GetFolder instance for folder operations
         */
        public GetFolder getFolder() {
            return new GetFolder(client, siteUrl, oAuth);
        }

        /**
         * Gets the API for retrieving multiple folders.
         *
         * @return GetFolders instance for multiple folder operations
         */
        public GetFolders getFolders() {
            return new GetFolders(client, siteUrl, oAuth);
        }

        /**
         * Gets the API for retrieving files from document libraries.
         *
         * @return GetFiles instance for file listing operations
         */
        public GetFiles getFiles() {
            return new GetFiles(client, siteUrl, oAuth);
        }

        /**
         * Gets the API for retrieving document library list items.
         *
         * @return GetDoclibListItem instance for document library item operations
         */
        public GetDoclibListItem getListItem() {
            return new GetDoclibListItem(client, siteUrl, oAuth);
        }
    }
}
