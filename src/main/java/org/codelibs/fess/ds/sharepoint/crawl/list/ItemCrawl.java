package org.codelibs.fess.ds.sharepoint.crawl.list;

import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemValueResponse;
import org.codelibs.fess.ds.sharepoint.crawl.SharePointCrawl;
import org.codelibs.fess.mylasta.direction.FessConfig;
import org.codelibs.fess.util.ComponentUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

public class ItemCrawl extends SharePointCrawl {
    private static final Logger logger = LoggerFactory.getLogger(ItemCrawl.class);

    private final String listName;
    private final String webLink;
    private final String editLink;

    public ItemCrawl(SharePointClient client, String listName, String webLink, String editLink) {
        super(client);
        this.listName = listName;
        this.webLink = webLink;
        this.editLink = editLink;
    }

    @Override
    public Map<String, Object> doCrawl(Queue<SharePointCrawl> crawlingQueue) {
        if (logger.isInfoEnabled()) {
            logger.info("[Crawling ListItem] [editLink:{}]", editLink);
        }

        final GetListItemValueResponse response = client.api().list().getListItemValue().setEditLink(editLink).execute();
        final String content = buildContent(response);

        final FessConfig fessConfig = ComponentUtil.getFessConfig();
        final Map<String, Object> dataMap = new HashMap<>();
        dataMap.put(fessConfig.getIndexFieldUrl(), webLink);
        dataMap.put(fessConfig.getIndexFieldHost(), client.helper().getHostName());
        dataMap.put(fessConfig.getIndexFieldSite(), client.getSiteUrl());

        dataMap.put(fessConfig.getIndexFieldTitle(), response.getTitle());
        dataMap.put(fessConfig.getIndexFieldContent(), content);
        dataMap.put(fessConfig.getIndexFieldDigest(), content);
        //TODO anchor
        dataMap.put(fessConfig.getIndexFieldAnchor(), response.getEditLink());
        dataMap.put(fessConfig.getIndexFieldContentLength(), content.length());
        dataMap.put(fessConfig.getIndexFieldLastModified(), response.getModified());
        dataMap.put(fessConfig.getIndexFieldCreated(), response.getCreated());

        return dataMap;
    }

    private String buildContent(final GetListItemValueResponse response) {
        final StringBuilder sb = new StringBuilder();
        response.getValues().entrySet().stream().forEach(entry -> {
            sb.append(entry.getKey()).append(' ').append(entry.getValue()).append('\n');
        });
        return sb.toString();
    }
}
