package org.codelibs.fess.ds.sharepoint.client.api.file.getfile;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApiResponse;

import java.io.IOException;
import java.io.InputStream;

public class GetFileResponse implements SharePointApiResponse {
    private final CloseableHttpResponse httpResponse;

    public GetFileResponse(CloseableHttpResponse httpResponse) {
        this.httpResponse = httpResponse;
    }

    public InputStream getFileContent() throws IOException {
        return httpResponse.getEntity().getContent();
    }

    @Override
    public void close() throws IOException {
        httpResponse.close();
    }
}
