package org.codelibs.fess.ds.sharepoint.client.api;

import java.io.Closeable;
import java.io.IOException;

public interface SharePointApiResponse extends Closeable {
    @Override
    default void close() throws IOException {
    }
}
