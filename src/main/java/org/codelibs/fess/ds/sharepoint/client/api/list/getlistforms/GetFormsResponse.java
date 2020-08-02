package org.codelibs.fess.ds.sharepoint.client.api.list.getlistforms;

import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;
import org.codelibs.fess.ds.sharepoint.client.api.list.PageType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GetFormsResponse implements SharePointApiResponse {
    private final List<Form> forms = new ArrayList<>();

    private GetFormsResponse() {
    }

    public List<Form> getForms() {
        return forms;
    }

    @SuppressWarnings("unchecked")
    protected static GetFormsResponse build(final SharePointApi.JsonResponse jsonResponse) {
        final GetFormsResponse response = new GetFormsResponse();

        final Map<String, Object> jsonMap = jsonResponse.getBodyAsMap();
        final List<Map<String, Object>> values = (List)jsonMap.get("value");
        values.stream().forEach(value -> {
            String id = value.get("Id").toString();
            String serverRelativeUrl = value.get("ServerRelativeUrl").toString();
            int type = Integer.valueOf(value.get("FormType").toString());
            response.forms.add(new Form(id, serverRelativeUrl, PageType.getPageType(type)));
        });
        return response;
    }

    public static class Form {
        private final String id;
        private final String serverRelativeUrl;
        private final PageType type;

        public Form(String id, String serverRelativeUrl, PageType type) {
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
