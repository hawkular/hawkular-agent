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
package org.hawkular.agent.monitor.protocol.dmr;

import org.hawkular.agent.monitor.protocol.ProtocolException;
import org.junit.Assert;
import org.junit.Test;

public class DMRLocationResolverTest {

    @Test
    public void testFindWildcardMatch() throws Exception {
        DMRLocationResolver resolver = new DMRLocationResolver();
        String match = resolver.findWildcardMatch(DMRNodeLocation.of("/one=1/two=*"),
                DMRNodeLocation.of("/one=111/two=222"));
        Assert.assertEquals("222", match);

        try {
            match = resolver.findWildcardMatch(DMRNodeLocation.of("/one=1"), DMRNodeLocation.of("/one=111"));
            Assert.fail("Should not have matched: " + match);
        } catch (ProtocolException ok) {
        }

        try {
            match = resolver.findWildcardMatch(null, DMRNodeLocation.of("/one=111"));
            Assert.fail("Should not have matched: " + match);
        } catch (ProtocolException ok) {
        }
    }

    @Test
    public void testIsMultiTarget() {
        DMRLocationResolver resolver = new DMRLocationResolver();
        Assert.assertFalse(resolver.isMultiTarget(DMRNodeLocation.of("/one=1/two=2/three=3")));
        Assert.assertTrue(resolver.isMultiTarget(DMRNodeLocation.of("/one=*/two=2/three=3")));
        Assert.assertTrue(resolver.isMultiTarget(DMRNodeLocation.of("/one=1/two=*/three=3")));
        Assert.assertTrue(resolver.isMultiTarget(DMRNodeLocation.of("/one=1/two=2/three=*")));
    }

    @Test
    public void testAbsolutize() {
        DMRLocationResolver resolver = new DMRLocationResolver();
        DMRNodeLocation abs;

        abs = resolver.absolutize(null, DMRNodeLocation.of("/two=2/three=3"));
        Assert.assertEquals("/two=2/three=3", abs.toString());

        abs = resolver.absolutize(DMRNodeLocation.of("/two=2/three=3"), (DMRNodeLocation) null);
        Assert.assertEquals("/two=2/three=3", abs.toString());

        abs = resolver.absolutize(DMRNodeLocation.empty(), DMRNodeLocation.of("/two=2/three=3"));
        Assert.assertEquals("/two=2/three=3", abs.toString());

        abs = resolver.absolutize(DMRNodeLocation.of("/one=1"), DMRNodeLocation.of("/two=2/three=3"));
        Assert.assertEquals("/one=1/two=2/three=3", abs.toString());
    }

    @Test
    public void testIsParent() throws Exception {
        DMRLocationResolver res = new DMRLocationResolver();
        Assert.assertTrue(res.isParent(DMRNodeLocation.empty(), DMRNodeLocation.of("/one=1")));
        Assert.assertTrue(res.isParent(DMRNodeLocation.of("/"), DMRNodeLocation.of("/one=1")));
        Assert.assertTrue(res.isParent(DMRNodeLocation.of("/one=1"), DMRNodeLocation.of("/one=1/two=2")));
        Assert.assertTrue(res.isParent(DMRNodeLocation.of("/o=1/t=2"), DMRNodeLocation.of("/o=1/t=2/thr=3")));

        Assert.assertFalse(res.isParent(DMRNodeLocation.of("/one=1"), DMRNodeLocation.of("/one=1")));
        Assert.assertFalse(res.isParent(DMRNodeLocation.of("/one=1"), DMRNodeLocation.of("/two=2")));

        // grandchild
        Assert.assertTrue(res.isParent(DMRNodeLocation.of("/one=1"), DMRNodeLocation.of("/one=1/two=2/three=3")));

    }

    @Test
    public void testMatches() {
        DMRLocationResolver resolver = new DMRLocationResolver();
        Assert.assertTrue(resolver.matches(DMRNodeLocation.of("/one=*/two=2"), DMRNodeLocation.of("/one=1/two=2")));
        Assert.assertTrue(resolver.matches(DMRNodeLocation.of("/one=1/two=*"), DMRNodeLocation.of("/one=1/two=2")));
        Assert.assertTrue(resolver.matches(DMRNodeLocation.of("/one=*/two=*"), DMRNodeLocation.of("/one=1/two=2")));
        Assert.assertFalse(resolver.matches(DMRNodeLocation.of("/one=1/two=XX"), DMRNodeLocation.of("/one=1/two=2")));
    }

    @Test
    public void testApplyTemplate() {
        DMRLocationResolver resolver = new DMRLocationResolver();
        DMRNodeLocation location = DMRNodeLocation.of("/one=1/two=2");
        String endpointName = "eName";

        String str = resolver.applyTemplate("hello", location, endpointName);
        Assert.assertEquals("hello", str);

        str = resolver.applyTemplate("hello %-", location, endpointName);
        Assert.assertEquals("hello 2", str);

        str = resolver.applyTemplate("hello %1 %2", location, endpointName);
        Assert.assertEquals("hello one 1", str);

        str = resolver.applyTemplate("hello %1.%2.%3.%4.[%ManagedServerName]", location, endpointName);
        Assert.assertEquals("hello one.1.two.2.[eName]", str);
    }

}
