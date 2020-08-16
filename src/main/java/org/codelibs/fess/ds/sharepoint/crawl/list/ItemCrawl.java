package org.codelibs.fess.ds.sharepoint.crawl.list;

import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.client.api.list.PageType;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistforms.GetForms;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistforms.GetFormsResponse;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemValueResponse;
import org.codelibs.fess.ds.sharepoint.crawl.SharePointCrawl;
import org.codelibs.fess.mylasta.direction.FessConfig;
import org.codelibs.fess.util.ComponentUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ItemCrawl extends SharePointCrawl {
    private static final Logger logger = LoggerFactory.getLogger(ItemCrawl.class);

    private final String listId;
    private final String listName;
    private final String itemId;
    private final List<String> roles;

    public ItemCrawl(SharePointClient client, String listId, String listName, String itemId, List<String> roles) {
        super(client);
        this.listId = listId;
        this.listName = listName;
        this.itemId = itemId;
        this.roles = roles;
    }

    @Override
    public Map<String, Object> doCrawl(Queue<SharePointCrawl> crawlingQueue) {
        if (logger.isInfoEnabled()) {
            logger.info("[Crawling ListItem] [listName:{}] [itemId:{}]", listName, itemId);
        }

        final GetListItemValueResponse response = client.api().list().getListItemValue().setListId(listId).setItemId(itemId).execute();
        final String content = buildContent(response);

        final String webLink = getWebLink();
        final FessConfig fessConfig = ComponentUtil.getFessConfig();
        final Map<String, Object> dataMap = new HashMap<>();
        dataMap.put(fessConfig.getIndexFieldUrl(), webLink);
        dataMap.put(fessConfig.getIndexFieldHost(), client.helper().getHostName());
        dataMap.put(fessConfig.getIndexFieldSite(), webLink.replace("http://", "").replace("https://", ""));

        dataMap.put(fessConfig.getIndexFieldTitle(), response.getTitle());
        dataMap.put(fessConfig.getIndexFieldContent(), content);
        dataMap.put(fessConfig.getIndexFieldDigest(), content);
        dataMap.put(fessConfig.getIndexFieldContentLength(), content.length());
        dataMap.put(fessConfig.getIndexFieldLastModified(), response.getModified());
        dataMap.put(fessConfig.getIndexFieldCreated(), response.getCreated());

        if (roles != null && !roles.isEmpty()) {
            dataMap.put(fessConfig.getIndexFieldRole(), roles);
        }
        return dataMap;
    }

    private String buildContent(final GetListItemValueResponse response) {
        final StringBuilder sb = new StringBuilder();
        response.getValues().entrySet().stream().forEach(entry -> {
            sb.append(entry.getKey()).append(' ').append(entry.getValue()).append('\n');
        });
        return sb.toString();
    }

    private String getWebLink() {
        final GetForms getForms = client.api().list().getForms();
        if (listId != null) {
            getForms.setListId(listId);
        }
        final GetFormsResponse getFormsResponse = getForms.execute();
        GetFormsResponse.Form form = getFormsResponse.getForms().stream().filter(f -> f.getType() == PageType.DISPLAY_FORM).findFirst().orElse(null);
        if (form == null) {
            return null;
        }
        String serverRelativeUrl = form.getServerRelativeUrl();
        return client.getUrl() + serverRelativeUrl.substring(1) + "?ID=" + itemId;
    }
}
