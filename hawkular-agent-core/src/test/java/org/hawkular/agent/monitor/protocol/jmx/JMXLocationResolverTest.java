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
package org.hawkular.agent.monitor.protocol.jmx;

import org.hawkular.agent.monitor.protocol.ProtocolException;
import org.junit.Assert;
import org.junit.Test;

public class JMXLocationResolverTest extends JMXLocationResolver {

    @Test
    public void testFindWildcardMatch() throws Exception {
        JMXLocationResolver resolver = new JMXLocationResolver();
        String match = resolver.findWildcardMatch(new JMXNodeLocation("domain:one=1,two=*"),
                new JMXNodeLocation("domain:one=1,two=222"));
        Assert.assertEquals("222", match);

        try {
            match = resolver.findWildcardMatch(new JMXNodeLocation("domain:one=1"),
                    new JMXNodeLocation("domain:one=111"));
            Assert.fail("Should not have matched: " + match);
        } catch (ProtocolException ok) {
        }

        try {
            match = resolver.findWildcardMatch(null, new JMXNodeLocation("domain:one=1"));
            Assert.fail("Should not have matched: " + match);
        } catch (ProtocolException ok) {
        }
    }

    @Test
    public void testIsMultiTarget() throws Exception {
        JMXLocationResolver resolver = new JMXLocationResolver();
        Assert.assertFalse(resolver.isMultiTarget(new JMXNodeLocation("domain:one=1,two=2,three=3")));
        Assert.assertTrue(resolver.isMultiTarget(new JMXNodeLocation("domain:one=*,two=2,three=3")));
        Assert.assertTrue(resolver.isMultiTarget(new JMXNodeLocation("domain:one=1,two=*,three=3")));
        Assert.assertTrue(resolver.isMultiTarget(new JMXNodeLocation("domain:one=1,two=2,three=*")));
    }

    @Test
    public void testAbsolutize() throws Exception {
        JMXLocationResolver resolver = new JMXLocationResolver();
        JMXNodeLocation abs;

        abs = resolver.absolutize(null, new JMXNodeLocation("domain:one=1,two=2"));
        Assert.assertEquals("domain:one=1,two=2", abs.toString());

        abs = resolver.absolutize(new JMXNodeLocation("domain:one=1,two=2"), (JMXNodeLocation) null);
        Assert.assertEquals("domain:one=1,two=2", abs.toString());

        // absolutize doesn't really do anything for JMX locations - always returns location if its not null
        abs = resolver.absolutize(new JMXNodeLocation("domain:foo=bar"), new JMXNodeLocation("domain:one=1,two=2"));
        Assert.assertEquals("domain:one=1,two=2", abs.toString());
    }

    @Test
    public void testIsParent() throws Exception {
        JMXLocationResolver jmx = new JMXLocationResolver();
        Assert.assertFalse(
                jmx.isParent(new JMXNodeLocation("domain:one=1"),
                        new JMXNodeLocation("domain:two=2")));
        Assert.assertFalse(
                jmx.isParent(new JMXNodeLocation("domain:one=1"),
                        new JMXNodeLocation("domain:one=111")));
        Assert.assertTrue(
                jmx.isParent(new JMXNodeLocation("domain:one=1,two=2"),
                        new JMXNodeLocation("domain:one=1,two=2,three=3")));
        // grandchild
        Assert.assertTrue(
                jmx.isParent(new JMXNodeLocation("domain:one=1"),
                        new JMXNodeLocation("domain:one=1,two=2,three=3")));
    }

    @Test
    public void testMatches() throws Exception {
        JMXLocationResolver resolver = new JMXLocationResolver();
        Assert.assertTrue(resolver.matches(new JMXNodeLocation("domain:one=*,two=2"),
                new JMXNodeLocation("domain:one=1,two=2")));
        Assert.assertTrue(resolver.matches(new JMXNodeLocation("domain:one=1,two=*"),
                new JMXNodeLocation("domain:one=1,two=2")));
        Assert.assertTrue(resolver.matches(new JMXNodeLocation("domain:one=*,two=*"),
                new JMXNodeLocation("domain:one=1,two=2")));
        Assert.assertFalse(resolver.matches(new JMXNodeLocation("domain:one=1,two=XX"),
                new JMXNodeLocation("domain:one=1,two=2")));
    }

    @Test
    public void testApplyTemplate() throws Exception {
        JMXLocationResolver resolver = new JMXLocationResolver();
        JMXNodeLocation location = new JMXNodeLocation("domain:one=1,two=2");
        String endpointName = "eName";

        String str = resolver.applyTemplate("hello", location, endpointName);
        Assert.assertEquals("hello", str);

        str = resolver.applyTemplate("hello %two%", location, endpointName);
        Assert.assertEquals("hello 2", str);

        str = resolver.applyTemplate("hello %one% %two%", location, endpointName);
        Assert.assertEquals("hello 1 2", str);

        str = resolver.applyTemplate("hello %one%.%two%.[%_ManagedServerName%]", location, endpointName);
        Assert.assertEquals("hello 1.2.[eName]", str);
    }

}
