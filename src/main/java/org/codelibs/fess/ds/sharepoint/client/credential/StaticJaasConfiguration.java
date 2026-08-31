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
package org.codelibs.fess.ds.sharepoint.client.credential;

import java.util.HashMap;
import java.util.Map;

import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.AppConfigurationEntry.LoginModuleControlFlag;
import javax.security.auth.login.Configuration;

/**
 * A JAAS {@link Configuration} that answers every application name with one required
 * {@code Krb5LoginModule} entry carrying a fixed set of options.
 *
 * <p>Handed to the four-argument {@link javax.security.auth.login.LoginContext} constructor so the
 * configuration is scoped to that one login rather than installed JVM-wide with
 * {@link Configuration#setConfiguration(Configuration)}. One crawler JVM runs every data config of
 * a crawl job, so a JVM-wide configuration set by one data config would silently become the
 * configuration of all the others. Passing it explicitly also means no
 * {@code AuthPermission("createLoginContext.<name>")} has to be granted.
 */
final class StaticJaasConfiguration extends Configuration {

    /** The login module every entry returned by this configuration names. */
    private static final String KRB5_LOGIN_MODULE = "com.sun.security.auth.module.Krb5LoginModule";

    /** The options passed to the login module. */
    private final Map<String, String> options;

    /**
     * Creates a configuration whose single login module entry carries the given options.
     *
     * @param options the {@code Krb5LoginModule} options; copied, so the caller may reuse the map
     */
    StaticJaasConfiguration(final Map<String, String> options) {
        this.options = new HashMap<>(options);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The application name is ignored: this configuration exists to serve exactly one login.
     */
    @Override
    public AppConfigurationEntry[] getAppConfigurationEntry(final String name) {
        return new AppConfigurationEntry[] { new AppConfigurationEntry(KRB5_LOGIN_MODULE, LoginModuleControlFlag.REQUIRED, options) };
    }
}
