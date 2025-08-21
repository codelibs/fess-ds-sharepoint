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
package org.codelibs.fess.ds.sharepoint.client.api.list;

import java.util.Arrays;

/**
 * Enumeration representing different types of SharePoint pages.
 */
public enum PageType {
    /** Default page type. */
    DEFAULT(0),
    /** Dialog view page type. */
    DIALOG_VIEW(2),
    /** Display form page type. */
    DISPLAY_FORM(4),
    /** Display form dialog page type. */
    DISPLAY_FORM_DIALOG(5),
    /** Edit form page type. */
    EDIT_FORM(6),
    /** Edit form dialog page type. */
    EDIT_FORM_DIALOG(7),
    /** Invalid page type. */
    INVALID(-1),
    /** New form page type. */
    NEW_FORM(8),
    /** New form dialog page type. */
    NEW_FORM_DIALOG(9),
    /** Normal view page type. */
    NORMAL_VIEW(1),
    /** Page max items page type. */
    PAGE_MAXITEMS(11),
    /** Solution form page type. */
    SOLUTION_FORM(10),
    /** View page type. */
    VIEW(3);

    private final int typeNumber;

    PageType(final int typeNumber) {
        this.typeNumber = typeNumber;
    }

    /**
     * Gets the type number for this page type.
     *
     * @return the type number
     */
    protected int getTypeNumber() {
        return typeNumber;
    }

    /**
     * Gets the PageType enum for the given type number.
     *
     * @param type the type number
     * @return the corresponding PageType, or INVALID if not found
     */
    public static PageType getPageType(final int type) {
        return Arrays.stream(PageType.values()).filter(value -> value.typeNumber == type).findFirst().orElse(INVALID);
    }
}
