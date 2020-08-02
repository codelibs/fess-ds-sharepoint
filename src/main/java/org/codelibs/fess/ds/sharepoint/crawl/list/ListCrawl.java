package org.codelibs.fess.ds.sharepoint.crawl.list;

import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.client.api.list.PageType;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistforms.GetForms;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistforms.GetFormsResponse;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitems.GetListItemsResponse;
import org.codelibs.fess.ds.sharepoint.crawl.SharePointCrawl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Queue;

public class ListCrawl extends SharePointCrawl {
    private static final Logger logger = LoggerFactory.getLogger(ListCrawl.class);

    private final String id;
    private final String listName;
    private final int numberPerPage;

    public ListCrawl(SharePointClient client, String id, String listName, int numberPerPage) {
        super(client);
        this.id = id;
        this.listName = listName;
        this.numberPerPage = numberPerPage;
    }

    @Override
    public Map<String, Object> doCrawl(Queue<SharePointCrawl> crawlingQueue) {
        if (logger.isInfoEnabled()) {
            logger.info("[Crawling List] [id:{}] [listName:{}]", id, listName);
        }

        String formUrl = getFormUrl();
        for (int start=0; ;start += numberPerPage) {
            final GetListItemsResponse getListItemsResponse;
            if (id != null) {
                getListItemsResponse = client.api().list().getListItems().setListId(id).setNum(numberPerPage).setStart(start).execute();
            } else if (listName != null) {
                getListItemsResponse = client.api().list().getListItems().setListName(listName).setNum(numberPerPage).setStart(start).execute();
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
                crawlingQueue.offer(new ItemCrawl(client, listName, formUrl + "?ID=" + item.getId(), item.getEditLink()));
                if (item.hasAttachments()) {
                    crawlingQueue.offer(new ItemAttachmentsCrawl(client, listName, item.getId(), item.getEditLink(), item.getCreated(), item.getModified()));
                }
            });
        }
        return null;
    }

    private String getFormUrl() {
        final GetForms getForms = client.api().list().getForms();
        if (id != null) {
            getForms.setListId(id);
        } else {
            getForms.setListName(listName);
        }
        final GetFormsResponse getFormsResponse = getForms.execute();
        GetFormsResponse.Form form = getFormsResponse.getForms().stream().filter(f -> f.getType() == PageType.DISPLAY_FORM).findFirst().orElse(null);
        if (form == null) {
            return null;
        }
        String serverRelativeUrl = form.getServerRelativeUrl();
        return client.getUrl() + serverRelativeUrl.substring(1);
    }
}
