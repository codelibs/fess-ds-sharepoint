package org.codelibs.fess.ds.sharepoint.client.credential;

import org.apache.http.auth.Credentials;

public interface SharePointCredential {
    Credentials getCredential();
}
