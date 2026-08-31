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
package org.codelibs.fess.ds.sharepoint.client.oauth;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.Property;
import org.codelibs.fess.ds.sharepoint.UnitDsTestCase;
import org.junit.jupiter.api.Test;

public class OAuthTest extends UnitDsTestCase {

    @Override
    protected boolean isSuppressTestCaseTransaction() {
        return true;
    }

    /**
     * Microsoft has scheduled Azure ACS - the only mechanism this class implements - for
     * retirement, so construction warns about it. The warning is the whole point of that code, and
     * an assertion that only construction succeeds cannot tell whether it was logged: deleting the
     * warning outright left such an assertion green.
     */
    @Test
    public void test_constructor_warnsThatAcsIsDeprecated() {
        final List<String> warnings = new ArrayList<>();
        final Logger logger = (Logger) LogManager.getLogger(OAuth.class);
        final Level original = logger.getLevel();
        final Appender appender = capturing(warnings);
        Configurator.setLevel(OAuth.class.getName(), Level.WARN);
        logger.addAppender(appender);
        try {
            new OAuth("clientId", "clientSecret", "tenant", "realm");
        } finally {
            logger.removeAppender(appender);
            appender.stop();
            Configurator.setLevel(OAuth.class.getName(), original);
        }

        assertEquals("construction must log exactly one warning", 1, warnings.size());
        assertTrue("the warning must name the deprecated mechanism: " + warnings.get(0),
                warnings.get(0).contains("Access Control Service"));
        assertTrue("and it must name the replacement to migrate to: " + warnings.get(0), warnings.get(0).contains("Entra"));
    }

    /** An appender that records the formatted message of every WARN event it is handed. */
    private static Appender capturing(final List<String> warnings) {
        final Appender appender = new AbstractAppender("oauth-test-capture", (Filter) null, (Layout<? extends Serializable>) null, true,
                Property.EMPTY_ARRAY) {
            @Override
            public void append(final LogEvent event) {
                if (Level.WARN.equals(event.getLevel())) {
                    warnings.add(event.getMessage().getFormattedMessage());
                }
            }
        };
        appender.start();
        return appender;
    }
}
