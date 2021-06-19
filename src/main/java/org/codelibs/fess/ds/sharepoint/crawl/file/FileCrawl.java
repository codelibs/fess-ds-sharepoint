/*
 * Copyright 2012-2021 CodeLibs Project and the Others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.codelibs.fess.ds.sharepoint.crawl.file;

import org.apache.commons.lang3.StringUtils;
import org.codelibs.fess.crawler.extractor.Extractor;
import org.codelibs.fess.crawler.helper.MimeTypeHelper;
import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.client.api.file.getfile.GetFileResponse;
import org.codelibs.fess.ds.sharepoint.crawl.SharePointCrawl;
import org.codelibs.fess.exception.DataStoreCrawlingException;
import org.codelibs.fess.helper.FileTypeHelper;
import org.codelibs.fess.mylasta.direction.FessConfig;
import org.codelibs.fess.util.ComponentUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class FileCrawl extends SharePointCrawl {
    private static final Logger logger = LoggerFactory.getLogger(FileCrawl.class);

    private final String fileName;
    private final String webUrl;
    private final String serverRelativeUrl;
    private final Date created;
    private final Date modified;
    private final List<String> roles;
    private final String listName;
    private final Map<String, String> additionalProperties = new HashMap<>();

    private final String defaultExtractorName = "tikaExtractor";

    public FileCrawl(SharePointClient client, String fileName, String webUrl, String serverRelativeUrl, Date created, Date modified, List<String> roles, String listName) {
        super(client);
        this.serverRelativeUrl = serverRelativeUrl;
        this.webUrl = webUrl;
        this.fileName = fileName;
        this.created = created;
        this.modified = modified;
        this.roles = roles;
        this.listName = listName;
    }

    public void addProperty(final String key, final String value) {
        additionalProperties.put(key, value);
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
        final String fileType = getFileType(mimeType);
        final String content = getContent(is, mimeType);

        final FessConfig fessConfig = ComponentUtil.getFessConfig();
        final Map<String, Object> dataMap = new HashMap<>();
        dataMap.put(fessConfig.getIndexFieldUrl(), webUrl);
        dataMap.put(fessConfig.getIndexFieldHost(), client.helper().getHostName());
        dataMap.put(fessConfig.getIndexFieldSite(), serverRelativeUrl);
        dataMap.put(fessConfig.getIndexFieldTitle(), fileName);
        if (StringUtils.isNotBlank(listName)) {
            dataMap.put(fessConfig.getIndexFieldTitle() + "WithListName", "[" + listName + "] " + fileName);
        } else {
            dataMap.put(fessConfig.getIndexFieldTitle() + "WithListName", fileName);
        }
        dataMap.put(fessConfig.getIndexFieldMimetype(), mimeType);
        dataMap.put(fessConfig.getIndexFieldFiletype(), fileType);
        dataMap.put(fessConfig.getIndexFieldContent(), content);
        dataMap.put(fessConfig.getIndexFieldDigest(), buildDigest(content));
        dataMap.put(fessConfig.getIndexFieldContentLength(), content.length());
        dataMap.put(fessConfig.getIndexFieldLastModified(), modified);
        dataMap.put(fessConfig.getIndexFieldCreated(), created);

        if (roles != null && !roles.isEmpty()) {
            dataMap.put(fessConfig.getIndexFieldRole(), roles);
        }
        if (additionalProperties.size() > 0) {
            additionalProperties.entrySet().stream().forEach(entry -> dataMap.put(entry.getKey(), entry.getValue()));
        }
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

    protected String getFileType(final String mimeType) {
        final FileTypeHelper fileTypeHelper = ComponentUtil.getFileTypeHelper();
        return fileTypeHelper.get(mimeType);
    }

}
