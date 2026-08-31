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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.codelibs.fess.ds.sharepoint.client.SharePointClient;
import org.codelibs.fess.ds.sharepoint.client.api.SharePointApis;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemRole;
import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitem.GetListItemRoleResponse;
import org.codelibs.fess.helper.SystemHelper;
import org.codelibs.fess.opensearch.config.exentity.DataConfig;
import org.codelibs.fess.util.ComponentUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;

/**
 * Exercises {@link SharePointCrawl#getItemRoles} against a canned role assignment response, so the
 * mapping from a SharePoint principal to a Fess role string is verified without a server.
 */
public class SharePointCrawlRoleTest extends UnitDsTestCase {

    private static final String AZURE_LOGIN_NAME = "i:0#.f|membership|foo@contoso.com";

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    @Override
    public void setUp(final TestInfo testInfo) throws Exception {
        super.setUp(testInfo);
        ComponentUtil.register(new SystemHelper(), "systemHelper");
    }

    private List<String> getItemRoles(final GetListItemRoleResponse response) {
        final SharePointCrawl crawl = new SharePointCrawl(new StubClient(response)) {
            @Override
            public Map<String, Object> doCrawl(final DataConfig dataConfig, final Queue<SharePointCrawl> crawlingQueue) {
                throw new UnsupportedOperationException("not used by this test");
            }
        };
        return crawl.getItemRoles("list-id", "1", new HashMap<>(), false);
    }

    private static GetListItemRoleResponse.User azureUser() {
        return new GetListItemRoleResponse.User("11", "Foo", AZURE_LOGIN_NAME);
    }

    @Test
    public void test_azureUserAssignedDirectlyGetsTheSameRoleAsViaAGroup() {
        // A user whose login name is "i:0#.f|membership|foo@contoso.com" must map to the same
        // role string whether the permission is assigned to them directly or through a
        // SharePoint group. The direct path used to strip only 7 characters and leave
        // "membership|foo@contoso.com" behind.
        final GetListItemRoleResponse direct = new GetListItemRoleResponse();
        direct.addUser(azureUser());
        final Set<String> directRoles = new HashSet<>(getItemRoles(direct));

        assertTrue("the directly assigned Azure user must produce the plain account role", directRoles.contains("1foo@contoso.com"));
        assertFalse("the directly assigned Azure user must not keep the membership| prefix",
                directRoles.contains("1membership|foo@contoso.com"));

        final GetListItemRoleResponse viaGroup = new GetListItemRoleResponse();
        final GetListItemRoleResponse.SharePointGroup group = new GetListItemRoleResponse.SharePointGroup("7", "Team");
        group.addUser(azureUser());
        viaGroup.addSharePointGroup(group);

        assertEquals("the same user must get the same roles on both paths", directRoles, new HashSet<>(getItemRoles(viaGroup)));
    }

    @Test
    public void test_adUserKeepsTheOnPremisesMapping() {
        // The on-premises path is unchanged: only accounts carrying a domain are mapped, and the
        // domain is dropped because that is the name the login form produces.
        final GetListItemRoleResponse response = new GetListItemRoleResponse();
        response.addUser(new GetListItemRoleResponse.User("12", "Bar", "i:0#.w|CONTOSO\\bar"));
        response.addUser(new GetListItemRoleResponse.User("13", "Local", "i:0#.w|local"));

        final Set<String> roles = new HashSet<>(getItemRoles(response));

        assertTrue("a domain-qualified account must be indexed without its domain", roles.contains("1bar"));
        assertFalse("an account with no domain must not be indexed", roles.contains("1local"));
    }

    @Test
    @Timeout(value = 30, threadMode = ThreadMode.SEPARATE_THREAD)
    public void test_cyclicSharePointGroupMembershipTerminates() {
        // Group A contains group B, group B contains group A. Expanding the permission must
        // return rather than recurse until the stack runs out.
        final GetListItemRoleResponse.SharePointGroup groupA = new GetListItemRoleResponse.SharePointGroup("1", "A");
        final GetListItemRoleResponse.SharePointGroup groupB = new GetListItemRoleResponse.SharePointGroup("2", "B");
        groupA.addSharePointGroup(groupB);
        groupB.addSharePointGroup(groupA);
        groupB.addUser(azureUser());

        final GetListItemRoleResponse response = new GetListItemRoleResponse();
        response.addSharePointGroup(groupA);

        final Set<String> roles = new HashSet<>(getItemRoles(response));

        assertTrue("the member of the nested group must still be indexed", roles.contains("1foo@contoso.com"));
    }

    /** A {@link SharePointClient} whose role API returns a canned response instead of calling out. */
    private static class StubClient extends SharePointClient {
        private final SharePointApis apis;

        StubClient(final GetListItemRoleResponse response) {
            super(null, "http://localhost/", "test", null, false);
            apis = new SharePointApis(null, "http://localhost/sites/test/", null) {
                @Override
                public ListApis list() {
                    return new ListApis() {
                        @Override
                        public GetListItemRole getListItemRole() {
                            return new GetListItemRole(null, siteUrl, null) {
                                @Override
                                public GetListItemRoleResponse execute() {
                                    return response;
                                }
                            };
                        }
                    };
                }
            };
        }

        @Override
        public SharePointApis api() {
            return apis;
        }
    }
}
