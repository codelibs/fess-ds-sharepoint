package org.codelibs.fess.ds.sharepoint.client.api.doclib.getfiles;

import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class GetFilesResponse implements SharePointApiResponse {
    private static final Logger logger = LoggerFactory.getLogger(GetFilesResponse.class);

    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private final List<DocLibFile> files = new ArrayList<>();

    public List<DocLibFile> getFiles() {
        return files;
    }

    public static GetFilesResponse build(SharePointApi.JsonResponse jsonResponse) {
        final Map<String, Object> jsonMap = jsonResponse.getBodyAsMap();
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> results = (List)jsonMap.get("value");

        final GetFilesResponse response = new GetFilesResponse();
        results.stream().forEach(result -> {
            final DocLibFile docLibFile = new DocLibFile();
            docLibFile.fileName = result.get("Name").toString();
            docLibFile.title = (String)result.getOrDefault("Title", "");
            docLibFile.serverRelativeUrl = result.get("ServerRelativeUrl").toString();
            try {
                docLibFile.created = sdf.parse(result.get("TimeCreated").toString());
                docLibFile.modified = sdf.parse(result.get("TimeLastModified").toString());
            } catch (ParseException e) {
                logger.warn("Failed to parse date.", e);
            }
            response.files.add(docLibFile);
        });

        return response;
    }

    public static class DocLibFile {
        private String fileName;
        private String title;
        private String serverRelativeUrl;
        private Date created;
        private Date modified;

        public String getFileName() {
            return fileName;
        }

        public String getTitle() {
            return title;
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
    }
}
