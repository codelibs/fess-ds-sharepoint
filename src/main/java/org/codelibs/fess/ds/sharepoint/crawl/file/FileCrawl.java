/*
 * Copyright 2012-2024 CodeLibs Project and the Others.
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

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.core.exception.IORuntimeException;
import org.codelibs.core.lang.StringUtil;
import org.codelibs.fess.crawler.helper.MimeTypeHelper;
import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.client.api.file.getfile.GetFileResponse;
import org.codelibs.fess.ds.sharepoint.crawl.SharePointCrawl;
import org.codelibs.fess.es.config.exentity.DataConfig;
import org.codelibs.fess.exception.DataStoreCrawlingException;
import org.codelibs.fess.helper.CrawlerStatsHelper.StatsKeyObject;
import org.codelibs.fess.helper.FileTypeHelper;
import org.codelibs.fess.mylasta.direction.FessConfig;
import org.codelibs.fess.util.ComponentUtil;

public class FileCrawl extends SharePointCrawl {
    private static final Logger logger = LogManager.getLogger(FileCrawl.class);

    private final String fileName;
    private final String webUrl;
    private final String serverRelativeUrl;
    private final Date created;
    private final Date modified;
    private final List<String> roles;
    private final Map<String, String> listValues;
    private final String listName;
    private final Map<String, String> additionalProperties = new HashMap<>();

    private static final String DEFAULT_EXTRACTOR_NAME = "tikaExtractor";

    public FileCrawl(final SharePointClient client, final String fileName, final String webUrl, final String serverRelativeUrl,
            final Date created, final Date modified, final List<String> roles, final Map<String, String> listValues,
            final String listName) {
        super(client);
        this.serverRelativeUrl = serverRelativeUrl;
        this.webUrl = webUrl;
        this.fileName = fileName;
        this.created = created;
        this.modified = modified;
        this.roles = roles;
        this.listValues = listValues;
        this.listName = listName != null ? listName : StringUtil.EMPTY;
        statsKey = new StatsKeyObject("file#" + serverRelativeUrl);
    }

    public void addProperty(final String key, final String value) {
        additionalProperties.put(key, value);
    }

    @Override
    public Map<String, Object> doCrawl(final DataConfig dataConfig, final Queue<SharePointCrawl> crawlingQueue) {
        if (logger.isInfoEnabled()) {
            logger.info("[Crawling File] [serverRelativeUrl:{}]", serverRelativeUrl);
        }

        try (GetFileResponse getFileResponse = client.api().file().getFile().setServerRelativeUrl(serverRelativeUrl).execute()) {
            return buildDataMap(dataConfig, getFileResponse);
        } catch (final IOException e) {
            throw new DataStoreCrawlingException(serverRelativeUrl, "Failed to file: " + fileName, e);
        }
    }

    private Map<String, Object> buildDataMap(final DataConfig dataConfig, final GetFileResponse response) throws IOException {
        final String mimeType = getMimeType(fileName, response);
        final String fileType = getFileType(mimeType);
        final String content = getContent(response, mimeType);

        final FessConfig fessConfig = ComponentUtil.getFessConfig();
        final Map<String, Object> dataMap = new HashMap<>();
        dataMap.put(fessConfig.getIndexFieldUrl(), webUrl);
        dataMap.put(fessConfig.getIndexFieldHost(), client.helper().getHostName());
        dataMap.put(fessConfig.getIndexFieldSite(), serverRelativeUrl);
        if (listValues.containsKey("Title") && StringUtils.isNotBlank(listValues.get("Title"))) {
            dataMap.put(fessConfig.getIndexFieldTitle(), listValues.get("Title"));
        } else {
            dataMap.put(fessConfig.getIndexFieldTitle(), fileName);
        }
        if (StringUtils.isNotBlank(listName)) {
            dataMap.put(fessConfig.getIndexFieldTitle() + "WithListName", "[" + listName + "] " + fileName);
        } else {
            dataMap.put(fessConfig.getIndexFieldTitle() + "WithListName", fileName);
        }
        dataMap.put("listName", listName);
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

    private String getContent(final GetFileResponse response, final String mimeType) {
        final StringBuilder content = new StringBuilder(1000);

        try (final InputStream is = response.getFileContent()) {
            final String fileText = ComponentUtil.getExtractorFactory().builder(is, null).extractorName(DEFAULT_EXTRACTOR_NAME)
                    .mimeType(mimeType).extract().getContent();
            if (StringUtils.isNotBlank(fileText)) {
                content.append(fileText);
            }
        } catch (final Exception e) {
            if (!ComponentUtil.getFessConfig().isCrawlerIgnoreContentException()) {
                throw new DataStoreCrawlingException(serverRelativeUrl, "Failed to get contents: " + fileName, e);
            }
            if (logger.isDebugEnabled()) {
                logger.warn("Could not get a text.", e);
            } else {
                logger.warn("Could not get a text. {}", e.getMessage());
            }
        }
        if (listValues.containsKey("Description") && StringUtils.isNotBlank(listValues.get("Description"))) {
            content.append(' ').append(listValues.get("Description"));
        }
        if (listValues.containsKey("Keywords") && StringUtils.isNotBlank(listValues.get("Keywords"))) {
            content.append(' ').append(listValues.get("Keywords"));
        }
        return content.toString();
    }

    protected String getMimeType(final String filename, final GetFileResponse response) {
        try (final InputStream is = response.getFileContent()) {
            final MimeTypeHelper mimeTypeHelper = ComponentUtil.getComponent(MimeTypeHelper.class);
            return mimeTypeHelper.getContentType(is, filename);
        } catch (final IOException e) {
            throw new IORuntimeException(e);
        }
    }

    protected String getFileType(final String mimeType) {
        final FileTypeHelper fileTypeHelper = ComponentUtil.getFileTypeHelper();
        return fileTypeHelper.get(mimeType);
    }

}
