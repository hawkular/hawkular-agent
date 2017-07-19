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
package org.hawkular.agent.monitor.inventory;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.hawkular.agent.monitor.api.Avail;
import org.hawkular.agent.monitor.protocol.dmr.DMRNodeLocation;
import org.junit.Assert;
import org.junit.Test;

public class AvailManagerTest {

    @Test
    public void testAddAvail() {
        AvailManager<DMRNodeLocation> am = new AvailManager<>();
        MeasurementInstance<DMRNodeLocation, AvailType<DMRNodeLocation>> measurementInstance =
                new MeasurementInstance<>(
                        new ID("foo"),
                        new Name("bar"),
                        new AttributeLocation<>(DMRNodeLocation.empty(), "foobar"),
                        new AvailType<>(
                                new ID("foo-avail"),
                                new Name("bar-avail"),
                                new AttributeLocation<>(DMRNodeLocation.empty(),"foobar-avail"),
                                new Interval(666, TimeUnit.DAYS),
                                Pattern.compile("foo"),
                                "foobar-id-template",
                                null));
        AvailManager.AddResult result;

        result = am.addAvail(measurementInstance, Avail.UP);
        Assert.assertEquals(Avail.UP, result.getAvail());
        Assert.assertEquals(measurementInstance, result.getMeasurementInstance());
        Assert.assertEquals(AvailManager.AddResult.Effect.STARTING, result.getEffect());

        result = am.addAvail(measurementInstance, Avail.UP);
        Assert.assertEquals(AvailManager.AddResult.Effect.UNCHANGED, result.getEffect());

        result = am.addAvail(measurementInstance, Avail.DOWN);
        Assert.assertEquals(Avail.DOWN, result.getAvail());
        Assert.assertEquals(AvailManager.AddResult.Effect.MODIFIED, result.getEffect());
    }
}
