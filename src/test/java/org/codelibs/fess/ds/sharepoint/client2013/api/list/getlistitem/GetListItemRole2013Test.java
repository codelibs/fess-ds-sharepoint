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

import java.util.List;

import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApi;
import org.codelibs.fess.util.ComponentUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

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
