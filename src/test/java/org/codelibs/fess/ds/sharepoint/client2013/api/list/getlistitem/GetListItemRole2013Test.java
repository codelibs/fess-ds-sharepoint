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
package org.codelibs.fess.ds.sharepoint.client2013.api.list.getlistitem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.ds.sharepoint.util.SharePointMockServer;
import org.codelibs.fess.util.ComponentUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;

public class GetListItemRole2013Test extends UnitDsTestCase {

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
    }

    @Override
    public void tearDown(TestInfo testInfo) throws Exception {
        ComponentUtil.setFessConfig(null);
        super.tearDown(testInfo);
    }

    // === execute() tests ===

    @Test
    public void test_securityGroupAssignedDirectlyIsReturned() throws Exception {
        // PrincipalType 4 is an AD security group. Assigned directly on the item it used to be
        // dropped, so an item whose only permission was an AD group ended up with no role at all.
        try (SharePointMockServer server = new SharePointMockServer()) {
            stubRoleAssignment(server, "10");
            server.onPathStatus(memberPath("10"), 200, XML, member("10", "DOMAIN\\Sales", 4, "DOMAIN\\Sales"));
            server.start();

            final GetListItemRole2013Response response = executeAgainst(server, new HashMap<>());

            assertEquals("the directly assigned security group must be returned", 1, response.getSecurityGroups().size());
            assertEquals("security group title", "DOMAIN\\Sales", response.getSecurityGroups().get(0).getTitle());
        }
    }

    @Test
    public void test_nestedGroupKeepsItsOwnTitle() throws Exception {
        // A group nested inside another must be named after itself, not after its parent.
        try (SharePointMockServer server = new SharePointMockServer()) {
            stubRoleAssignment(server, "7");
            server.onPathStatus(memberPath("7"), 200, XML, member("7", "Marketing", 8, ""));
            server.onPathStatus(usersPath("7"), 200, XML, usersFeedOneEntry("8", "Engineering", 8, ""));
            server.onPathStatus(usersPath("8"), 200, XML, usersFeedOneEntry("9", "Alice", 1, "i:0#.f|membership|alice@example.com"));
            server.start();

            final GetListItemRole2013Response.SharePointGroup parent = executeAgainst(server, new HashMap<>()).getSharePointGroups().get(0);

            assertEquals("the outer group must keep its own title", "Marketing", parent.getTitle());
            assertEquals("the outer group must hold exactly the nested group", 1, parent.getSharePointGroups().size());
            final GetListItemRole2013Response.SharePointGroup nested = parent.getSharePointGroups().get(0);
            assertEquals("the nested group must carry its own title", "Engineering", nested.getTitle());
            assertEquals("the nested group's member must be read from the nested group", 1, nested.getUsers().size());
        }
    }

    @Test
    @Timeout(value = 30, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_cyclicGroupMembershipTerminates() throws Exception {
        // Group A contains group B, group B contains group A. This must return rather than
        // recurse until the stack runs out. The crawl layer's own visited set does not catch
        // this: GetListItemRole2013 blows its own stack inside this recursion before the crawl
        // layer ever sees the object graph, so the cut has to happen in the group cache itself.
        try (SharePointMockServer server = new SharePointMockServer()) {
            stubRoleAssignment(server, "7");
            server.onPathStatus(memberPath("7"), 200, XML, member("7", "A", 8, ""));
            server.onPathStatus(usersPath("7"), 200, XML, usersFeedOneEntry("8", "B", 8, ""));
            server.onPathStatus(usersPath("8"), 200, XML, usersFeedOneEntry("7", "A", 8, ""));
            server.start();

            final GetListItemRole2013Response.SharePointGroup groupA = executeAgainst(server, new HashMap<>()).getSharePointGroups().get(0);

            assertEquals("the outer group must be A", "A", groupA.getTitle());
            final GetListItemRole2013Response.SharePointGroup groupB = groupA.getSharePointGroups().get(0);
            assertEquals("A must contain B", "B", groupB.getTitle());
            assertSame("B must point back at the very same A rather than a fresh expansion of it", groupA,
                    groupB.getSharePointGroups().get(0));
        }
    }

    // === RoleDefinitionBindingsDocHandler tests ===

    @Test
    public void test_roleDefinitionBindingsDocHandler_limitedAccessOnly() {
        final String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:d=\"http://schemas.microsoft.com/ado/2007/08/dataservices\">"
                + "<entry><content><m:properties xmlns:m=\"http://schemas.microsoft.com/ado/2007/08/dataservices/metadata\">"
                + "<d:RoleTypeKind>1</d:RoleTypeKind>" + "</m:properties></content></entry>" + "</feed>";

        final GetListItemRole2013.RoleDefinitionBindingsDocHandler handler = new GetListItemRole2013.RoleDefinitionBindingsDocHandler();
        SharePointApi.XmlResponse.parseXml(xml, handler);

        final List<Integer> roleTypeKinds = handler.getRoleTypeKinds();
        assertEquals(1, roleTypeKinds.size());
        assertEquals(Integer.valueOf(1), roleTypeKinds.get(0));
    }

    @Test
    public void test_roleDefinitionBindingsDocHandler_readerRole() {
        final String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:d=\"http://schemas.microsoft.com/ado/2007/08/dataservices\">"
                + "<entry><content><m:properties xmlns:m=\"http://schemas.microsoft.com/ado/2007/08/dataservices/metadata\">"
                + "<d:RoleTypeKind>2</d:RoleTypeKind>" + "</m:properties></content></entry>" + "</feed>";

        final GetListItemRole2013.RoleDefinitionBindingsDocHandler handler = new GetListItemRole2013.RoleDefinitionBindingsDocHandler();
        SharePointApi.XmlResponse.parseXml(xml, handler);

        final List<Integer> roleTypeKinds = handler.getRoleTypeKinds();
        assertEquals(1, roleTypeKinds.size());
        assertEquals(Integer.valueOf(2), roleTypeKinds.get(0));
    }

    @Test
    public void test_roleDefinitionBindingsDocHandler_contributorRole() {
        final String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:d=\"http://schemas.microsoft.com/ado/2007/08/dataservices\">"
                + "<entry><content><m:properties xmlns:m=\"http://schemas.microsoft.com/ado/2007/08/dataservices/metadata\">"
                + "<d:RoleTypeKind>3</d:RoleTypeKind>" + "</m:properties></content></entry>" + "</feed>";

        final GetListItemRole2013.RoleDefinitionBindingsDocHandler handler = new GetListItemRole2013.RoleDefinitionBindingsDocHandler();
        SharePointApi.XmlResponse.parseXml(xml, handler);

        final List<Integer> roleTypeKinds = handler.getRoleTypeKinds();
        assertEquals(1, roleTypeKinds.size());
        assertEquals(Integer.valueOf(3), roleTypeKinds.get(0));
    }

    @Test
    public void test_roleDefinitionBindingsDocHandler_administratorRole() {
        final String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:d=\"http://schemas.microsoft.com/ado/2007/08/dataservices\">"
                + "<entry><content><m:properties xmlns:m=\"http://schemas.microsoft.com/ado/2007/08/dataservices/metadata\">"
                + "<d:RoleTypeKind>5</d:RoleTypeKind>" + "</m:properties></content></entry>" + "</feed>";

        final GetListItemRole2013.RoleDefinitionBindingsDocHandler handler = new GetListItemRole2013.RoleDefinitionBindingsDocHandler();
        SharePointApi.XmlResponse.parseXml(xml, handler);

        final List<Integer> roleTypeKinds = handler.getRoleTypeKinds();
        assertEquals(1, roleTypeKinds.size());
        assertEquals(Integer.valueOf(5), roleTypeKinds.get(0));
    }

    @Test
    public void test_roleDefinitionBindingsDocHandler_multipleRoles() {
        final String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:d=\"http://schemas.microsoft.com/ado/2007/08/dataservices\">"
                + "<entry><content><m:properties xmlns:m=\"http://schemas.microsoft.com/ado/2007/08/dataservices/metadata\">"
                + "<d:RoleTypeKind>1</d:RoleTypeKind>" + "</m:properties></content></entry>"
                + "<entry><content><m:properties xmlns:m=\"http://schemas.microsoft.com/ado/2007/08/dataservices/metadata\">"
                + "<d:RoleTypeKind>3</d:RoleTypeKind>" + "</m:properties></content></entry>" + "</feed>";

        final GetListItemRole2013.RoleDefinitionBindingsDocHandler handler = new GetListItemRole2013.RoleDefinitionBindingsDocHandler();
        SharePointApi.XmlResponse.parseXml(xml, handler);

        final List<Integer> roleTypeKinds = handler.getRoleTypeKinds();
        assertEquals(2, roleTypeKinds.size());
        assertEquals(Integer.valueOf(1), roleTypeKinds.get(0));
        assertEquals(Integer.valueOf(3), roleTypeKinds.get(1));
    }

    @Test
    public void test_roleDefinitionBindingsDocHandler_emptyResponse() {
        final String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:d=\"http://schemas.microsoft.com/ado/2007/08/dataservices\">"
                + "</feed>";

        final GetListItemRole2013.RoleDefinitionBindingsDocHandler handler = new GetListItemRole2013.RoleDefinitionBindingsDocHandler();
        SharePointApi.XmlResponse.parseXml(xml, handler);

        final List<Integer> roleTypeKinds = handler.getRoleTypeKinds();
        assertEquals(0, roleTypeKinds.size());
    }

    @Test
    public void test_roleDefinitionBindingsDocHandler_noneRole() {
        final String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:d=\"http://schemas.microsoft.com/ado/2007/08/dataservices\">"
                + "<entry><content><m:properties xmlns:m=\"http://schemas.microsoft.com/ado/2007/08/dataservices/metadata\">"
                + "<d:RoleTypeKind>0</d:RoleTypeKind>" + "</m:properties></content></entry>" + "</feed>";

        final GetListItemRole2013.RoleDefinitionBindingsDocHandler handler = new GetListItemRole2013.RoleDefinitionBindingsDocHandler();
        SharePointApi.XmlResponse.parseXml(xml, handler);

        final List<Integer> roleTypeKinds = handler.getRoleTypeKinds();
        assertEquals(1, roleTypeKinds.size());
        assertEquals(Integer.valueOf(0), roleTypeKinds.get(0));
    }

    // === Filtering logic tests using parsed handler results ===

    @Test
    public void test_filteringLogic_limitedAccessOnly_shouldFilter() {
        // Simulates the filtering logic: all RoleTypeKind <= 1 -> should be filtered
        final GetListItemRole2013.RoleDefinitionBindingsDocHandler handler = parseRoleDefinitionBindingsXml(1);
        assertTrue(shouldFilter(handler.getRoleTypeKinds()));
    }

    @Test
    public void test_filteringLogic_noneOnly_shouldFilter() {
        // RoleTypeKind=0 (None) -> should be filtered
        final GetListItemRole2013.RoleDefinitionBindingsDocHandler handler = parseRoleDefinitionBindingsXml(0);
        assertTrue(shouldFilter(handler.getRoleTypeKinds()));
    }

    @Test
    public void test_filteringLogic_noneAndLimitedAccess_shouldFilter() {
        // RoleTypeKind 0 and 1 -> should be filtered
        final GetListItemRole2013.RoleDefinitionBindingsDocHandler handler = parseRoleDefinitionBindingsXml(0, 1);
        assertTrue(shouldFilter(handler.getRoleTypeKinds()));
    }

    @Test
    public void test_filteringLogic_readerRole_shouldNotFilter() {
        // RoleTypeKind=2 (Reader) -> should NOT be filtered
        final GetListItemRole2013.RoleDefinitionBindingsDocHandler handler = parseRoleDefinitionBindingsXml(2);
        assertFalse(shouldFilter(handler.getRoleTypeKinds()));
    }

    @Test
    public void test_filteringLogic_contributorRole_shouldNotFilter() {
        // RoleTypeKind=3 (Contributor) -> should NOT be filtered
        final GetListItemRole2013.RoleDefinitionBindingsDocHandler handler = parseRoleDefinitionBindingsXml(3);
        assertFalse(shouldFilter(handler.getRoleTypeKinds()));
    }

    @Test
    public void test_filteringLogic_administratorRole_shouldNotFilter() {
        // RoleTypeKind=5 (Administrator) -> should NOT be filtered
        final GetListItemRole2013.RoleDefinitionBindingsDocHandler handler = parseRoleDefinitionBindingsXml(5);
        assertFalse(shouldFilter(handler.getRoleTypeKinds()));
    }

    @Test
    public void test_filteringLogic_mixedLimitedAccessAndContributor_shouldNotFilter() {
        // RoleTypeKind 1 (Limited Access) + 3 (Contributor) -> should NOT be filtered
        final GetListItemRole2013.RoleDefinitionBindingsDocHandler handler = parseRoleDefinitionBindingsXml(1, 3);
        assertFalse(shouldFilter(handler.getRoleTypeKinds()));
    }

    @Test
    public void test_filteringLogic_emptyBindings_shouldFilter() {
        // No bindings -> should be filtered
        final GetListItemRole2013.RoleDefinitionBindingsDocHandler handler = parseRoleDefinitionBindingsXml();
        assertTrue(shouldFilter(handler.getRoleTypeKinds()));
    }

    // === buildRoleDefinitionBindingsUrl test ===

    @Test
    public void test_buildRoleDefinitionBindingsUrl() {
        final GetListItemRole2013 api = new GetListItemRole2013(null, "https://example.sharepoint.com/sites/test/", null);
        api.setId("list-guid-123", "42");
        final String url = api.buildRoleDefinitionBindingsUrl("10");
        assertEquals(
                "https://example.sharepoint.com/sites/test/_api/Web/Lists(guid'list-guid-123')/Items(42)/RoleAssignments/GetByPrincipalId(10)/RoleDefinitionBindings",
                url);
    }

    // === Helper methods ===

    private static final String XML = "application/xml";

    private static final String LIST_ID = "11111111-1111-1111-1111-111111111111";

    private static final String ITEM_ID = "1";

    private static String basePath() {
        return "/sites/test/_api/Web/Lists(guid'" + LIST_ID + "')/";
    }

    private static String roleAssignmentsPath() {
        return basePath() + "Items(" + ITEM_ID + ")/RoleAssignments";
    }

    private static String roleDefinitionBindingsPath(final String principalId) {
        return basePath() + "Items(" + ITEM_ID + ")/RoleAssignments/GetByPrincipalId(" + principalId + ")/RoleDefinitionBindings";
    }

    private static String memberPath(final String principalId) {
        return basePath() + "RoleAssignments/GetByPrincipalId(" + principalId + ")/Member";
    }

    private static String usersPath(final String groupId) {
        return "/sites/test/_api/Web/SiteGroups/GetById(" + groupId + ")/Users";
    }

    /**
     * Stubs one role assignment naming the given principal, plus a RoleDefinitionBindings
     * response that keeps it out of the limited-access filter (RoleTypeKind 3, Contributor).
     */
    private static void stubRoleAssignment(final SharePointMockServer server, final String principalId) {
        server.onPathStatus(roleAssignmentsPath(), 200, XML, roleAssignmentsFeed(principalId));
        server.onPathStatus(roleDefinitionBindingsPath(principalId), 200, XML, roleTypeKindFeed(3));
    }

    private static String roleAssignmentsFeed(final String principalId) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:d=\"http://schemas.microsoft.com/ado/2007/08/dataservices\">"
                + "<entry><content><m:properties xmlns:m=\"http://schemas.microsoft.com/ado/2007/08/dataservices/metadata\">"
                + "<d:PrincipalId>" + principalId + "</d:PrincipalId>" + "</m:properties></content></entry></feed>";
    }

    private static String roleTypeKindFeed(final int roleTypeKind) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:d=\"http://schemas.microsoft.com/ado/2007/08/dataservices\">"
                + "<entry><content><m:properties xmlns:m=\"http://schemas.microsoft.com/ado/2007/08/dataservices/metadata\">"
                + "<d:RoleTypeKind>" + roleTypeKind + "</d:RoleTypeKind>" + "</m:properties></content></entry></feed>";
    }

    private static String member(final String id, final String title, final int principalType, final String loginName) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<entry xmlns=\"http://www.w3.org/2005/Atom\" xmlns:d=\"http://schemas.microsoft.com/ado/2007/08/dataservices\">"
                + "<content><m:properties xmlns:m=\"http://schemas.microsoft.com/ado/2007/08/dataservices/metadata\">" + "<d:Id>" + id
                + "</d:Id><d:Title>" + title + "</d:Title>" + "<d:PrincipalType>" + principalType + "</d:PrincipalType>" + "<d:LoginName>"
                + loginName + "</d:LoginName>" + "</m:properties></content></entry>";
    }

    private static String usersFeedOneEntry(final String id, final String title, final int principalType, final String loginName) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:d=\"http://schemas.microsoft.com/ado/2007/08/dataservices\">"
                + "<entry><content><m:properties xmlns:m=\"http://schemas.microsoft.com/ado/2007/08/dataservices/metadata\">" + "<d:Id>"
                + id + "</d:Id><d:Title>" + title + "</d:Title>" + "<d:PrincipalType>" + principalType + "</d:PrincipalType>"
                + "<d:LoginName>" + loginName + "</d:LoginName>" + "</m:properties></content></entry></feed>";
    }

    private static GetListItemRole2013Response executeAgainst(final SharePointMockServer server,
            final Map<String, GetListItemRole2013Response.SharePointGroup> cache) throws Exception {
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            return new GetListItemRole2013(httpClient, server.getBaseUrl() + "sites/test/", null).setId(LIST_ID, ITEM_ID)
                    .setSharePointGroupCache(cache)
                    .execute();
        }
    }

    private GetListItemRole2013.RoleDefinitionBindingsDocHandler parseRoleDefinitionBindingsXml(final int... roleTypeKinds) {
        final StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        xml.append("<feed xmlns=\"http://www.w3.org/2005/Atom\" xmlns:d=\"http://schemas.microsoft.com/ado/2007/08/dataservices\">");
        for (final int kind : roleTypeKinds) {
            xml.append("<entry><content><m:properties xmlns:m=\"http://schemas.microsoft.com/ado/2007/08/dataservices/metadata\">");
            xml.append("<d:RoleTypeKind>").append(kind).append("</d:RoleTypeKind>");
            xml.append("</m:properties></content></entry>");
        }
        xml.append("</feed>");

        final GetListItemRole2013.RoleDefinitionBindingsDocHandler handler = new GetListItemRole2013.RoleDefinitionBindingsDocHandler();
        SharePointApi.XmlResponse.parseXml(xml.toString(), handler);
        return handler;
    }

    /**
     * Reproduces the same filtering logic as isLimitedAccessOnly in GetListItemRole2013.
     * Returns true if the principal should be filtered out (Limited Access only).
     */
    private boolean shouldFilter(final List<Integer> roleTypeKinds) {
        if (roleTypeKinds.isEmpty()) {
            return true;
        }
        for (final int roleTypeKind : roleTypeKinds) {
            if (roleTypeKind > 1) {
                return false;
            }
        }
        return true;
    }
}
