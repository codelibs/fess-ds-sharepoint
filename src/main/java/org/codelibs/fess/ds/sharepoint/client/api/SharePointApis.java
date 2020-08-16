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
import org.codelibs.fess.ds.sharepoint.client.api.list.getlists.GetLists;

public class SharePointApis {
    private final CloseableHttpClient client;
    private final String siteUrl;

    public SharePointApis(CloseableHttpClient client, String siteUrl) {
        this.client = client;
        this.siteUrl = siteUrl;
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
        public GetLists getLists() {
            return new GetLists(client, siteUrl);
        }

        public GetListItems getListItems() {
            return new GetListItems(client, siteUrl);
        }

        public GetListItemValue getListItemValue() {
            return new GetListItemValue(client, siteUrl);
        }

        public GetListItemAttachments getListItemAttachments() {
            return new GetListItemAttachments(client, siteUrl);
        }

        public GetListItemRole getListItemRole() {
            return new GetListItemRole(client, siteUrl);
        }

        public GetForms getForms() {
            return new GetForms(client, siteUrl);
        }
    }

    public class FileApis {
        public GetFile getFile() {
            return new GetFile(client, siteUrl);
        }
    }

    public class DocLibApis {
        public GetFolder getFolder() {
            return new GetFolder(client, siteUrl);
        }

        public GetFolders getFolders() {
            return new GetFolders(client, siteUrl);
        }

        public GetFiles getFiles() {
            return new GetFiles(client, siteUrl);
        }

        public GetDoclibListItem getListItem() {
            return new GetDoclibListItem(client, siteUrl);
        }
    }
}
