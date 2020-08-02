package org.codelibs.fess.ds.sharepoint.crawl.list;

import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemAttachmentsResponse;
import org.codelibs.fess.ds.sharepoint.crawl.SharePointCrawl;
import org.codelibs.fess.ds.sharepoint.crawl.file.FileCrawl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Map;
import java.util.Queue;

public class ItemAttachmentsCrawl extends SharePointCrawl {
    private static final Logger logger = LoggerFactory.getLogger(ItemAttachmentsCrawl.class);

    private final String listName;
    private final String itemId;
    private final String editLink;
    private final Date created;
    private final Date modified;

    public ItemAttachmentsCrawl(SharePointClient client, String listName, String itemId, String editLink, Date created, Date modified) {
        super(client);
        this.itemId = itemId;
        this.listName = listName;
        this.editLink = editLink;
        this.created = created;
        this.modified = modified;
    }

    @Override
    public Map<String, Object> doCrawl(Queue<SharePointCrawl> crawlingQueue) {
        if (logger.isInfoEnabled()) {
            logger.info("[Crawling ListItem Attachments] [editLink:{}]", editLink);
        }

        GetListItemAttachmentsResponse response = client.api().list().getListItemAttachments().setEditLink(editLink).execute();
        response.getFiles().forEach(file -> {
            crawlingQueue.offer(new FileCrawl(client, file.getFileName(),
                    client.getUrl() + client.helper().encodeRelativeUrl(file.getServerRelativeUrl().substring(1)),
                    file.getServerRelativeUrl(),
                    created,
                    modified));
        });
        return null;
    }
}
