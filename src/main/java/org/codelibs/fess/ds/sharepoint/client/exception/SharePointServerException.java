package org.codelibs.fess.ds.sharepoint.client.exception;

public class SharePointServerException extends RuntimeException {
    private final int statusCode;

    public SharePointServerException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
