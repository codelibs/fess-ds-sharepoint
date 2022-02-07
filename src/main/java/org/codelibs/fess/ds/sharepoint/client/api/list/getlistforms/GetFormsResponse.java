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
package org.codelibs.fess.ds.sharepoint.client.api.list.getlistforms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;
import org.codelibs.fess.ds.sharepoint.client.api.list.PageType;
import org.codelibs.fess.util.DocumentUtil;

public class GetFormsResponse implements SharePointApiResponse {
    private final List<Form> forms = new ArrayList<>();

    public List<Form> getForms() {
        return forms;
    }

    protected static GetFormsResponse build(final SharePointApi.JsonResponse jsonResponse) {
        final GetFormsResponse response = new GetFormsResponse();

        final Map<String, Object> jsonMap = jsonResponse.getBodyAsMap();
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> values = (List<Map<String, Object>>) jsonMap.get("value");
        values.stream().forEach(value -> {
            final String id = DocumentUtil.getValue(value, "Id", String.class);
            final String serverRelativeUrl = DocumentUtil.getValue(value, "ServerRelativeUrl", String.class);
            final int type = DocumentUtil.getValue(value, "FormType", Integer.class);
            response.forms.add(new Form(id, serverRelativeUrl, PageType.getPageType(type)));
        });
        return response;
    }

    public static class Form {
        private final String id;
        private final String serverRelativeUrl;
        private final PageType type;

        public Form(final String id, final String serverRelativeUrl, final PageType type) {
            this.id = id;
            this.serverRelativeUrl = serverRelativeUrl;
            this.type = type;
        }

        public String getId() {
            return id;
        }

        public String getServerRelativeUrl() {
            return serverRelativeUrl;
        }

        public PageType getType() {
            return type;
        }
    }
}
