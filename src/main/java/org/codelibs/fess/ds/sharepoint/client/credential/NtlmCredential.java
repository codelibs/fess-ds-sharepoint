package org.codelibs.fess.ds.sharepoint.client.credential;

import org.apache.http.auth.Credentials;
import org.apache.http.auth.NTCredentials;

public class NtlmCredential implements SharePointCredential {
    private final String user;
    private final String password;
    private final String hostName;
    private final String domain;

    public NtlmCredential(String user, String password, String hostName, String domain) {
        this.user = user;
        this.password = password;
        this.hostName = hostName;
        this.domain = domain;
    }

    @Override
    public Credentials getCredential() {
        return new NTCredentials(user, password, hostName, domain);
    }

}
