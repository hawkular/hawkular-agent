/*
 * Copyright 2015-2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.agent.monitor.service;

import java.io.File;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Obtains version info from this class's manifest.
 *
 * @author John Mazzitelli
 */
public class Version {
    public static final String PROP_IMPL_TITLE = "Implementation-Title";
    public static final String PROP_IMPL_VERSION = "Implementation-Version";
    public static final String PROP_BUILD_SHA = "Built-From-Git-SHA1";
    public static final String PROP_BUILD_DATE = "Built-On";

    /**
     * Caches the properties so we don't have to keep getting them.
     */
    private static Properties propertiesCache = null;

    public static String getVersionString() {
        Properties props = getVersionProperties();
        String title = props.getProperty(PROP_IMPL_TITLE);
        String version = props.getProperty(PROP_IMPL_VERSION);
        String sha = props.getProperty(PROP_BUILD_SHA);
        String buildDate = props.getProperty(PROP_BUILD_DATE);
        return String.format("%s: %s (%s) built on [%s]", title, version, sha, buildDate);

    }

    public static Properties getVersionProperties() {
        if (propertiesCache == null) {
            Properties newProps = new Properties();
            try {
                String jarUrl = Version.class.getProtectionDomain().getCodeSource().getLocation().getFile();
                try (JarFile jar = new JarFile(new File(jarUrl))) {
                    Manifest manifest = jar.getManifest();
                    Attributes attributes = manifest.getMainAttributes();
                    for (Entry<Object, Object> entry : attributes.entrySet()) {
                        newProps.setProperty(entry.getKey().toString(), entry.getValue().toString());
                    }
                }
            } catch (Exception e) {
                newProps.put(PROP_BUILD_SHA, "unknown");
                newProps.put(PROP_BUILD_DATE, "unknown");
                Package pkg = Version.class.getPackage();
                if (pkg != null) {
                    newProps.put(PROP_IMPL_TITLE, pkg.getImplementationTitle());
                    newProps.put(PROP_IMPL_VERSION, pkg.getImplementationVersion());
                } else {
                    newProps.put(PROP_IMPL_TITLE, "unknown");
                    newProps.put(PROP_IMPL_VERSION, "unknown");
                }
            }
            propertiesCache = newProps;
        }

        Properties retProps = new Properties();
        retProps.putAll(propertiesCache);
        return retProps;
    }
}
