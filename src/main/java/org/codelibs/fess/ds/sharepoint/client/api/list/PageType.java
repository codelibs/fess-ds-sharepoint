/*
 * Copyright 2012-2023 CodeLibs Project and the Others.
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
package org.codelibs.fess.ds.sharepoint.client.api.list;

import java.util.Arrays;

public enum PageType {
    DEFAULT(0), DIALOG_VIEW(2), DISPLAY_FORM(4), DISPLAY_FORM_DIALOG(5), EDIT_FORM(6), EDIT_FORM_DIALOG(7), INVALID(-1), NEW_FORM(
            8), NEW_FORM_DIALOG(9), NORMAL_VIEW(1), PAGE_MAXITEMS(11), SOLUTION_FORM(10), VIEW(3),;

    private final int typeNumber;

    PageType(final int typeNumber) {
        this.typeNumber = typeNumber;
    }

    protected int getTypeNumber() {
        return typeNumber;
    }

    public static PageType getPageType(final int type) {
        return Arrays.stream(PageType.values()).filter(value -> value.typeNumber == type).findFirst().orElse(INVALID);
    }
}
