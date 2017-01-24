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
package org.hawkular.agent.monitor.protocol;

import javax.management.ObjectName;

import org.hawkular.agent.monitor.protocol.dmr.DMRLocationResolver;
import org.hawkular.agent.monitor.protocol.dmr.DMRNodeLocation;
import org.hawkular.agent.monitor.protocol.jmx.JMXLocationResolver;
import org.hawkular.agent.monitor.protocol.jmx.JMXNodeLocation;
import org.hawkular.agent.monitor.protocol.platform.Constants;
import org.hawkular.agent.monitor.protocol.platform.PlatformLocationResolver;
import org.hawkular.agent.monitor.protocol.platform.PlatformNodeLocation;
import org.hawkular.agent.monitor.protocol.platform.PlatformPath;
import org.hawkular.agent.monitor.util.WildflyCompatibilityUtils;
import org.junit.Assert;
import org.junit.Test;

public class LocationResolverTest {

    @Test
    public void testFindWildcardMatchDMR() throws Exception {
        DMRNodeLocation multiTargetLocation = new DMRNodeLocation(
                WildflyCompatibilityUtils.parseCLIStyleAddress("/subsystem=datasources/data-source=DS/connection-properties=*"));
        DMRNodeLocation singleLocation = new DMRNodeLocation(
                WildflyCompatibilityUtils.parseCLIStyleAddress("/subsystem=datasources/data-source=DS/connection-properties=foo"));
        DMRLocationResolver resolver = new DMRLocationResolver();
        Assert.assertEquals("foo", resolver.findWildcardMatch(multiTargetLocation, singleLocation));

        singleLocation = new DMRNodeLocation(
                WildflyCompatibilityUtils.parseCLIStyleAddress("/subsystem=datasources/data-source=DS"));
        try {
            resolver.findWildcardMatch(multiTargetLocation, singleLocation);
            Assert.fail("Single location was missing 'connection-properties' key - should have failed");
        } catch (ProtocolException expected) {
        }

        singleLocation = new DMRNodeLocation(
                WildflyCompatibilityUtils.parseCLIStyleAddress("/subsystem=datasources/data-source=DS/not-what-we-want=foo"));
        try {
            resolver.findWildcardMatch(multiTargetLocation, singleLocation);
            Assert.fail("Single location was missing 'connection-properties' key - should have failed");
        } catch (ProtocolException expected) {
        }
    }

    @Test
    public void testFindWildcardMatchJMX() throws Exception {
        JMXNodeLocation multiTargetLocation = new JMXNodeLocation(
                new ObjectName("domain:name=value,matchme=*"));
        JMXNodeLocation singleLocation = new JMXNodeLocation(
                new ObjectName("domain:name2=value2,name3=value3,matchme=foo,name=value"));
        JMXLocationResolver resolver = new JMXLocationResolver();
        Assert.assertEquals("foo", resolver.findWildcardMatch(multiTargetLocation, singleLocation));

        singleLocation = new JMXNodeLocation(new ObjectName("domain:name2=value2,name3=value3,name=value"));
        try {
            resolver.findWildcardMatch(multiTargetLocation, singleLocation);
            Assert.fail("Single location was missing 'matchme' key - should have failed");
        } catch (ProtocolException expected) {
        }
    }

    @Test
    public void testFindWildcardMatchPlatform() throws Exception {
        PlatformNodeLocation multiTargetLocation = new PlatformNodeLocation(
                PlatformPath.builder().segment(Constants.PlatformResourceType.OPERATING_SYSTEM, "linux")
                        .any(Constants.PlatformResourceType.FILE_STORE).build());
        PlatformNodeLocation singleLocation = new PlatformNodeLocation(
                PlatformPath.builder().segment(Constants.PlatformResourceType.OPERATING_SYSTEM, "linux")
                        .segment(Constants.PlatformResourceType.FILE_STORE, "foo").build());
        PlatformLocationResolver resolver = new PlatformLocationResolver();
        Assert.assertEquals("foo", resolver.findWildcardMatch(multiTargetLocation, singleLocation));
    }

}
