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
package org.codelibs.fess.ds.sharepoint.crawl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.codelibs.fess.crawler.filter.UrlFilter;
import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.client.api.list.PageType;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistforms.GetForms;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistforms.GetFormsResponse;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemRoleResponse;
import org.codelibs.fess.helper.CrawlerStatsHelper.StatsKeyObject;
import org.codelibs.fess.helper.SystemHelper;
import org.codelibs.fess.opensearch.config.exentity.DataConfig;
import org.codelibs.fess.util.ComponentUtil;

/**
 * Abstract base class for SharePoint crawling operations.
 */
public abstract class SharePointCrawl {

    /** The SharePoint client for API access. */
    protected final SharePointClient client;

    /** The statistics key object for tracking crawl statistics. */
    protected StatsKeyObject statsKey;

    /**
     * Creates a new SharePointCrawl instance.
     *
     * @param client the SharePoint client to use for API access
     */
    protected SharePointCrawl(final SharePointClient client) {
        this.client = client;
    }

    /**
     * Performs the crawling operation for this SharePoint object.
     *
     * @param dataConfig the data configuration for the crawling operation
     * @param crawlingQueue the queue to add additional crawling tasks to
     * @return a map containing the crawled data
     */
    public abstract Map<String, Object> doCrawl(final DataConfig dataConfig, final Queue<SharePointCrawl> crawlingQueue);

    /**
     * Retrieves the roles for a list item.
     *
     * @param listId the list ID
     * @param itemId the item ID
     * @param sharePointGroupCache cache for SharePoint groups
     * @param skipRole if true, returns an empty list without fetching roles
     * @return list of role identifiers
     */
    protected List<String> getItemRoles(final String listId, final String itemId,
            final Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache, final boolean skipRole) {
        if (skipRole) {
            return new ArrayList<>();
        }
        final GetListItemRoleResponse getListItemRoleResponse =
                client.api().list().getListItemRole().setId(listId, itemId).setSharePointGroupCache(sharePointGroupCache).execute();
        final Set<String> roles =
                new HashSet<>(collectRoles(getListItemRoleResponse.getUsers(), getListItemRoleResponse.getSecurityGroups()));
        getListItemRoleResponse.getSharePointGroups()
                .stream()
                .flatMap(group -> getSharePointGroupTitles(group, sharePointGroupCache, new HashSet<>()).stream())
                .forEach(roles::add);
        return roles.stream().collect(Collectors.toUnmodifiableList());
    }

    /**
     * Maps the principals of one permission scope to Fess role strings.
     *
     * <p>Both the item scope and the SharePoint group scope funnel through here. They used to
     * carry two copies of this mapping, and the copies had drifted: the item scope stripped only
     * the 7-character on-premises prefix from an Azure login name and left "membership|" on the
     * front, so the same user got a different role depending on how the permission reached them.
     *
     * @param users the users of this scope
     * @param securityGroups the security groups of this scope
     * @return the role strings for this scope
     */
    private Set<String> collectRoles(final List<GetListItemRoleResponse.User> users,
            final List<GetListItemRoleResponse.SecurityGroup> securityGroups) {
        final SystemHelper systemHelper = ComponentUtil.getSystemHelper();
        final Set<String> roles = new HashSet<>();
        // AD
        users.stream()
                .filter(user -> !user.isAzureAccount())
                .map(GetListItemRoleResponse.User::getAccount)
                .filter(title -> title.contains("\\"))
                .map(systemHelper::getSearchRoleByUser)
                .forEach(roles::add);
        securityGroups.stream()
                .map(GetListItemRoleResponse.SecurityGroup::getTitle)
                .filter(title -> title.contains("\\"))
                .map(systemHelper::getSearchRoleByGroup)
                .forEach(roles::add);
        // AzureAD
        users.stream()
                .filter(GetListItemRoleResponse.User::isAzureAccount)
                .map(GetListItemRoleResponse.User::getAzureAccount)
                .map(systemHelper::getSearchRoleByUser)
                .forEach(roles::add);
        users.stream()
                .filter(GetListItemRoleResponse.User::isAzureAccount)
                .map(GetListItemRoleResponse.User::getAdAccountFromAzureAccount)
                .map(systemHelper::getSearchRoleByUser)
                .forEach(roles::add);
        securityGroups.stream()
                .filter(GetListItemRoleResponse.SecurityGroup::isAzureAccount)
                .map(GetListItemRoleResponse.SecurityGroup::getAzureAccount)
                .map(systemHelper::getSearchRoleByGroup)
                .forEach(roles::add);
        securityGroups.stream()
                .filter(GetListItemRoleResponse.SecurityGroup::isAzureAccount)
                .map(GetListItemRoleResponse.SecurityGroup::getTitle)
                .map(systemHelper::getSearchRoleByGroup)
                .forEach(roles::add);
        return roles;
    }

    private Set<String> getSharePointGroupTitles(final GetListItemRoleResponse.SharePointGroup sharePointGroup,
            final Map<String, GetListItemRoleResponse.SharePointGroup> sharePointGroupCache, final Set<String> visitedGroupIds) {
        if (sharePointGroup.getId() != null && !visitedGroupIds.add(sharePointGroup.getId())) {
            // Already expanded on this path. SharePoint lets groups contain each other, so
            // without this the recursion never terminates.
            return Collections.emptySet();
        }
        final Set<String> titles = new HashSet<>(collectRoles(sharePointGroup.getUsers(), sharePointGroup.getSecurityGroups()));
        sharePointGroup.getSharePointGroups()
                .stream()
                .flatMap(group -> getSharePointGroupTitles(group, sharePointGroupCache, visitedGroupIds).stream())
                .forEach(titles::add);
        return titles;
    }

    /**
     * Returns the server-relative URL of a list's DISPLAY_FORM, the form the three crawl paths
     * below list level all use to build a web link.
     *
     * <p>The result depends only on {@code listId}, but {@code ItemCrawl}, {@code FolderCrawl}
     * and {@code ItemAttachmentsCrawl} each used to re-fetch it once per list item, per file, and
     * per attachment - the same request repeated for every item in a list that may hold
     * thousands. {@code formsCache} is shared across a whole crawl the same way
     * {@code sharePointGroupCache} is, so the first caller to ask about a given list fetches it
     * and every later caller for that list is answered from the cache - as long as the list has a
     * DISPLAY_FORM. {@code computeIfAbsent} does not store a null, so a list with no such form is
     * re-fetched by every caller, exactly as it was before.
     *
     * @param listId the list's GUID, or null if the caller has none
     * @param formsCache the cache to read from and populate, keyed by listId
     * @return the DISPLAY_FORM's server-relative URL, or null if the list has no such form
     */
    protected String getDisplayFormUrl(final String listId, final Map<String, String> formsCache) {
        if (listId == null) {
            return fetchDisplayFormUrl(null);
        }
        return formsCache.computeIfAbsent(listId, this::fetchDisplayFormUrl);
    }

    /**
     * Fetches the server-relative URL of a list's DISPLAY_FORM directly, with no caching.
     *
     * @param listId the list's GUID, or null
     * @return the DISPLAY_FORM's server-relative URL, or null if the list has no such form
     */
    private String fetchDisplayFormUrl(final String listId) {
        final GetForms getForms = client.api().list().getForms();
        if (listId != null) {
            getForms.setListId(listId);
        }
        final GetFormsResponse getFormsResponse = getForms.execute();
        return getFormsResponse.getForms()
                .stream()
                .filter(form -> form.getType() == PageType.DISPLAY_FORM)
                .findFirst()
                .map(GetFormsResponse.Form::getServerRelativeUrl)
                .orElse(null);
    }

    /**
     * Builds a digest string from the content.
     *
     * @param content the content to create a digest from
     * @return the digest string, truncated to maximum length
     */
    protected String buildDigest(final String content) {
        final int maxLength = ComponentUtil.getFessConfig().getCrawlerDocumentFileMaxDigestLengthAsInteger();
        return StringUtils.abbreviate(content, maxLength);
    }

    /**
     * Checks a crawled item's URL-ish value against a pre-built {@link UrlFilter}.
     *
     * <p>The filter is built once per crawl, in {@code SharePointCrawler}'s constructor via
     * {@code ComponentUtil.getComponent(UrlFilter.class)} - the same component every other
     * {@code fess-ds-*} plugin with this parameter uses - and threaded down to each crawl unit
     * from there, rather than rebuilt on every call: {@link UrlFilter} is session-scoped and
     * backed by an in-memory service keyed by session ID, so building and initializing a fresh one
     * per file would keep accumulating patterns under whatever session key was used, in a map nothing
     * ever clears mid-crawl.
     *
     * <p>A null filter (no patterns configured, or the component unavailable in a container that
     * does not wire it - such as this plugin's own unit tests) means nothing is filtered.
     *
     * @param urlFilter the filter built for this crawl, or null
     * @param value the URL-ish value to check - a server-relative path for a document library file
     *            or a list item attachment, a list item's {@code FileRef} for a list item
     * @return true if the item should be crawled
     */
    protected boolean isUrlAllowed(final UrlFilter urlFilter, final String value) {
        return urlFilter == null || StringUtils.isBlank(value) || urlFilter.match(value);
    }

    /**
     * Returns the statistics key object for this crawl.
     *
     * @return the stats key object
     */
    public StatsKeyObject getStatsKey() {
        return statsKey;
    }
}
