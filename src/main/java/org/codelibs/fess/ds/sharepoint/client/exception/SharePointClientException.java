package org.codelibs.fess.ds.sharepoint.client.exception;

public class SharePointClientException extends RuntimeException {
    public SharePointClientException() {
    }

    public SharePointClientException(String message) {
        super(message);
    }

    public SharePointClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public SharePointClientException(Throwable cause) {
        super(cause);
    }

    public SharePointClientException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
