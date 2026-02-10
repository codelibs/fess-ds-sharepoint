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

import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.codelibs.fess.util.ComponentUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

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

    // === Helper methods ===

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
