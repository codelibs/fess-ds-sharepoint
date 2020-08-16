package org.codelibs.fess.ds.sharepoint.crawl.list;

import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemRoleResponse;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitems.GetListItemsResponse;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlists.GetListsResponse;
import org.codelibs.fess.ds.sharepoint.crawl.SharePointCrawl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Queue;

public class ListCrawl extends SharePointCrawl {
    private static final Logger logger = LoggerFactory.getLogger(ListCrawl.class);

    private final String id;
    private final String listName;
    private final int numberPerPage;
    private final Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache;


    public ListCrawl(SharePointClient client, String id, String listName, int numberPerPage, Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache) {
        super(client);
        this.id = id;
        this.listName = listName;
        this.numberPerPage = numberPerPage;
        this.sharePointGroupCache = sharePointGroupCache;
    }

    @Override
    public Map<String, Object> doCrawl(Queue<SharePointCrawl> crawlingQueue) {
        if (logger.isInfoEnabled()) {
            logger.info("[Crawling List] [id:{}] [listName:{}]", id, listName);
        }

        GetListsResponse getListsResponse = client.api().list().getLists().execute();
        GetListsResponse.SharePointList sharePointList = null;
        for (GetListsResponse.SharePointList list: getListsResponse.getLists()) {
            if (this.id != null) {
                if (list.getId().equals(this.id)) {
                    sharePointList = list;
                    break;
                }
            } else {
                if (list.getListName().equals(this.listName)) {
                    sharePointList = list;
                    break;
                }
            }
        }
        String listId;
        String listName;
        if (sharePointList == null) {
            listId = this.id;
            listName = this.listName;
        } else {
            listId = sharePointList.getId();
            listName = sharePointList.getListName();
        }

        for (int start=0; ;start += numberPerPage) {
            final GetListItemsResponse getListItemsResponse;
            if (listId != null) {
                getListItemsResponse = client.api().list().getListItems().setListId(listId).setNum(numberPerPage).setStart(start).execute();
            } else {
                return null;
            }
            if (getListItemsResponse.getListItems().isEmpty()) {
                break;
            }
            getListItemsResponse.getListItems().forEach(item -> {
                if (item.getTitle().startsWith("$Resources")) {
                    return;
                }

                final List<String> roles = getItemRoles(listId, item.getId(), sharePointGroupCache);
                crawlingQueue.offer(new ItemCrawl(client, listId, listName, item.getId(), roles));
                if (item.hasAttachments()) {
                    crawlingQueue.offer(new ItemAttachmentsCrawl(client, listId, listName, item.getId(), item.getCreated(), item.getModified(), roles));
                }
            });
        }
        return null;
    }
}
