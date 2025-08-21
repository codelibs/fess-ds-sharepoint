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
package org.codelibs.fess.ds.sharepoint.client2013.api.list.getlists;

import java.util.List;

import org.codelibs.fess.ds.sharepoint.client.api.list.getlists.GetListsResponse;

/**
 * SharePoint 2013 specific response class for GetLists API operations.
 * This class extends GetListsResponse to provide SharePoint 2013 compatibility
 * while maintaining the same interface as the base response class.
 *
 * @see GetListsResponse
 * @see GetLists2013
 */
public class GetLists2013Response extends GetListsResponse {

    /**
     * Constructs a new GetLists2013Response with the provided list of SharePoint lists.
     *
     * @param lists list of SharePointList objects parsed from SharePoint 2013 response
     */
    public GetLists2013Response(final List<SharePointList> lists) {
        super(lists);
    }

    /**
     * Gets the list of SharePoint lists retrieved from SharePoint 2013.
     *
     * @return list of SharePointList objects
     */
    @Override
    public List<SharePointList> getLists() {
        return lists;
    }

}
