package org.codelibs.fess.ds.sharepoint.crawl;

import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlists.GetListsResponse;
import org.codelibs.fess.ds.sharepoint.crawl.doclib.FolderCrawl;
import org.codelibs.fess.ds.sharepoint.crawl.list.ListCrawl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Queue;

public class SiteCrawl extends SharePointCrawl {
    private static final Logger logger = LoggerFactory.getLogger(SiteCrawl.class);

    private final String siteName;
    private final int numberPerPage;

    public SiteCrawl(SharePointClient client, String siteName, int numberPerPage) {
        super(client);
        this.siteName = siteName;
        this.numberPerPage = numberPerPage;
    }

    @Override
    public Map<String, Object> doCrawl(final Queue<SharePointCrawl> crawlingQueue) {
        if (logger.isInfoEnabled()) {
            logger.info("[Crawling Site] [siteName:{}]", siteName);
        }
        GetListsResponse getListsResponse = client.api().list().getLists().execute();
        getListsResponse.getLists().stream().forEach(list -> {
            if (list.isNoCrawl()) {
                return;
            }
            crawlingQueue.offer(new ListCrawl(client, list.getId(), null, numberPerPage));
        });
        crawlingQueue.offer(new FolderCrawl(client, "/sites/" + siteName + "/Shared Documents"));
        return null;
    }
}
