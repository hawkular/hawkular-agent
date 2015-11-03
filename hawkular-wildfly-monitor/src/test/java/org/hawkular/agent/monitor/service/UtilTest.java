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
package org.hawkular.agent.monitor.service;

import java.net.MalformedURLException;
import java.util.HashMap;

import org.junit.Assert;
import org.junit.Test;

public class UtilTest {
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
}
