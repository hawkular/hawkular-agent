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
package org.hawkular.agent.monitor.util;

import java.net.MalformedURLException;
import java.util.HashMap;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class UtilTest {
    @Test
    public void testExtractHashFromString() {
        Assert.assertNull(Util.extractDockerContainerIdFromString(null));
        Assert.assertNull(Util.extractDockerContainerIdFromString(""));
        Assert.assertNull(Util.extractDockerContainerIdFromString("\n"));
        Assert.assertNull(Util.extractDockerContainerIdFromString("This has no hash"));
        Assert.assertNull(Util.extractDockerContainerIdFromString("Missing the word dock?r 1234567890abcdefABCDEF"));

        String l;
        String hash;

        l = "11:memory:/docker/99cb4a5d8c7a8a29d01dfcbb7c2ba210bad5470cc7a86474945441361a37513a\n";
        hash = "99cb4a5d8c7a8a29d01dfcbb7c2ba210bad5470cc7a86474945441361a37513a";
        Assert.assertEquals(hash, Util.extractDockerContainerIdFromString(l));

        l = "11:freezer:/system.slice/docker-c4a970c28c9d277373b5d1458679ac17c10db8538dd081072a95682b4396674f.scope\n";
        hash = "c4a970c28c9d277373b5d1458679ac17c10db8538dd081072a95682b4396674f";
        Assert.assertEquals(hash, Util.extractDockerContainerIdFromString(l));

        l = "9876543210fedcba blah blah docker blah blah-1234567890abcdefABCDEF.yadda fedcba9876543210";
        hash = "1234567890abcdefABCDEF";
        Assert.assertEquals(hash, Util.extractDockerContainerIdFromString(l));
    }

    @Test
    public void encodeUrlSpace() {
        String encoded = Util.urlEncode("space should be a percent20");
        Assert.assertEquals("space%20should%20be%20a%20percent20", encoded);
    }

    @Test
    public void getContextUrl() throws MalformedURLException {
        StringBuilder url = Util.getContextUrlString("http://host:8080", "some-context");
        Assert.assertEquals("http://host:8080/some-context/", url.toString());
    }

    @Test
    public void json() {
        HashMap<String, String> map = new HashMap<>();
        map.put("one", "1");
        map.put("two", "2");
        String json = Util.toJson(map);
        Assert.assertEquals("{\"one\":\"1\",\"two\":\"2\"}", json);

        HashMap<?, ?> mapDup = Util.fromJson(json, HashMap.class);
        Assert.assertEquals(map, mapDup);
    }

    @Test
    @Ignore("CI system has no /etc/machine-id . But this should work on Fedora/RHEL/CentOS boxes.")
    public void getSystemId() {
        Assert.assertNotNull(Util.getMachineId());
    }
}
