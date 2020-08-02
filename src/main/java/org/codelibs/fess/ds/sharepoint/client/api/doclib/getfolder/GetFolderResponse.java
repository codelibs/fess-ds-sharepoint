package org.codelibs.fess.ds.sharepoint.client.api.doclib.getfolder;

import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointClientException;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

public class GetFolderResponse implements SharePointApiResponse {
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private String id = null;
    private String name = null;
    private boolean exists = false;
    private String serverRelativeUrl = null;
    private Date created = null;
    private Date modified = null;
    private int itemCount = 0;

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

    public static GetFolderResponse build(SharePointApi.JsonResponse jsonResponse) {
        final Map<String, Object> jsonMap = jsonResponse.getBodyAsMap();
        return buildFromMap(jsonMap);
    }

    public static GetFolderResponse buildFromMap(Map<String, Object> jsonMap) {
        @SuppressWarnings("unchecked")

        final GetFolderResponse response = new GetFolderResponse();
        response.id = jsonMap.get("UniqueId").toString();
        response.name = jsonMap.get("Name").toString();
        response.exists = Boolean.valueOf(jsonMap.get("Exists").toString());
        response.serverRelativeUrl = jsonMap.get("ServerRelativeUrl").toString();
        try {
            response.created = sdf.parse(jsonMap.get("TimeCreated").toString());
            response.modified = sdf.parse(jsonMap.get("TimeLastModified").toString());
        } catch (ParseException e) {
            throw new SharePointClientException(e);
        }
        response.itemCount = Integer.valueOf(jsonMap.get("ItemCount").toString());
        return response;
    }
}
