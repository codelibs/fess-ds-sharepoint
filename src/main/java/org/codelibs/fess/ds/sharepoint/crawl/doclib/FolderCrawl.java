package org.codelibs.fess.ds.sharepoint.crawl.doclib;

import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.client.api.doclib.getfiles.GetFilesResponse;
import org.codelibs.fess.ds.sharepoint.client.api.doclib.getfolder.GetFolderResponse;
import org.codelibs.fess.ds.sharepoint.client.api.doclib.getfolders.GetFoldersResponse;
import org.codelibs.fess.ds.sharepoint.crawl.SharePointCrawl;
import org.codelibs.fess.ds.sharepoint.crawl.file.FileCrawl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Queue;

public class FolderCrawl extends SharePointCrawl {
    private static final Logger logger = LoggerFactory.getLogger(FolderCrawl.class);

    private final String serverRelativeUrl;

    public FolderCrawl(SharePointClient client, String serverRelativeUrl) {
        super(client);
        this.serverRelativeUrl = serverRelativeUrl;
    }

    @Override
    public Map<String, Object> doCrawl(Queue<SharePointCrawl> crawlingQueue) {
        if (logger.isInfoEnabled()) {
            logger.info("[Crawling DocLib Folder] serverRelativeUrl:{}", serverRelativeUrl);
        }

        GetFolderResponse getFolderResponse = client.api().doclib()
                .getFolder()
                .setServerRelativeUrl(serverRelativeUrl)
                .execute();
        if (getFolderResponse.getItemCount() > 0) {
            GetFoldersResponse getFoldersResponse = client.api().doclib()
                    .getFolders()
                    .setServerRelativeUrl(serverRelativeUrl)
                    .execute();
            getFoldersResponse.getFolders().forEach(subFolder -> {
                crawlingQueue.offer(new FolderCrawl(client, subFolder.getServerRelativeUrl()));
            });

            GetFilesResponse getFilesResponse = client.api().doclib()
                    .getFiles()
                    .setServerRelativeUrl(serverRelativeUrl)
                    .execute();
            getFilesResponse.getFiles().forEach(file -> {
                crawlingQueue.offer(new FileCrawl(client, file.getFileName(), client.helper().buildDocLibFileWebLink(file.getServerRelativeUrl()), file.getServerRelativeUrl(), file.getCreated(), file.getModified()));
            });
        }
        return null;
    }
}
