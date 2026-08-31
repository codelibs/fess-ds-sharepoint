/*
 * Copyright 2012-2025 CodeLibs Project and the Others.
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
import java.util.Arrays;
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
import org.codelibs.fess.crawler.exception.MaxLengthExceededException;
import org.codelibs.fess.crawler.helper.MimeTypeHelper;
import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.client.api.file.getfile.GetFileResponse;
import org.codelibs.fess.ds.sharepoint.crawl.SharePointCrawl;
import org.codelibs.fess.exception.DataStoreCrawlingException;
import org.codelibs.fess.helper.CrawlerStatsHelper.StatsKeyObject;
import org.codelibs.fess.helper.FileTypeHelper;
import org.codelibs.fess.mylasta.direction.FessConfig;
import org.codelibs.fess.opensearch.config.exentity.DataConfig;
import org.codelibs.fess.util.ComponentUtil;

/**
 * Crawling class for SharePoint file items.
 * Handles extraction and indexing of individual files from SharePoint document libraries.
 */
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
    private final boolean ignoreError;
    private final String extractorName;
    private final String[] supportedMimeTypes;
    private final long maxContentLength;

    /** The extractor component used unless {@code extractor_name} configures a different one. */
    public static final String DEFAULT_EXTRACTOR_NAME = "tikaExtractor";

    /** The pattern list used unless {@code supported_mimetypes} configures a different one: every MIME type. */
    public static final String[] DEFAULT_SUPPORTED_MIMETYPES = { ".*" };

    /** The bound used unless {@code max_content_length} configures a different one: unlimited. */
    public static final long DEFAULT_MAX_CONTENT_LENGTH = -1L;

    /**
     * Constructs a FileCrawl instance for crawling a specific SharePoint file.
     *
     * @param client the SharePoint client for API communication
     * @param fileName the name of the file to crawl
     * @param webUrl the web URL for the file
     * @param serverRelativeUrl the server-relative URL of the file
     * @param created the file creation date
     * @param modified the file modification date
     * @param roles the list of roles/permissions for the file
     * @param listValues additional metadata values from the list item
     * @param listName the name of the list containing the file
     * @param ignoreError whether a content extraction failure should be logged instead of failing
     *            this crawl target
     * @param extractorName the name of the extractor component used to extract file content
     * @param supportedMimeTypes regular expressions a file's MIME type must match at least one of
     *            to be crawled
     * @param maxContentLength the maximum file size in bytes, or a negative number for no limit
     */
    public FileCrawl(final SharePointClient client, final String fileName, final String webUrl, final String serverRelativeUrl,
            final Date created, final Date modified, final List<String> roles, final Map<String, String> listValues, final String listName,
            final boolean ignoreError, final String extractorName, final String[] supportedMimeTypes, final long maxContentLength) {
        super(client);
        this.serverRelativeUrl = serverRelativeUrl;
        this.webUrl = webUrl;
        this.fileName = fileName;
        this.created = created;
        this.modified = modified;
        this.roles = roles;
        this.listValues = listValues;
        this.listName = listName != null ? listName : StringUtil.EMPTY;
        this.ignoreError = ignoreError;
        this.extractorName = extractorName;
        this.supportedMimeTypes = supportedMimeTypes;
        this.maxContentLength = maxContentLength;
        statsKey = new StatsKeyObject("file#" + serverRelativeUrl);
    }

    /**
     * Adds an additional property to be included in the crawled data.
     *
     * @param key the property key
     * @param value the property value
     */
    public void addProperty(final String key, final String value) {
        additionalProperties.put(key, value);
    }

    @Override
    public Map<String, Object> doCrawl(final DataConfig dataConfig, final Queue<SharePointCrawl> crawlingQueue) {
        if (logger.isInfoEnabled()) {
            logger.info("[Crawling File] [serverRelativeUrl:{}]", serverRelativeUrl);
        }

        // Checked against the file name alone, before any request for the file is made: SharePoint's
        // listing APIs carry no content to sniff at this point, but MimeTypeHelper can resolve a
        // type from a file name by itself (passing a null stream makes it skip content detection
        // entirely), so a file supported_mimetypes excludes never costs the download this crawl
        // would otherwise spend on it.
        if (!isSupportedMimeType(fileName)) {
            if (logger.isDebugEnabled()) {
                logger.debug("{} is not a supported mimetype.", fileName);
            }
            return null;
        }

        try (GetFileResponse getFileResponse = client.api().file().getFile().setServerRelativeUrl(serverRelativeUrl).execute()) {
            checkContentLength(getFileResponse);
            return buildDataMap(dataConfig, getFileResponse);
        } catch (final MaxLengthExceededException e) {
            // A deliberate policy exclusion, not a crawl failure - the same shape as a
            // supported_mimetypes mismatch just above. It must not be reported as a lost crawl
            // target: SharePointDataStore#store suppresses delete_old_docs for the whole run when
            // any target is, and a file being over a configured size limit on purpose is not that.
            if (logger.isDebugEnabled()) {
                logger.debug("{} exceeds max_content_length.", serverRelativeUrl, e);
            } else {
                logger.info("{} exceeds max_content_length. {}", serverRelativeUrl, e.getMessage());
            }
            return null;
        } catch (final IOException e) {
            throw new DataStoreCrawlingException(serverRelativeUrl, "Failed to file: " + fileName, e);
        }
    }

    /**
     * Rejects a file whose declared {@code Content-Length} is over {@code max_content_length},
     * before {@link GetFileResponse#getFileContent(long)} downloads it.
     *
     * <p>{@link GetFileResponse#getContentLength()} returns -1 - and this check does not fire -
     * whenever the response does not declare an upfront length, which includes a gzip-compressed
     * response as well as a chunked one (this plugin's client never disables content compression).
     * {@link #getContent}'s call to {@link GetFileResponse#getFileContent(long)} is the backstop
     * that still applies then: it bounds the download itself, not just extraction, by counting
     * bytes as they are copied out of the response.
     *
     * @param response the file response to check
     * @throws MaxLengthExceededException if the declared content length is over the configured
     *             maximum
     */
    private void checkContentLength(final GetFileResponse response) {
        if (maxContentLength < 0) {
            return;
        }
        final long contentLength = response.getContentLength();
        if (contentLength >= 0 && contentLength > maxContentLength) {
            throw new MaxLengthExceededException("The content length (" + contentLength + " byte) is over " + maxContentLength
                    + " byte. The url is " + serverRelativeUrl);
        }
    }

    /**
     * Checks whether a file name's MIME type, resolved without downloading the file, matches at
     * least one {@code supported_mimetypes} pattern.
     *
     * @param filename the file name to resolve a MIME type from
     * @return true if the file should be crawled
     */
    protected boolean isSupportedMimeType(final String filename) {
        final MimeTypeHelper mimeTypeHelper = ComponentUtil.getComponent(MimeTypeHelper.class);
        final String mimeType = mimeTypeHelper.getContentType(null, filename);
        return Arrays.stream(supportedMimeTypes).anyMatch(mimeType::matches);
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

        try (final InputStream is = response.getFileContent(maxContentLength)) {
            final String fileText = ComponentUtil.getExtractorFactory()
                    .builder(is, null)
                    .extractorName(extractorName)
                    .mimeType(mimeType)
                    .maxContentLength(maxContentLength)
                    .extract()
                    .getContent();
            if (StringUtils.isNotBlank(fileText)) {
                content.append(fileText);
            }
        } catch (final MaxLengthExceededException e) {
            // Reached only if the body were not already fully buffered (and validated) by
            // getMimeType's earlier call to the same bounded getFileContent - not the normal case,
            // but re-thrown rather than swallowed by ignore_error below either way, so doCrawl's
            // own catch treats this the same as the Content-Length precheck: a skip, not a
            // "continue with whatever text extraction managed" outcome.
            throw e;
        } catch (final Exception e) {
            // An OR, not a per-data-config override: this is suppressed whenever EITHER
            // ignore_error is true OR the global crawler.ignore.content.exception setting is
            // true. ignore_error=false alone cannot force a hard failure while the global setting
            // says to ignore - it only matters when the global setting is false. That is why
            // ignore_error defaults to false rather than to the true every sibling fess-ds-*
            // plugin uses: at false this whole condition reduces to the global setting alone,
            // which is what this plugin did before the parameter existed, on an installation that
            // has left the global setting at Fess's own default of true as well as on one that
            // had deliberately set it to false to get hard failures. Setting ignore_error=true
            // suppresses the failure regardless of the global setting.
            if (!ignoreError && !ComponentUtil.getFessConfig().isCrawlerIgnoreContentException()) {
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

    /**
     * Determines the MIME type of the file based on its content and filename.
     *
     * @param filename the name of the file
     * @param response the file response containing the file content
     * @return the MIME type of the file
     */
    protected String getMimeType(final String filename, final GetFileResponse response) {
        try (final InputStream is = response.getFileContent(maxContentLength)) {
            final MimeTypeHelper mimeTypeHelper = ComponentUtil.getComponent(MimeTypeHelper.class);
            return mimeTypeHelper.getContentType(is, filename);
        } catch (final IOException e) {
            throw new IORuntimeException(e);
        }
    }

    /**
     * Gets the file type based on the MIME type.
     *
     * @param mimeType the MIME type of the file
     * @return the corresponding file type
     */
    protected String getFileType(final String mimeType) {
        final FileTypeHelper fileTypeHelper = ComponentUtil.getFileTypeHelper();
        return fileTypeHelper.get(mimeType);
    }

}
