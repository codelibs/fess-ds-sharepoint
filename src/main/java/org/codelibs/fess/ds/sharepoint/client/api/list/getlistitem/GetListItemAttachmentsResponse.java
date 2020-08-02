package org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem;

import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GetListItemAttachmentsResponse implements SharePointApiResponse {
    private List<AttachmentFile> files = new ArrayList<>();

    public static GetListItemAttachmentsResponse build(SharePointApi.JsonResponse jsonResponse) {
        GetListItemAttachmentsResponse response = new GetListItemAttachmentsResponse();

        Map<String, Object> jsonMap = jsonResponse.getBodyAsMap();

        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> valueList = (List)jsonMap.get("value");
        valueList.stream().forEach(value -> {
            final String fileName = value.get("FileName").toString();
            final String serverRelativeUrl = value.get("ServerRelativeUrl").toString();
            response.files.add(new AttachmentFile(fileName, serverRelativeUrl));
        });

        return response;
    }

    public List<AttachmentFile> getFiles() {
        return files;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        files.stream().forEach(file -> {
            sb.append('[');
            sb.append("file:").append(file.getFileName());
            sb.append(" url:").append(file.getServerRelativeUrl());
            sb.append("] ");
        });
        return sb.toString();
    }

    public static class AttachmentFile {
        private final String fileName;
        private final String serverRelativeUrl;

        private AttachmentFile(String fileName, String serverRelativeUrl) {
            this.fileName = fileName;
            this.serverRelativeUrl = serverRelativeUrl;
        }

        public String getFileName() {
            return fileName;
        }

        public String getServerRelativeUrl() {
            return serverRelativeUrl;
        }
    }
}
