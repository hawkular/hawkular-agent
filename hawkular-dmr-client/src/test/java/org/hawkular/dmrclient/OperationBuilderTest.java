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
package org.hawkular.dmrclient;

import org.hawkular.dmr.api.OperationBuilder;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class OperationBuilderTest {

    private static final String DATASOURCE_NAME = "h2";

    @Test
    public void testWriteAttribute() {

        ModelNode actual = OperationBuilder.writeAttribute()//
                .address().subsystemDatasources().datasource(DATASOURCE_NAME).parentBuilder()//
                .attribute("k1", "v1").build();

        String expected = "{\n" //
                + "    \"operation\" => \"write-attribute\",\n" //
                + "    \"address\" => [\n" //
                + "        (\"subsystem\" => \"datasources\"),\n" //
                + "        (\"data-source\" => \"h2\")\n" //
                + "    ],\n" //
                + "    \"name\" => \"k1\",\n" //
                + "    \"value\" => \"v1\"\n" //
                + "}" //
                ;
        // DmrUtils.printJavaStringLiteral(actual);
        Assert.assertEquals(expected, actual.toString());
    }

    @Test
    public void testAdd() {

        ModelNode actual = OperationBuilder.add()//
                .address().subsystemDatasources().datasource(DATASOURCE_NAME).parentBuilder()//
                .attribute("k1", "v1").build();

        String expected = "{\n" //
                + "    \"operation\" => \"add\",\n" //
                + "    \"address\" => [\n" //
                + "        (\"subsystem\" => \"datasources\"),\n" //
                + "        (\"data-source\" => \"h2\")\n" //
                + "    ],\n" //
                + "    \"k1\" => \"v1\"\n" //
                + "}" //
                ;
        // DmrUtils.printJavaStringLiteral(actual);
        Assert.assertEquals(expected, actual.toString());
    }

    @Test
    public void testRemove() {

        ModelNode actual = OperationBuilder.remove()//
                .address().subsystemDatasources().datasource(DATASOURCE_NAME).parentBuilder()//
                .build();

        String expected = "{\n" //
                + "    \"operation\" => \"remove\",\n" //
                + "    \"address\" => [\n" //
                + "        (\"subsystem\" => \"datasources\"),\n" //
                + "        (\"data-source\" => \"h2\")\n" //
                + "    ]\n" //
                + "}" //
                ;
        // DmrUtils.printJavaStringLiteral(actual);
        Assert.assertEquals(expected, actual.toString());
    }

    @Test
    public void testAddressFromString() {

        ModelNode actual = OperationBuilder.address().segments("/subsystem=datasources/data-source=h2").build();

        String expected = "[\n" //
                + "    (\"subsystem\" => \"datasources\"),\n" //
                + "    (\"data-source\" => \"h2\")\n" //
                + "]" //
                ;
        // DmrUtils.printJavaStringLiteral(actual);
        Assert.assertEquals(expected, actual.toString());
    }

    @Test
    public void testAddressFromAddress() {

        ModelNode parent = OperationBuilder.address().segments("/subsystem=datasources/data-source=h2").build();

        ModelNode actual = OperationBuilder.address().segments(parent).segment("connection-properties", "prop1")
                .build();

        String expected = "[\n" //
                + "    (\"subsystem\" => \"datasources\"),\n" //
                + "    (\"data-source\" => \"h2\"),\n" //
                + "    (\"connection-properties\" => \"prop1\")\n" //
                + "]" //
                ;
        // DmrUtils.printJavaStringLiteral(actual);
        Assert.assertEquals(expected, actual.toString());
    }

}
