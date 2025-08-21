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
package org.codelibs.fess.ds.sharepoint.client2013.api.list.getlistitems;

import java.util.List;

import org.codelibs.fess.ds.sharepoint.client.api.list.getlistitems.GetListItemsResponse;

/**
 * SharePoint 2013 specific response class for GetListItems API.
 * Extends the base GetListItemsResponse with SharePoint 2013 specific behavior.
 */
public class GetListItems2013Response extends GetListItemsResponse {

    /**
     * Constructs a new GetListItems2013Response with the provided list items.
     *
     * @param listItems the list of items retrieved from SharePoint 2013
     */
    public GetListItems2013Response(final List<ListItem> listItems) {
        super(listItems);
    }

    /**
     * Returns the list of items retrieved from SharePoint 2013.
     *
     * @return the list of SharePoint list items
     */
    @Override
    public List<ListItem> getListItems() {
        return listItems;
    }
}
