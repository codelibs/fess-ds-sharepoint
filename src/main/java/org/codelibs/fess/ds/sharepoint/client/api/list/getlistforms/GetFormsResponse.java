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
package org.codelibs.fess.ds.sharepoint.client.api.list.getlistforms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;
import org.codelibs.fess.ds.sharepoint.client.api.list.PageType;
import org.codelibs.fess.util.DocumentUtil;

/**
 * Response object containing SharePoint list forms data.
 * This class represents the response from the GetForms API call and contains
 * a list of forms associated with a SharePoint list.
 */
public class GetFormsResponse implements SharePointApiResponse {

    /**
     * Default constructor for GetFormsResponse.
     */
    public GetFormsResponse() {
        // Default constructor
    }

    private final List<Form> forms = new ArrayList<>();

    /**
     * Gets the list of forms from the response.
     *
     * @return a list of Form objects representing the SharePoint list forms
     */
    public List<Form> getForms() {
        return forms;
    }

    /**
     * Builds a GetFormsResponse from a JSON response.
     *
     * @param jsonResponse the JSON response from the SharePoint API
     * @return a new GetFormsResponse instance populated with form data
     */
    protected static GetFormsResponse build(final SharePointApi.JsonResponse jsonResponse) {
        final GetFormsResponse response = new GetFormsResponse();

        final Map<String, Object> jsonMap = jsonResponse.getBodyAsMap();
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> values = (List<Map<String, Object>>) jsonMap.get("value");
        values.stream().forEach(value -> {
            final String id = DocumentUtil.getValue(value, "Id", String.class);
            final String serverRelativeUrl = DocumentUtil.getValue(value, "ServerRelativeUrl", String.class);
            final int type = DocumentUtil.getValue(value, "FormType", Integer.class, 0);
            response.forms.add(new Form(id, serverRelativeUrl, PageType.getPageType(type)));
        });
        return response;
    }

    /**
     * Represents a single form in a SharePoint list.
     * Contains information about the form's ID, URL, and type.
     */
    public static class Form {
        private final String id;
        private final String serverRelativeUrl;
        private final PageType type;

        /**
         * Constructs a new Form instance.
         *
         * @param id the unique identifier of the form
         * @param serverRelativeUrl the server-relative URL of the form
         * @param type the type of the form (e.g., Display, Edit, New)
         */
        public Form(final String id, final String serverRelativeUrl, final PageType type) {
            this.id = id;
            this.serverRelativeUrl = serverRelativeUrl;
            this.type = type;
        }

        /**
         * Gets the form ID.
         *
         * @return the unique identifier of the form
         */
        public String getId() {
            return id;
        }

        /**
         * Gets the server-relative URL of the form.
         *
         * @return the server-relative URL of the form
         */
        public String getServerRelativeUrl() {
            return serverRelativeUrl;
        }

        /**
         * Gets the type of the form.
         *
         * @return the PageType indicating the form's purpose (Display, Edit, New)
         */
        public PageType getType() {
            return type;
        }
    }
}
