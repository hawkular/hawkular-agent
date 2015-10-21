/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.wildfly.module.installer;

import java.io.File;
import java.net.URL;

import org.junit.Assert;
import org.junit.Test;

public class RegisterModuleConfigurationTest {

    @Test
    public void textExtendWithNull() throws Exception {
        URL subsystem = new File("foo").toURI().toURL();
        RegisterModuleConfiguration o1 = new RegisterModuleConfiguration()
            .subsystem(subsystem)
            .socketBinding(subsystem);

        RegisterModuleConfiguration o2 = new RegisterModuleConfiguration();
        o1.extend(o2);
        Assert.assertEquals(subsystem, o1.getSubsystem());
        Assert.assertEquals(subsystem, o1.getSocketBinding());
    }

    @Test
    public void testExtend() throws Exception {
        URL subsystem = new File("foo").toURI().toURL();
        RegisterModuleConfiguration o1 = new RegisterModuleConfiguration()
            .subsystem(subsystem);

        URL subsystem2 = new File("foo2").toURI().toURL();
        RegisterModuleConfiguration o2 = new RegisterModuleConfiguration()
            .subsystem(subsystem2);

        o1.extend(o2);
        Assert.assertEquals(subsystem2, o1.getSubsystem());
    }
}
