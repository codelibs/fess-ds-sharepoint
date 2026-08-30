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
package org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.codelibs.fess.ds.sharepoint.client.exception.SharePointServerException;
import org.codelibs.fess.ds.sharepoint.util.SharePointMockServer;
import org.codelibs.fess.util.ComponentUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;

public class GetListItemRoleTest extends UnitDsTestCase {
    private GetListItemRole getListItemRole;

    @Override
    protected String prepareConfigFile() {
        return "test_app.xml";
    }

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Override
    public void setUp(TestInfo testInfo) throws Exception {
        super.setUp(testInfo);
        getListItemRole = new GetListItemRole(null, "https://example.sharepoint.com/sites/test", null);
    }

    @Override
    public void tearDown(TestInfo testInfo) throws Exception {
        ComponentUtil.setFessConfig(null);
        super.tearDown(testInfo);
    }

    // === isLimitedAccessOnly tests ===

    @Test
    public void test_isLimitedAccessOnly_nullBindings() {
        final Map<String, Object> roleAssignment = new HashMap<>();
        // RoleDefinitionBindings is null
        assertTrue(getListItemRole.isLimitedAccessOnly(roleAssignment));
    }

    @Test
    public void test_isLimitedAccessOnly_emptyBindings() {
        final Map<String, Object> roleAssignment = new HashMap<>();
        roleAssignment.put("RoleDefinitionBindings", new ArrayList<>());
        assertTrue(getListItemRole.isLimitedAccessOnly(roleAssignment));
    }

    @Test
    public void test_isLimitedAccessOnly_limitedAccessOnly() {
        // RoleTypeKind=1 (Guest/Limited Access) only -> should be filtered out
        final Map<String, Object> roleAssignment = createRoleAssignment(1);
        assertTrue(getListItemRole.isLimitedAccessOnly(roleAssignment));
    }

    @Test
    public void test_isLimitedAccessOnly_noneOnly() {
        // RoleTypeKind=0 (None) only -> should be filtered out
        final Map<String, Object> roleAssignment = createRoleAssignment(0);
        assertTrue(getListItemRole.isLimitedAccessOnly(roleAssignment));
    }

    @Test
    public void test_isLimitedAccessOnly_noneAndLimitedAccess() {
        // RoleTypeKind 0 and 1 -> should be filtered out
        final Map<String, Object> roleAssignment = createRoleAssignment(0, 1);
        assertTrue(getListItemRole.isLimitedAccessOnly(roleAssignment));
    }

    @Test
    public void test_isLimitedAccessOnly_readerRole() {
        // RoleTypeKind=2 (Reader) -> should NOT be filtered out
        final Map<String, Object> roleAssignment = createRoleAssignment(2);
        assertFalse(getListItemRole.isLimitedAccessOnly(roleAssignment));
    }

    @Test
    public void test_isLimitedAccessOnly_contributorRole() {
        // RoleTypeKind=3 (Contributor) -> should NOT be filtered out
        final Map<String, Object> roleAssignment = createRoleAssignment(3);
        assertFalse(getListItemRole.isLimitedAccessOnly(roleAssignment));
    }

    @Test
    public void test_isLimitedAccessOnly_webDesignerRole() {
        // RoleTypeKind=4 (WebDesigner) -> should NOT be filtered out
        final Map<String, Object> roleAssignment = createRoleAssignment(4);
        assertFalse(getListItemRole.isLimitedAccessOnly(roleAssignment));
    }

    @Test
    public void test_isLimitedAccessOnly_administratorRole() {
        // RoleTypeKind=5 (Administrator) -> should NOT be filtered out
        final Map<String, Object> roleAssignment = createRoleAssignment(5);
        assertFalse(getListItemRole.isLimitedAccessOnly(roleAssignment));
    }

    @Test
    public void test_isLimitedAccessOnly_mixedLimitedAccessAndContributor() {
        // RoleTypeKind 1 (Limited Access) + 3 (Contributor) -> should NOT be filtered out
        final Map<String, Object> roleAssignment = createRoleAssignment(1, 3);
        assertFalse(getListItemRole.isLimitedAccessOnly(roleAssignment));
    }

    @Test
    public void test_isLimitedAccessOnly_mixedLimitedAccessAndReader() {
        // RoleTypeKind 1 (Limited Access) + 2 (Reader) -> should NOT be filtered out
        final Map<String, Object> roleAssignment = createRoleAssignment(1, 2);
        assertFalse(getListItemRole.isLimitedAccessOnly(roleAssignment));
    }

    @Test
    public void test_isLimitedAccessOnly_roleTypeKindAsString() {
        // RoleTypeKind as String "2" (Reader) -> should NOT be filtered out
        final Map<String, Object> roleAssignment = new HashMap<>();
        final List<Map<String, Object>> bindings = new ArrayList<>();
        final Map<String, Object> binding = new HashMap<>();
        binding.put("RoleTypeKind", "2");
        bindings.add(binding);
        roleAssignment.put("RoleDefinitionBindings", bindings);
        assertFalse(getListItemRole.isLimitedAccessOnly(roleAssignment));
    }

    @Test
    public void test_isLimitedAccessOnly_roleTypeKindAsStringLimitedAccess() {
        // RoleTypeKind as String "1" (Limited Access) -> should be filtered out
        final Map<String, Object> roleAssignment = new HashMap<>();
        final List<Map<String, Object>> bindings = new ArrayList<>();
        final Map<String, Object> binding = new HashMap<>();
        binding.put("RoleTypeKind", "1");
        bindings.add(binding);
        roleAssignment.put("RoleDefinitionBindings", bindings);
        assertTrue(getListItemRole.isLimitedAccessOnly(roleAssignment));
    }

    @Test
    public void test_isLimitedAccessOnly_nullRoleTypeKind() {
        // RoleTypeKind is null in binding -> treated as no valid permission
        final Map<String, Object> roleAssignment = new HashMap<>();
        final List<Map<String, Object>> bindings = new ArrayList<>();
        final Map<String, Object> binding = new HashMap<>();
        binding.put("RoleTypeKind", null);
        bindings.add(binding);
        roleAssignment.put("RoleDefinitionBindings", bindings);
        assertTrue(getListItemRole.isLimitedAccessOnly(roleAssignment));
    }

    @Test
    public void test_isLimitedAccessOnly_multipleRealPermissions() {
        // Multiple real permissions: Reader + Contributor + Administrator
        final Map<String, Object> roleAssignment = createRoleAssignment(2, 3, 5);
        assertFalse(getListItemRole.isLimitedAccessOnly(roleAssignment));
    }

    // === Group expansion tests ===

    @Test
    public void test_nestedGroupKeepsItsOwnTitle() throws Exception {
        // A group nested inside another must be named after itself, not after its parent.
        try (SharePointMockServer server = new SharePointMockServer()) {
            stubSingleGroupAssignment(server, "7", "Marketing");
            // Registered per path rather than per page, so this test says nothing about paging
            // and fails only if the nested group is misnamed.
            server.onPathStatus(usersPath("7"), 200, JSON,
                    "{\"value\":[{\"Id\":\"8\",\"Title\":\"Engineering\",\"LoginName\":\"\",\"PrincipalType\":8}]}");
            server.onPathStatus(usersPath("8"), 200, JSON,
                    "{\"value\":[{\"Id\":\"9\",\"Title\":\"Alice\",\"LoginName\":\"i:0#.f|membership|alice@example.com\","
                            + "\"PrincipalType\":1}]}");
            server.start();

            final GetListItemRoleResponse.SharePointGroup parent = executeAgainst(server).getSharePointGroups().get(0);

            assertEquals("the outer group must keep its own title", "Marketing", parent.getTitle());
            assertEquals("the outer group must hold exactly the nested group", 1, parent.getSharePointGroups().size());
            final GetListItemRoleResponse.SharePointGroup nested = parent.getSharePointGroups().get(0);
            assertEquals("the nested group must carry its own title", "Engineering", nested.getTitle());
            assertEquals("the nested group's member must be read from the nested group", 1, nested.getUsers().size());
        }
    }

    @Test
    @Timeout(value = 30, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_cyclicGroupMembershipTerminates() throws Exception {
        // Group A contains group B, group B contains group A. This must return rather than
        // recurse until the stack runs out.
        try (SharePointMockServer server = new SharePointMockServer()) {
            stubSingleGroupAssignment(server, "7", "A");
            // Registered per path rather than per page, so this test says nothing about paging
            // and fails only if the cycle is not cut.
            server.onPathStatus(usersPath("7"), 200, JSON,
                    "{\"value\":[{\"Id\":\"8\",\"Title\":\"B\",\"LoginName\":\"\",\"PrincipalType\":8}]}");
            server.onPathStatus(usersPath("8"), 200, JSON,
                    "{\"value\":[{\"Id\":\"7\",\"Title\":\"A\",\"LoginName\":\"\",\"PrincipalType\":8}]}");
            server.start();

            final GetListItemRoleResponse.SharePointGroup groupA = executeAgainst(server).getSharePointGroups().get(0);

            assertEquals("the outer group must be A", "A", groupA.getTitle());
            final GetListItemRoleResponse.SharePointGroup groupB = groupA.getSharePointGroups().get(0);
            assertEquals("A must contain B", "B", groupB.getTitle());
            assertSame("B must point back at the very same A rather than a fresh expansion of it", groupA,
                    groupB.getSharePointGroups().get(0));
        }
    }

    @Test
    public void test_aFullPageOfLimitedAccessDoesNotEndTheListing() throws Exception {
        // Limited Access assignments are filtered out of the result, and a list with broken
        // inheritance routinely has a whole page of them. The listing used to stop as soon as a
        // page contributed nothing, so every role assignment on the pages after it was lost.
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathQuery(ITEM_PATH + "/RoleAssignments", MEMBER_PAGING_QUERY + EXPAND_QUERY, JSON,
                    roleAssignmentPage("5", 1, PAGE_SIZE));
            server.onPathQuery(ITEM_PATH + "/RoleAssignments", "%24skip=200&%24top=200" + EXPAND_QUERY, JSON,
                    "{\"value\":[{\"PrincipalId\":12,\"RoleDefinitionBindings\":[{\"RoleTypeKind\":3}]}]}");
            server.onPathStatus(ITEM_PATH + "/RoleAssignments/GetByPrincipalId(12)/Member", 200, JSON,
                    "{\"Id\":\"12\",\"Title\":\"Carol\",\"LoginName\":\"i:0#.f|membership|carol@example.com\",\"PrincipalType\":1}");
            server.start();

            final GetListItemRoleResponse response = executeAgainst(server);

            assertEquals("the real assignment behind the Limited Access page must be read", 1, response.getUsers().size());
            assertEquals("and it must be the one from the second page", "Carol", response.getUsers().get(0).getTitle());
        }
    }

    @Test
    public void test_memberIsExpandedInlineInsteadOfFetchedPerPrincipal() throws Exception {
        // Each role assignment used to cost a second GET to resolve its principal. Expanding
        // Member inline removes that request entirely: the assignment listing already carries
        // the principal's fields nested under "Member".
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathQuery(ITEM_PATH + "/RoleAssignments", MEMBER_PAGING_QUERY + EXPAND_QUERY, JSON,
                    "{\"value\":[{\"PrincipalId\":12,\"RoleDefinitionBindings\":[{\"RoleTypeKind\":3}],"
                            + "\"Member\":{\"Id\":\"12\",\"Title\":\"Carol\","
                            + "\"LoginName\":\"i:0#.f|membership|carol@example.com\",\"PrincipalType\":1}}]}");
            server.onPathQuery(ITEM_PATH + "/RoleAssignments", "%24skip=200&%24top=200" + EXPAND_QUERY, JSON, "{\"value\":[]}");
            server.start();

            final GetListItemRoleResponse response = executeAgainst(server);

            assertEquals("the expanded member must still be resolved", 1, response.getUsers().size());
            assertEquals("and it must be the expanded principal", "Carol", response.getUsers().get(0).getTitle());
            assertEquals("the member endpoint must not be called at all", 0,
                    server.getRecordedRequests().stream().filter(request -> request.getPath().contains("/Member")).count());
        }
    }

    @Test
    public void test_memberFallsBackToPerPrincipalFetchWhenNotExpanded() throws Exception {
        // A server that does not expand Member inline - an older server, or one that simply
        // leaves the property out - must still be resolved, via the per-principal request the
        // expansion above replaces when it is honored.
        try (SharePointMockServer server = new SharePointMockServer()) {
            server.onPathQuery(ITEM_PATH + "/RoleAssignments", MEMBER_PAGING_QUERY + EXPAND_QUERY, JSON,
                    "{\"value\":[{\"PrincipalId\":12,\"RoleDefinitionBindings\":[{\"RoleTypeKind\":3}]}]}");
            server.onPathQuery(ITEM_PATH + "/RoleAssignments", "%24skip=200&%24top=200" + EXPAND_QUERY, JSON, "{\"value\":[]}");
            server.onPathStatus(ITEM_PATH + "/RoleAssignments/GetByPrincipalId(12)/Member", 200, JSON,
                    "{\"Id\":\"12\",\"Title\":\"Carol\",\"LoginName\":\"i:0#.f|membership|carol@example.com\",\"PrincipalType\":1}");
            server.start();

            final GetListItemRoleResponse response = executeAgainst(server);

            assertEquals("the fallback fetch must still resolve the principal", 1, response.getUsers().size());
            assertEquals("and it must be Carol", "Carol", response.getUsers().get(0).getTitle());
            assertEquals("exactly one fallback request must be made", 1,
                    server.getRecordedRequests().stream().filter(request -> request.getPath().contains("/Member")).count());
        }
    }

    @Test
    @Timeout(value = 60, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_roleAssignmentListingStopsWhenTheServerIgnoresSkip() throws Exception {
        // A stub registered by path answers every query identically, so this mock ignores $skip
        // exactly the way a broken SharePoint server does. Only the page bound can end the
        // listing; without one this call never returns, which is what the timeout above turns
        // into a failure instead of a hung suite.
        try (SharePointMockServer server = new SharePointMockServer()) {
            // One group repeated across the page: the group cache answers every repeat after the
            // first, so the listing costs one member lookup rather than one per assignment.
            server.onPathStatus(ITEM_PATH + "/RoleAssignments", 200, JSON, roleAssignmentPage("7", 3, PAGE_SIZE));
            server.onPathStatus(ITEM_PATH + "/RoleAssignments/GetByPrincipalId(7)/Member", 200, JSON,
                    "{\"Id\":\"7\",\"Title\":\"Marketing\",\"LoginName\":\"\",\"PrincipalType\":8}");
            server.onPathStatus(usersPath("7"), 200, JSON, aliceMember("9"));
            server.start();

            executeAgainst(server);

            assertEquals("the listing must stop after its page bound", MAX_PAGES,
                    server.getRecordedRequests()
                            .stream()
                            .filter(request -> (ITEM_PATH + "/RoleAssignments").equals(request.getPath()))
                            .count());
        }
    }

    @Test
    public void test_groupMembersAreReadPageByPage() throws Exception {
        // A group with more members than one server page must yield all of them. The members
        // used to be fetched with a single unpaged request.
        try (SharePointMockServer server = new SharePointMockServer()) {
            stubSingleGroupAssignment(server, "7", "Big");
            // The first page answers any query, so an unpaged request gets it too and the
            // assertion below reports the members that were lost rather than a missing stub.
            server.onPathStatus(usersPath("7"), 200, JSON, userPage(0, PAGE_SIZE));
            server.onPathQuery(usersPath("7"), "%24skip=200&%24top=200", JSON, userPage(PAGE_SIZE, 1));
            server.start();

            final GetListItemRoleResponse.SharePointGroup group = executeAgainst(server).getSharePointGroups().get(0);

            assertEquals("every member of both pages must be read", PAGE_SIZE + 1, group.getUsers().size());
            assertTrue("the members request must carry paging parameters", server.getRecordedRequests()
                    .stream()
                    .anyMatch(request -> usersPath("7").equals(request.getPath()) && "%24skip=200&%24top=200".equals(request.getQuery())));
        }
    }

    @Test
    public void test_aFailedMemberFetchLeavesNothingCached() throws Exception {
        // The members endpoint fails once and then works. The crawler retries a failed crawl
        // with the same group cache, so the failed attempt must not leave the half-built group
        // behind - the retry would be handed an empty group and index the item with none of
        // that group's permissions.
        try (SharePointMockServer server = new SharePointMockServer()) {
            stubSingleGroupAssignment(server, "7", "Marketing");
            server.onPathOnce(usersPath("7"), 500, JSON, "{\"error\":{\"message\":\"transient\"}}");
            server.onPathStatus(usersPath("7"), 200, JSON, aliceMember("9"));
            server.start();

            final Map<String, GetListItemRoleResponse.SharePointGroup> cache = new HashMap<>();
            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                assertMemberFetchFails(httpClient, server, cache);

                final GetListItemRoleResponse.SharePointGroup group =
                        executeWithCache(httpClient, server, cache).getSharePointGroups().get(0);
                assertEquals("the retry must read the group's members instead of reusing a failed attempt", 1, group.getUsers().size());
            }
        }
    }

    @Test
    public void test_aFailedNestedMemberFetchLeavesNothingCached() throws Exception {
        // Same thing one level down: the nested group is cached under its own key before its
        // members are read, so a failure there must clear that key too.
        try (SharePointMockServer server = new SharePointMockServer()) {
            stubSingleGroupAssignment(server, "7", "A");
            server.onPathStatus(usersPath("7"), 200, JSON,
                    "{\"value\":[{\"Id\":\"8\",\"Title\":\"B\",\"LoginName\":\"\",\"PrincipalType\":8}]}");
            server.onPathOnce(usersPath("8"), 500, JSON, "{\"error\":{\"message\":\"transient\"}}");
            server.onPathStatus(usersPath("8"), 200, JSON, aliceMember("9"));
            server.start();

            final Map<String, GetListItemRoleResponse.SharePointGroup> cache = new HashMap<>();
            try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
                assertMemberFetchFails(httpClient, server, cache);

                final GetListItemRoleResponse.SharePointGroup outer =
                        executeWithCache(httpClient, server, cache).getSharePointGroups().get(0);
                assertEquals("the retry must rebuild the outer group and reattach the nested one", 1, outer.getSharePointGroups().size());
                assertEquals("the retry must read the nested group's members too", 1, outer.getSharePointGroups().get(0).getUsers().size());
            }
        }
    }

    /**
     * A group reaches the shared cache <em>before</em> its members are read - deliberately, because
     * that is what breaks a membership cycle. With more than one crawl thread that publication is
     * visible to the other threads, and a thread that took the still-empty group for a finished one
     * would index every item that group protects with none of its permissions.
     *
     * <p>The map below counts down the moment the group is published, so the second thread starts
     * exactly inside the window the fix has to close; the members endpoint is slow, so the window
     * is a second wide rather than an instant. Against the unfixed code the second thread reads the
     * empty group and this test fails on the very first assertion about it.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_twoThreadsResolvingTheSameGroupBothSeeACompleteGroup() throws Exception {
        try (SharePointMockServer server = new SharePointMockServer()) {
            stubSingleGroupAssignment(server, "7", "Marketing");
            server.onPathStatus(usersPath("7"), 200, JSON,
                    "{\"value\":[{\"Id\":\"9\",\"Title\":\"Alice\",\"LoginName\":\"i:0#.f|membership|alice@example.com\","
                            + "\"PrincipalType\":1},"
                            + "{\"Id\":\"10\",\"Title\":\"EXAMPLE\\\\engineers\",\"LoginName\":\"EXAMPLE\\\\engineers\","
                            + "\"PrincipalType\":4}]}");
            server.withDelay(usersPath("7"), 1000L);
            server.start();

            final CountDownLatch published = new CountDownLatch(1);
            final Map<String, GetListItemRoleResponse.SharePointGroup> cache =
                    new ConcurrentHashMap<String, GetListItemRoleResponse.SharePointGroup>() {
                        private static final long serialVersionUID = 1L;

                        @Override
                        public GetListItemRoleResponse.SharePointGroup put(final String key,
                                final GetListItemRoleResponse.SharePointGroup value) {
                            final GetListItemRoleResponse.SharePointGroup previous = super.put(key, value);
                            published.countDown();
                            return previous;
                        }
                    };

            // 4 connections, so neither thread is held up waiting for the other's slow request.
            try (CloseableHttpClient httpClient = HttpClientBuilder.create().setMaxConnPerRoute(4).setMaxConnTotal(4).build()) {
                final AtomicReference<GetListItemRoleResponse> firstResponse = new AtomicReference<>();
                final AtomicReference<Throwable> firstFailure = new AtomicReference<>();
                final Thread first = new Thread(() -> {
                    try {
                        firstResponse.set(executeWithCache(httpClient, server, cache));
                    } catch (final Throwable t) {
                        firstFailure.set(t);
                    }
                });
                first.start();
                try {
                    assertTrue("the first thread must have published its still-empty group", published.await(30, TimeUnit.SECONDS));

                    final GetListItemRoleResponse.SharePointGroup second =
                            executeWithCache(httpClient, server, cache).getSharePointGroups().get(0);

                    assertEquals("the second thread must not be handed a group whose members are still being read", 1,
                            second.getUsers().size());
                    assertEquals("and its security groups must be complete too", 1, second.getSecurityGroups().size());
                } finally {
                    first.join(30_000L);
                }
                assertNull("the first thread must not have failed", firstFailure.get());
                final GetListItemRoleResponse.SharePointGroup firstGroup = firstResponse.get().getSharePointGroups().get(0);
                assertEquals("the thread that built the group must see it complete as well", 1, firstGroup.getUsers().size());
                assertEquals("including its security groups", 1, firstGroup.getSecurityGroups().size());
            }
        }
    }

    // === Helper methods ===

    private static final String JSON = "application/json";

    private static final int PAGE_SIZE = 200;

    /** How many pages one listing is allowed to fetch. */
    private static final int MAX_PAGES = 100;

    private static final String MEMBER_PAGING_QUERY = "%24skip=0&%24top=200";

    /** The $expand suffix GetListItemRole appends to every role assignment listing request. */
    private static final String EXPAND_QUERY = "&%24expand=RoleDefinitionBindings,Member";

    private static final String LIST_ID = "11111111-1111-1111-1111-111111111111";

    private static final String ITEM_PATH = "/sites/test/_api/Web/Lists(guid'" + LIST_ID + "')/Items(1)";

    private static String usersPath(final String groupId) {
        return "/sites/test/_api/Web/SiteGroups/GetById(" + groupId + ")/Users";
    }

    /**
     * Stubs one role assignment naming a SharePoint group, plus the empty second page that ends
     * the role assignment loop.
     */
    private static void stubSingleGroupAssignment(final SharePointMockServer server, final String principalId, final String title) {
        server.onPathQuery(ITEM_PATH + "/RoleAssignments", MEMBER_PAGING_QUERY + EXPAND_QUERY, JSON,
                "{\"value\":[{\"PrincipalId\":" + principalId + ",\"RoleDefinitionBindings\":[{\"RoleTypeKind\":3}]}]}");
        server.onPathQuery(ITEM_PATH + "/RoleAssignments", "%24skip=200&%24top=200" + EXPAND_QUERY, JSON, "{\"value\":[]}");
        server.onPathStatus(ITEM_PATH + "/RoleAssignments/GetByPrincipalId(" + principalId + ")/Member", 200, JSON,
                "{\"Id\":\"" + principalId + "\",\"Title\":\"" + title + "\",\"LoginName\":\"\",\"PrincipalType\":8}");
    }

    /**
     * A page of role assignments naming one principal {@code count} times.
     *
     * @param principalId the principal every entry names
     * @param roleTypeKind the bound role type; 1 is Limited Access, which the filter drops
     * @param count how many entries the page holds
     * @return the JSON body
     */
    private static String roleAssignmentPage(final String principalId, final int roleTypeKind, final int count) {
        final StringBuilder buf = new StringBuilder("{\"value\":[");
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                buf.append(',');
            }
            buf.append("{\"PrincipalId\":")
                    .append(principalId)
                    .append(",\"RoleDefinitionBindings\":[{\"RoleTypeKind\":")
                    .append(roleTypeKind)
                    .append("}]}");
        }
        return buf.append("]}").toString();
    }

    private static String userPage(final int firstId, final int count) {
        final StringBuilder buf = new StringBuilder("{\"value\":[");
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                buf.append(',');
            }
            final int id = firstId + i;
            buf.append("{\"Id\":\"")
                    .append(id)
                    .append("\",\"Title\":\"User ")
                    .append(id)
                    .append("\",\"LoginName\":\"i:0#.f|membership|u")
                    .append(id)
                    .append("@example.com\",\"PrincipalType\":1}");
        }
        return buf.append("]}").toString();
    }

    private static GetListItemRoleResponse executeAgainst(final SharePointMockServer server) throws Exception {
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            return executeWithCache(httpClient, server, new HashMap<>());
        }
    }

    private static GetListItemRoleResponse executeWithCache(final CloseableHttpClient httpClient, final SharePointMockServer server,
            final Map<String, GetListItemRoleResponse.SharePointGroup> cache) {
        return new GetListItemRole(httpClient, server.getBaseUrl() + "sites/test", null).setId(LIST_ID, "1")
                .setSharePointGroupCache(cache)
                .execute();
    }

    /** Runs one attempt that is expected to hit the queued server error, standing in for a crawl the crawler will retry. */
    private void assertMemberFetchFails(final CloseableHttpClient httpClient, final SharePointMockServer server,
            final Map<String, GetListItemRoleResponse.SharePointGroup> cache) {
        try {
            executeWithCache(httpClient, server, cache);
            fail("the attempt that hits the failing members endpoint must propagate the error");
        } catch (final SharePointServerException e) {
            // expected: this is what SharePointCrawler catches and retries
        }
    }

    private static String aliceMember(final String id) {
        return "{\"value\":[{\"Id\":\"" + id + "\",\"Title\":\"Alice\",\"LoginName\":\"i:0#.f|membership|alice@example.com\","
                + "\"PrincipalType\":1}]}";
    }

    private Map<String, Object> createRoleAssignment(final int... roleTypeKinds) {
        final Map<String, Object> roleAssignment = new HashMap<>();
        final List<Map<String, Object>> bindings = new ArrayList<>();
        for (final int roleTypeKind : roleTypeKinds) {
            final Map<String, Object> binding = new HashMap<>();
            binding.put("RoleTypeKind", roleTypeKind);
            bindings.add(binding);
        }
        roleAssignment.put("RoleDefinitionBindings", bindings);
        return roleAssignment;
    }
}
