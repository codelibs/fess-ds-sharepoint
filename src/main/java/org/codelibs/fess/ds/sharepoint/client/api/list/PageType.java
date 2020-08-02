package org.codelibs.fess.ds.sharepoint.client.api.list;

import java.util.Arrays;

public enum PageType {
    DEFAULT(0),
    DIALOG_VIEW(2),
    DISPLAY_FORM(4),
    DISPLAY_FORM_DIALOG(5),
    EDIT_FORM(6),
    EDIT_FORM_DIALOG(7),
    INVALID(-1),
    NEW_FORM(8),
    NEW_FORM_DIALOG(9),
    NORMAL_VIEW(1),
    PAGE_MAXITEMS(11),
    SOLUTION_FORM(10),
    VIEW(3),
    ;

    private final int typeNumber;

    PageType(final int typeNumber) {
        this.typeNumber = typeNumber;
    }

    private int getTypeNumber() {
        return typeNumber;
    }

    public static PageType getPageType(final int type) {
        return Arrays.stream(PageType.values()).filter(value -> value.typeNumber == type).findFirst().orElse(INVALID);
    }
}
