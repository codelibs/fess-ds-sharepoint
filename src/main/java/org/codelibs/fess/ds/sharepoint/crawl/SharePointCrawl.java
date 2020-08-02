package org.codelibs.fess.ds.sharepoint.crawl;

import org.codelibs.fess.ds.sharepoint.client.SharePointClient;

import java.util.Map;
import java.util.Queue;

public abstract class SharePointCrawl {
    protected final SharePointClient client;

    public SharePointCrawl(SharePointClient client) {
        this.client = client;
    }

    abstract public Map<String, Object> doCrawl(final Queue<SharePointCrawl> crawlingQueue);
}
