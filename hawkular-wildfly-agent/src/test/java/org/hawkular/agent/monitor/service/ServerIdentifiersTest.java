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

import java.util.UUID;

import org.junit.Test;

public class ServerIdentifiersTest {

    @Test
    public void testGetFullIdentifier() {
        ServerIdentifiers id = new ServerIdentifiers("one", "two", "three", null);
        assert id.toString().equals("one.two.three");

        id = new ServerIdentifiers(null, "two", "three", null);
        assert id.toString().equals("two.three");

        id = new ServerIdentifiers(null, null, "three", null);
        assert id.toString().equals("three");

        id = new ServerIdentifiers(null, null, null, null);
        assert id.toString().equals("");

        id = new ServerIdentifiers("", "two", "three", null);
        assert id.toString().equals("two.three");

        id = new ServerIdentifiers("", "", "three", null);
        assert id.toString().equals("three");

        id = new ServerIdentifiers("", "", "", null);
        assert id.toString().equals("");

        // if server name and node name are the same, only one is added to the full ID
        id = new ServerIdentifiers("one", "two", "two", null);
        assert id.toString().equals("one.two");

        id = new ServerIdentifiers("", "two", "two", null);
        assert id.toString().equals("two");

        id = new ServerIdentifiers(null, "two", "two", null);
        assert id.toString().equals("two");

        String uuid = UUID.randomUUID().toString();
        id = new ServerIdentifiers("a", "b", "c", uuid);
        assert id.toString().equals(uuid) : String.format("Should have been [%s] but was [%s]", uuid, id);
    }

}
