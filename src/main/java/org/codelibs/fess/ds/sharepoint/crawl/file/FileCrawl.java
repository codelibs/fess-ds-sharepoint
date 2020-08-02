package org.codelibs.fess.ds.sharepoint.crawl.file;

import org.codelibs.fess.crawler.extractor.Extractor;
import org.codelibs.fess.crawler.helper.MimeTypeHelper;
import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.client.api.file.getfile.GetFileResponse;
import org.codelibs.fess.ds.sharepoint.crawl.SharePointCrawl;
import org.codelibs.fess.exception.DataStoreCrawlingException;
import org.codelibs.fess.mylasta.direction.FessConfig;
import org.codelibs.fess.util.ComponentUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

public class FileCrawl extends SharePointCrawl {
    private static final Logger logger = LoggerFactory.getLogger(FileCrawl.class);

    private final String fileName;
    private final String webUrl;
    private final String serverRelativeUrl;
    private final Date created;
    private final Date modified;

    private final String defaultExtractorName = "tikaExtractor";

    public FileCrawl(SharePointClient client, String fileName, String webUrl, String serverRelativeUrl, Date created, Date modified) {
        super(client);
        this.serverRelativeUrl = serverRelativeUrl;
        this.webUrl = webUrl;
        this.fileName = fileName;
        this.created = created;
        this.modified = modified;
    }

    @Override
    public Map<String, Object> doCrawl(Queue<SharePointCrawl> crawlingQueue) {
        if (logger.isInfoEnabled()) {
            logger.info("[Crawling File] [serverRelativeUrl:{}]", serverRelativeUrl);
        }

        try(GetFileResponse getFileResponse = client.api().file().getFile().setServerRelativeUrl(serverRelativeUrl).execute()) {
            return buildDataMap(getFileResponse);
        } catch (IOException e) {
            throw new DataStoreCrawlingException(serverRelativeUrl, "Failed to file: " + fileName, e);
        }
    }

    private Map<String, Object> buildDataMap(GetFileResponse response) throws IOException {
        final InputStream is = response.getFileContent();
        final String mimeType = getMimeType(fileName, is);
        final String content = getContent(is, mimeType);

        final FessConfig fessConfig = ComponentUtil.getFessConfig();
        final Map<String, Object> dataMap = new HashMap<>();
        dataMap.put(fessConfig.getIndexFieldUrl(), webUrl);
        dataMap.put(fessConfig.getIndexFieldHost(), client.helper().getHostName());
        dataMap.put(fessConfig.getIndexFieldSite(), client.getSiteUrl());

        dataMap.put(fessConfig.getIndexFieldTitle(), fileName);
        dataMap.put(fessConfig.getIndexFieldMimetype(), mimeType);
        dataMap.put(fessConfig.getIndexFieldContent(), content);
        dataMap.put(fessConfig.getIndexFieldDigest(), content);
        //TODO anchor
        dataMap.put(fessConfig.getIndexFieldAnchor(), serverRelativeUrl);
        dataMap.put(fessConfig.getIndexFieldContentLength(), content.length());
        dataMap.put(fessConfig.getIndexFieldLastModified(), modified);
        dataMap.put(fessConfig.getIndexFieldCreated(), created);
        return dataMap;
    }

    private String getContent(final InputStream is, final String mimeType) {
        try {
            Extractor extractor = ComponentUtil.getExtractorFactory().getExtractor(mimeType);
            if (extractor == null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("use a defautl extractor as {} by {}", defaultExtractorName, mimeType);
                }
                extractor = ComponentUtil.getComponent(defaultExtractorName);
            }
            return extractor.getText(is, null).getContent();
        } catch (final Exception e) {
            throw new DataStoreCrawlingException(serverRelativeUrl, "Failed to get contents: " + fileName, e);
        }
    }

    protected String getMimeType(final String filename, final InputStream is) {
        final MimeTypeHelper mimeTypeHelper = ComponentUtil.getComponent(MimeTypeHelper.class);
        return mimeTypeHelper.getContentType(is, filename);
    }

}
