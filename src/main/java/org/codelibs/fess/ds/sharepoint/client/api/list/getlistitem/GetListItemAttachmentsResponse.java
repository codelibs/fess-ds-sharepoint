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
package org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;

public class GetListItemAttachmentsResponse implements SharePointApiResponse {
    private final List<AttachmentFile> files = new ArrayList<>();

    public static GetListItemAttachmentsResponse build(final SharePointApi.JsonResponse jsonResponse) {
        final GetListItemAttachmentsResponse response = new GetListItemAttachmentsResponse();

        final Map<String, Object> jsonMap = jsonResponse.getBodyAsMap();

        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> valueList = (List<Map<String, Object>>) jsonMap.get("value");
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
        final StringBuilder sb = new StringBuilder(100);
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

        public AttachmentFile(final String fileName, final String serverRelativeUrl) {
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
