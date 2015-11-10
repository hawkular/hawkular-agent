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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.agent.monitor.inventory.TypeSet.TypeSetBuilder;
import org.hawkular.agent.monitor.protocol.dmr.DMRNodeLocation;
import org.jboss.as.controller.PathAddress;
import org.junit.Assert;
import org.junit.Test;

public class ResourceTypeManagerTest {

    @Test
    public void simpleGraphDMR() {
        ResourceType<DMRNodeLocation> rt1_1 = createResourceTypeDMR("res1.1", "/res1_1=*");
        ResourceType<DMRNodeLocation> rt1_2 = createResourceTypeDMR("res1.2", "/res1_2=*");
        TypeSet<ResourceType<DMRNodeLocation>> set1 = createResourceTypeSetDMR("set1", true,
                rt1_1, rt1_2);

        Map<Name, TypeSet<ResourceType<DMRNodeLocation>>> rTypeSetDmrMap = new HashMap<>();
        rTypeSetDmrMap.put(set1.getName(), set1);

        ResourceTypeManager<DMRNodeLocation> rtm = new ResourceTypeManager<>(rTypeSetDmrMap, null);

        Assert.assertEquals("There should be two types", 2, rtm.getResourceTypesBreadthFirst().size());

        Set<ResourceType<DMRNodeLocation>> roots = rtm.getRootResourceTypes();
        Assert.assertEquals("The two types are root resources", 2, roots.size());
    }

    @Test
    public void simpleParentChildGraphDMR() {
        ResourceType<DMRNodeLocation> rt1_1 = createResourceTypeDMR("res1_1", "/res1_1=*");
        ResourceType<DMRNodeLocation> rt1_2 = createResourceTypeDMR("res1_2", "/res1_2=*");
        TypeSet<ResourceType<DMRNodeLocation>> set1 = createResourceTypeSetDMR("set1", true,
                rt1_1, rt1_2);

        ResourceType<DMRNodeLocation> rt2_1 = createResourceTypeDMR("res2_1", "/res2_1=*",
                rt1_1.getName());
        ResourceType<DMRNodeLocation> rt2_2 = createResourceTypeDMR("res2_2", "/res2_2=*",
                rt1_2.getName());
        TypeSet<ResourceType<DMRNodeLocation>> set2 = createResourceTypeSetDMR("set2", true,
                rt2_1, rt2_2);

        Map<Name, TypeSet<ResourceType<DMRNodeLocation>>> rTypeSetDmrMap = new HashMap<>();
        rTypeSetDmrMap.put(set1.getName(), set1);
        rTypeSetDmrMap.put(set2.getName(), set2);

        ResourceTypeManager<DMRNodeLocation> rtm = new ResourceTypeManager<>(rTypeSetDmrMap, null);

        Assert.assertEquals("There should be 4 types", 4, rtm.getResourceTypesBreadthFirst().size());

        Set<ResourceType<DMRNodeLocation>> roots = rtm.getRootResourceTypes();
        Assert.assertEquals("There are only two types that are root resources", 2, roots.size());
        Assert.assertTrue(roots.contains(rt1_1));
        Assert.assertTrue(roots.contains(rt1_2));

        Set<ResourceType<DMRNodeLocation>> outgoingEdgesOf;
        outgoingEdgesOf = rtm.getParents(rt1_1);
        Assert.assertTrue("Root resource has no parent", outgoingEdgesOf.isEmpty());
        outgoingEdgesOf = rtm.getParents(rt1_2);
        Assert.assertTrue("Root resource has no parent", outgoingEdgesOf.isEmpty());
        outgoingEdgesOf = rtm.getParents(rt2_1);
        Assert.assertTrue(outgoingEdgesOf.iterator().next().equals(rt1_1));
        outgoingEdgesOf = rtm.getParents(rt2_2);
        Assert.assertTrue(outgoingEdgesOf.iterator().next().equals(rt1_2));

        Set<ResourceType<DMRNodeLocation>> incomingEdgesOf;
        incomingEdgesOf = rtm.getChildren(rt1_1);
        Assert.assertEquals(1, incomingEdgesOf.size());
        Assert.assertTrue(incomingEdgesOf.iterator().next().equals(rt2_1));
        incomingEdgesOf = rtm.getChildren(rt1_2);
        Assert.assertEquals(1, incomingEdgesOf.size());
        Assert.assertTrue(incomingEdgesOf.iterator().next().equals(rt2_2));
        incomingEdgesOf = rtm.getChildren(rt2_1);
        Assert.assertTrue("Has no children", incomingEdgesOf.isEmpty());
        incomingEdgesOf = rtm.getChildren(rt2_2);
        Assert.assertTrue("Has no children", incomingEdgesOf.isEmpty());

    }

    @Test
    public void disabledTypesDMR() {
        ResourceType<DMRNodeLocation> rt1_1 = createResourceTypeDMR("res1_1", "/res1_1=*");
        TypeSet<ResourceType<DMRNodeLocation>> set1 = createResourceTypeSetDMR("set1", true,
                rt1_1);

        ResourceType<DMRNodeLocation> rt2_1 = createResourceTypeDMR("res2_1", "/res2_1=*",
                rt1_1.getName());
        TypeSet<ResourceType<DMRNodeLocation>> set2 = createResourceTypeSetDMR("set2", false,
                rt2_1);

        ResourceType<DMRNodeLocation> rt3_1 = createResourceTypeDMR("res3_1", "/res3_1=*",
                rt2_1.getName());
        TypeSet<ResourceType<DMRNodeLocation>> set3 = createResourceTypeSetDMR("set3", true,
                rt3_1);

        ResourceType<DMRNodeLocation> rt4_1 = createResourceTypeDMR("res4_1", "/res4_1=*",
                rt3_1.getName());
        TypeSet<ResourceType<DMRNodeLocation>> set4 = createResourceTypeSetDMR("set4", true,
                rt4_1);

        Map<Name, TypeSet<ResourceType<DMRNodeLocation>>> rTypeSetDmrMap = new HashMap<>();
        rTypeSetDmrMap.put(set1.getName(), set1);
        rTypeSetDmrMap.put(set2.getName(), set2);
        rTypeSetDmrMap.put(set3.getName(), set3);
        rTypeSetDmrMap.put(set4.getName(), set4);

        ResourceTypeManager<DMRNodeLocation> rtm = new ResourceTypeManager<>(rTypeSetDmrMap, null);
        Assert.assertEquals("There should be only 1 non-disabled type", 1, rtm.getResourceTypesBreadthFirst().size());

        Set<ResourceType<DMRNodeLocation>> roots = rtm.getRootResourceTypes();
        Assert.assertEquals("There is only one root type", 1, roots.size());
        Assert.assertTrue(roots.contains(rt1_1));

        Assert.assertTrue("Root resource has no parent", rtm.getParents(rt1_1).isEmpty());
        Assert.assertTrue("Root resource has no enabled children", rtm.getChildren(rt1_1).isEmpty());
    }

    @Test
    public void disabledAllTypesDMR() {
        ResourceType<DMRNodeLocation> rt1_1 = createResourceTypeDMR("res1_1", "/res1_1=*");
        TypeSet<ResourceType<DMRNodeLocation>> set1 = createResourceTypeSetDMR("set1", false,
                rt1_1);

        ResourceType<DMRNodeLocation> rt2_1 = createResourceTypeDMR("res2_1", "/res2_1=*",
                rt1_1.getName());
        TypeSet<ResourceType<DMRNodeLocation>> set2 = createResourceTypeSetDMR("set2", false,
                rt2_1);

        ResourceType<DMRNodeLocation> rt3_1 = createResourceTypeDMR("res3_1", "/res3_1=*",
                rt2_1.getName());
        TypeSet<ResourceType<DMRNodeLocation>> set3 = createResourceTypeSetDMR("set3", true,
                rt3_1);

        ResourceType<DMRNodeLocation> rt4_1 = createResourceTypeDMR("res4_1", "/res4_1=*",
                rt3_1.getName());
        TypeSet<ResourceType<DMRNodeLocation>> set4 = createResourceTypeSetDMR("set4", true,
                rt4_1);

        Map<Name, TypeSet<ResourceType<DMRNodeLocation>>> rTypeSetDmrMap = new HashMap<>();
        rTypeSetDmrMap.put(set1.getName(), set1);
        rTypeSetDmrMap.put(set2.getName(), set2);
        rTypeSetDmrMap.put(set3.getName(), set3);
        rTypeSetDmrMap.put(set4.getName(), set4);

        ResourceTypeManager<DMRNodeLocation> rtm = new ResourceTypeManager<>(rTypeSetDmrMap, null);

        Assert.assertTrue("There should be no enabled types", rtm.getResourceTypesBreadthFirst().isEmpty());
    }

    @Test
    public void deepGraphDMR() {
        ResourceType<DMRNodeLocation> rt1_1 = createResourceTypeDMR("res1_1", "/res1_1=*");
        ResourceType<DMRNodeLocation> rt1_2 = createResourceTypeDMR("res1_2", "/res1_2=*");
        TypeSet<ResourceType<DMRNodeLocation>> set1 = createResourceTypeSetDMR("set1", true,
                rt1_1, rt1_2);

        ResourceType<DMRNodeLocation> rt2_1 = createResourceTypeDMR("res2_1", "/res2_1=*",
                rt1_1.getName());
        ResourceType<DMRNodeLocation> rt2_2 = createResourceTypeDMR("res2_2", "/res2_2=*",
                rt1_2.getName());
        TypeSet<ResourceType<DMRNodeLocation>> set2 = createResourceTypeSetDMR("set2", true,
                rt2_1, rt2_2);

        ResourceType<DMRNodeLocation> rt3_1 = createResourceTypeDMR("res3_1", "/res3_1=*",
                rt2_1.getName());
        ResourceType<DMRNodeLocation> rt3_2 = createResourceTypeDMR("res3_2", "/res3_2=*",
                rt2_2.getName());
        TypeSet<ResourceType<DMRNodeLocation>> set3 = createResourceTypeSetDMR("set3", true,
                rt3_1, rt3_2);

        Map<Name, TypeSet<ResourceType<DMRNodeLocation>>> rTypeSetDmrMap = new HashMap<>();
        rTypeSetDmrMap.put(set1.getName(), set1);
        rTypeSetDmrMap.put(set2.getName(), set2);
        rTypeSetDmrMap.put(set3.getName(), set3);

        ResourceTypeManager<DMRNodeLocation> rtm = new ResourceTypeManager<>(rTypeSetDmrMap, null);
        List<ResourceType<DMRNodeLocation>> resourceTypes = rtm.getResourceTypesBreadthFirst();
        Assert.assertEquals("There should be 6 types", 6, resourceTypes.size());

        Set<ResourceType<DMRNodeLocation>> roots = rtm.getRootResourceTypes();
        Assert.assertEquals("There are only two types that are root resources", 2, roots.size());
        Assert.assertTrue(roots.contains(rt1_1));
        Assert.assertTrue(roots.contains(rt1_2));

        Set<ResourceType<DMRNodeLocation>> outgoingEdgesOf;
        outgoingEdgesOf = rtm.getParents(rt1_1);
        Assert.assertTrue("Root resource has no parent", outgoingEdgesOf.isEmpty());
        outgoingEdgesOf = rtm.getParents(rt1_2);
        Assert.assertTrue("Root resource has no parent", outgoingEdgesOf.isEmpty());
        outgoingEdgesOf = rtm.getParents(rt2_1);
        Assert.assertTrue(outgoingEdgesOf.iterator().next().equals(rt1_1));
        outgoingEdgesOf = rtm.getParents(rt2_2);
        Assert.assertTrue(outgoingEdgesOf.iterator().next().equals(rt1_2));
        outgoingEdgesOf = rtm.getParents(rt3_1);
        Assert.assertTrue(outgoingEdgesOf.iterator().next().equals(rt2_1));
        outgoingEdgesOf = rtm.getParents(rt3_2);
        Assert.assertTrue(outgoingEdgesOf.iterator().next().equals(rt2_2));
    }

    @Test
    public void multiParentGraphDMR() {
        ResourceType<DMRNodeLocation> rt1_1 = createResourceTypeDMR("res1_1", "/res1_1=*");
        ResourceType<DMRNodeLocation> rt1_2 = createResourceTypeDMR("res1_2", "/res1_2=*");
        TypeSet<ResourceType<DMRNodeLocation>> set1 = createResourceTypeSetDMR("set1", true,
                rt1_1, rt1_2);

        ResourceType<DMRNodeLocation> rt2_1 = createResourceTypeDMR("res2_1", "/res2_1=*",
                rt1_1.getName());
        ResourceType<DMRNodeLocation> rt2_2 = createResourceTypeDMR("res2_2", "/res2_2=*",
                rt1_1.getName(), rt1_2.getName());
        TypeSet<ResourceType<DMRNodeLocation>> set2 = createResourceTypeSetDMR("set2", true,
                rt2_1, rt2_2);

        Map<Name, TypeSet<ResourceType<DMRNodeLocation>>> rTypeSetDmrMap = new HashMap<>();
        rTypeSetDmrMap.put(set1.getName(), set1);
        rTypeSetDmrMap.put(set2.getName(), set2);

        ResourceTypeManager<DMRNodeLocation> rtm = new ResourceTypeManager<>(rTypeSetDmrMap, null);

        Assert.assertEquals("There should be 4 types", 4, rtm.getResourceTypesBreadthFirst().size());

        Set<ResourceType<DMRNodeLocation>> roots = rtm.getRootResourceTypes();
        Assert.assertEquals("There are only two types that are root resources", 2, roots.size());
        Assert.assertTrue(roots.contains(rt1_1));
        Assert.assertTrue(roots.contains(rt1_2));

        Set<ResourceType<DMRNodeLocation>> outgoingEdgesOf = rtm.getParents(rt2_1);
        Assert.assertEquals("There is 1 parent", 1, outgoingEdgesOf.size());
        for (ResourceType<DMRNodeLocation> edge : outgoingEdgesOf) {
            Assert.assertTrue(edge.equals(rt1_1));
        }

        outgoingEdgesOf = rtm.getParents(rt2_2);
        Assert.assertEquals("There are 2 parents", 2, outgoingEdgesOf.size());
        for (ResourceType<DMRNodeLocation> edge : outgoingEdgesOf) {
            Assert.assertTrue(edge.equals(rt1_1) || edge.equals(rt1_2));
        }
    }

    @Test
    public void ignoreSetsDMR() {
        ResourceType<DMRNodeLocation> rt1_1 = createResourceTypeDMR("res1.1", "/res1_1=*");
        ResourceType<DMRNodeLocation> rt1_2 = createResourceTypeDMR("res1.2", "/res1_1=*");
        TypeSet<ResourceType<DMRNodeLocation>> set1 = createResourceTypeSetDMR("set1", true,
                rt1_1, rt1_2);

        ResourceType<DMRNodeLocation> rt2_1 = createResourceTypeDMR("res2.1", "/res1_1=*");
        ResourceType<DMRNodeLocation> rt2_2 = createResourceTypeDMR("res2.2", "/res2_2=*");
        TypeSet<ResourceType<DMRNodeLocation>> set2 = createResourceTypeSetDMR("set2", true,
                rt2_1, rt2_2);

        Map<Name, TypeSet<ResourceType<DMRNodeLocation>>> rTypeSetDmrMap = new HashMap<>();
        rTypeSetDmrMap.put(set1.getName(), set1);
        rTypeSetDmrMap.put(set2.getName(), set2);

        ResourceTypeManager<DMRNodeLocation> rtm = new ResourceTypeManager<>(rTypeSetDmrMap,
                Arrays.asList(set1.getName()));

        Assert.assertEquals("There should be two types", 2, rtm.getResourceTypesBreadthFirst().size());

        Set<ResourceType<DMRNodeLocation>> roots = rtm.getRootResourceTypes();
        Assert.assertEquals("The two types are root resources", 2, roots.size());
    }

    private TypeSet<ResourceType<DMRNodeLocation>> createResourceTypeSetDMR(String name,
            boolean enabled, ResourceType<DMRNodeLocation>... types) {
        TypeSetBuilder<ResourceType<DMRNodeLocation>> typeSetBuilder = TypeSet
                .<ResourceType<DMRNodeLocation>> builder() //
                .id(new ID(name)) //
                .name(new Name(name)) //
                .enabled(enabled);

        if (types != null) {
            for (ResourceType<DMRNodeLocation> type : types) {
                typeSetBuilder.type(type);
            }
        }
        return typeSetBuilder.build();
    }

    private ResourceType<DMRNodeLocation> createResourceTypeDMR(String name, String path,
            Name... parents) {
        return ResourceType.<DMRNodeLocation> builder() //
                .id(new ID(name)) //
                .name(new Name(name)) //
                .resourceNameTemplate(name) //
                .location(new DMRNodeLocation(PathAddress.parseCLIStyleAddress(path))) //
                .parents(Arrays.asList(parents)) //
                .build();
    }
}
