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
package org.codelibs.fess.ds.sharepoint.client2013.api.doclib.getlistitem;

import org.codelibs.fess.ds.sharepoint.client.api.doclib.getlistitem.GetDoclibListItemResponse;

public class GetDoclibListItem2013Response extends GetDoclibListItemResponse {
    public GetDoclibListItem2013Response(final String listId, final String itemId) {
        super(listId, itemId);
    }

    @Override
    public String getListId() {
        return listId;
    }

    @Override
    public String getItemId() {
        return itemId;
    }
}
