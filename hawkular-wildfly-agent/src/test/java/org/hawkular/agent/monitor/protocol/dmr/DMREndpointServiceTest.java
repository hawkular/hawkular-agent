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

import static org.junit.Assert.assertEquals;

import java.util.UUID;

import org.junit.Test;


/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 *
 */
public class DMREndpointServiceTest {
    @Test
    public void testServerId() {

        String id = DMREndpointService.getServerIdentifier("one", "two", "three", null);
        assertEquals("one.two.three", id);

        id = DMREndpointService.getServerIdentifier(null, "two", "three", null);
        assertEquals("two.three", id);

        id = DMREndpointService.getServerIdentifier(null, null, "three", null);
        assertEquals("three", id);

        id = DMREndpointService.getServerIdentifier(null, null, null, null);
        assertEquals("", id);

        id = DMREndpointService.getServerIdentifier("", "two", "three", null);
        assertEquals("two.three", id);

        id = DMREndpointService.getServerIdentifier("", "", "three", null);
        assertEquals("three", id);

        id = DMREndpointService.getServerIdentifier("", "", "", null);
        assertEquals("", id);

        // if server name and node name are the same, only one is added to the full ID
        id = DMREndpointService.getServerIdentifier("one", "two", "two", null);
        assertEquals("one.two", id);

        id = DMREndpointService.getServerIdentifier("", "two", "two", null);
        assertEquals("two", id);

        id = DMREndpointService.getServerIdentifier(null, "two", "two", null);
        assertEquals("two", id);

        String uuid = UUID.randomUUID().toString();
        id = DMREndpointService.getServerIdentifier("a", "b", "c", uuid);
        assertEquals(uuid, id);
    }
}
