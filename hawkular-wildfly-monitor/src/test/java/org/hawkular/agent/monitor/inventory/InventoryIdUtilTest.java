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
package org.hawkular.agent.monitor.inventory;

import org.hawkular.agent.monitor.inventory.InventoryIdUtil.ResourceIdParts;
import org.hawkular.agent.monitor.inventory.dmr.RemoteDMRManagedServer;
import org.junit.Assert;
import org.junit.Test;

public class InventoryIdUtilTest {

    @Test
    public void testParsingResourceId() {
        ID id;
        ResourceIdParts parts;

        id = InventoryIdUtil.generateResourceId(
                "testfeedid",
                new RemoteDMRManagedServer(null, new Name("testmanagedserver")), "/test/id/path");

        Assert.assertEquals("testfeedid~testmanagedserver~/test/id/path", id.toString());
        parts = InventoryIdUtil.parseResourceId(id.getIDString());
        Assert.assertEquals("testfeedid", parts.getFeedId());
        Assert.assertEquals("testmanagedserver", parts.getManagedServerName());
        Assert.assertEquals("/test/id/path", parts.getIdPart());

        // test that you can have ~ in the last part of the ID
        id = InventoryIdUtil.generateResourceId(
                "testfeedid",
                new RemoteDMRManagedServer(null, new Name("testmanagedserver")), "~/~test/~id/~path");

        Assert.assertEquals("testfeedid~testmanagedserver~~/~test/~id/~path", id.toString());
        parts = InventoryIdUtil.parseResourceId(id.getIDString());
        Assert.assertEquals("testfeedid", parts.getFeedId());
        Assert.assertEquals("testmanagedserver", parts.getManagedServerName());
        Assert.assertEquals("~/~test/~id/~path", parts.getIdPart());
    }
}
