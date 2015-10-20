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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.hawkular.agent.monitor.inventory.dmr.DMRResourceType;
import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.junit.Assert;
import org.junit.Test;

public class ResourceTypeManagerTest {

    @Test
    public void simpleGraphDMR() {
        DMRResourceType rt1_1 = createResourceTypeDMR("res1.1", "/res1.1");
        DMRResourceType rt1_2 = createResourceTypeDMR("res1.2", "/res1.2");
        TypeSet<DMRResourceType> set1 = createResourceTypeSetDMR("set1", true, rt1_1, rt1_2);

        Map<Name, TypeSet<DMRResourceType>> rTypeSetDmrMap = new HashMap<>();
        rTypeSetDmrMap.put(set1.getName(), set1);

        ResourceTypeManager<DMRResourceType> rtm = new ResourceTypeManager<>(rTypeSetDmrMap);
        DirectedGraph<DMRResourceType, DefaultEdge> graph = rtm.getResourceTypesGraph();

        Assert.assertNotNull(graph);
        Assert.assertTrue("There is no parent/child hierarchy - no edges yet", graph.edgeSet().isEmpty());
        Assert.assertEquals("There should be two types", 2, graph.vertexSet().size());

        Set<DMRResourceType> roots = rtm.getRootResourceTypes();
        Assert.assertEquals("The two types are root resources", 2, roots.size());
    }

    @Test
    public void simpleParentChildGraphDMR() {
        DMRResourceType rt1_1 = createResourceTypeDMR("res1_1", "/res1_1");
        DMRResourceType rt1_2 = createResourceTypeDMR("res1_2", "/res1_2");
        TypeSet<DMRResourceType> set1 = createResourceTypeSetDMR("set1", true, rt1_1, rt1_2);

        DMRResourceType rt2_1 = createResourceTypeDMR("res2_1", "/res2_1", rt1_1.getName());
        DMRResourceType rt2_2 = createResourceTypeDMR("res2_2", "/res2_2", rt1_2.getName());
        TypeSet<DMRResourceType> set2 = createResourceTypeSetDMR("set2", true, rt2_1, rt2_2);

        Map<Name, TypeSet<DMRResourceType>> rTypeSetDmrMap = new HashMap<>();
        rTypeSetDmrMap.put(set1.getName(), set1);
        rTypeSetDmrMap.put(set2.getName(), set2);

        ResourceTypeManager<DMRResourceType> rtm = new ResourceTypeManager<>(rTypeSetDmrMap);
        DirectedGraph<DMRResourceType, DefaultEdge> graph = rtm.getResourceTypesGraph();

        Assert.assertNotNull(graph);
        Assert.assertFalse("There is parent/child hierarchy - should have edges", graph.edgeSet().isEmpty());
        Assert.assertEquals("There should be 4 types", 4, graph.vertexSet().size());

        Set<DMRResourceType> roots = rtm.getRootResourceTypes();
        Assert.assertEquals("There are only two types that are root resources", 2, roots.size());
        Assert.assertTrue(roots.contains(rt1_1));
        Assert.assertTrue(roots.contains(rt1_2));

        Set<DefaultEdge> outgoingEdgesOf;
        outgoingEdgesOf = graph.outgoingEdgesOf(rt1_1);
        Assert.assertTrue("Root resource has no parent", outgoingEdgesOf.isEmpty());
        outgoingEdgesOf = graph.outgoingEdgesOf(rt1_2);
        Assert.assertTrue("Root resource has no parent", outgoingEdgesOf.isEmpty());
        outgoingEdgesOf = graph.outgoingEdgesOf(rt2_1);
        Assert.assertTrue(graph.getEdgeTarget(outgoingEdgesOf.iterator().next()).equals(rt1_1));
        outgoingEdgesOf = graph.outgoingEdgesOf(rt2_2);
        Assert.assertTrue(graph.getEdgeTarget(outgoingEdgesOf.iterator().next()).equals(rt1_2));

        Set<DefaultEdge> incomingEdgesOf;
        incomingEdgesOf = graph.incomingEdgesOf(rt1_1);
        Assert.assertEquals(1, incomingEdgesOf.size());
        Assert.assertTrue(graph.getEdgeSource(incomingEdgesOf.iterator().next()).equals(rt2_1));
        incomingEdgesOf = graph.incomingEdgesOf(rt1_2);
        Assert.assertEquals(1, incomingEdgesOf.size());
        Assert.assertTrue(graph.getEdgeSource(incomingEdgesOf.iterator().next()).equals(rt2_2));
        incomingEdgesOf = graph.incomingEdgesOf(rt2_1);
        Assert.assertTrue("Has no children", incomingEdgesOf.isEmpty());
        incomingEdgesOf = graph.incomingEdgesOf(rt2_2);
        Assert.assertTrue("Has no children", incomingEdgesOf.isEmpty());

    }

    @Test
    public void disabledTypesDMR() {
        DMRResourceType rt1_1 = createResourceTypeDMR("res1_1", "/res1_1");
        TypeSet<DMRResourceType> set1 = createResourceTypeSetDMR("set1", true, rt1_1);

        DMRResourceType rt2_1 = createResourceTypeDMR("res2_1", "/res2_1", rt1_1.getName());
        TypeSet<DMRResourceType> set2 = createResourceTypeSetDMR("set2", false, rt2_1);

        DMRResourceType rt3_1 = createResourceTypeDMR("res3_1", "/res3_1", rt2_1.getName());
        TypeSet<DMRResourceType> set3 = createResourceTypeSetDMR("set3", true, rt3_1);

        DMRResourceType rt4_1 = createResourceTypeDMR("res4_1", "/res4_1", rt3_1.getName());
        TypeSet<DMRResourceType> set4 = createResourceTypeSetDMR("set4", true, rt4_1);

        Map<Name, TypeSet<DMRResourceType>> rTypeSetDmrMap = new HashMap<>();
        rTypeSetDmrMap.put(set1.getName(), set1);
        rTypeSetDmrMap.put(set2.getName(), set2);
        rTypeSetDmrMap.put(set3.getName(), set3);
        rTypeSetDmrMap.put(set4.getName(), set4);

        ResourceTypeManager<DMRResourceType> rtm = new ResourceTypeManager<>(rTypeSetDmrMap);
        DirectedGraph<DMRResourceType, DefaultEdge> graph = rtm.getResourceTypesGraph();

        Assert.assertNotNull(graph);
        Assert.assertEquals("There should be only 1 non-disabled type", 1, graph.vertexSet().size());

        Set<DMRResourceType> roots = rtm.getRootResourceTypes();
        Assert.assertEquals("There is only one root type", 1, roots.size());
        Assert.assertTrue(roots.contains(rt1_1));

        Assert.assertTrue("Root resource has no parent", graph.outgoingEdgesOf(rt1_1).isEmpty());
        Assert.assertTrue("Root resource has no enabled children", graph.incomingEdgesOf(rt1_1).isEmpty());
    }

    @Test
    public void disabledAllTypesDMR() {
        DMRResourceType rt1_1 = createResourceTypeDMR("res1_1", "/res1_1");
        TypeSet<DMRResourceType> set1 = createResourceTypeSetDMR("set1", false, rt1_1);

        DMRResourceType rt2_1 = createResourceTypeDMR("res2_1", "/res2_1", rt1_1.getName());
        TypeSet<DMRResourceType> set2 = createResourceTypeSetDMR("set2", false, rt2_1);

        DMRResourceType rt3_1 = createResourceTypeDMR("res3_1", "/res3_1", rt2_1.getName());
        TypeSet<DMRResourceType> set3 = createResourceTypeSetDMR("set3", true, rt3_1);

        DMRResourceType rt4_1 = createResourceTypeDMR("res4_1", "/res4_1", rt3_1.getName());
        TypeSet<DMRResourceType> set4 = createResourceTypeSetDMR("set4", true, rt4_1);

        Map<Name, TypeSet<DMRResourceType>> rTypeSetDmrMap = new HashMap<>();
        rTypeSetDmrMap.put(set1.getName(), set1);
        rTypeSetDmrMap.put(set2.getName(), set2);
        rTypeSetDmrMap.put(set3.getName(), set3);
        rTypeSetDmrMap.put(set4.getName(), set4);

        ResourceTypeManager<DMRResourceType> rtm = new ResourceTypeManager<>(rTypeSetDmrMap);
        DirectedGraph<DMRResourceType, DefaultEdge> graph = rtm.getResourceTypesGraph();

        Assert.assertNotNull(graph);
        Assert.assertTrue("There should be no enabled types", graph.vertexSet().isEmpty());
    }

    @Test
    public void deepGraphDMR() {
        DMRResourceType rt1_1 = createResourceTypeDMR("res1_1", "/res1_1");
        DMRResourceType rt1_2 = createResourceTypeDMR("res1_2", "/res1_2");
        TypeSet<DMRResourceType> set1 = createResourceTypeSetDMR("set1", true, rt1_1, rt1_2);

        DMRResourceType rt2_1 = createResourceTypeDMR("res2_1", "/res2_1", rt1_1.getName());
        DMRResourceType rt2_2 = createResourceTypeDMR("res2_2", "/res2_2", rt1_2.getName());
        TypeSet<DMRResourceType> set2 = createResourceTypeSetDMR("set2", true, rt2_1, rt2_2);

        DMRResourceType rt3_1 = createResourceTypeDMR("res3_1", "/res3_1", rt2_1.getName());
        DMRResourceType rt3_2 = createResourceTypeDMR("res3_2", "/res3_2", rt2_2.getName());
        TypeSet<DMRResourceType> set3 = createResourceTypeSetDMR("set3", true, rt3_1, rt3_2);

        Map<Name, TypeSet<DMRResourceType>> rTypeSetDmrMap = new HashMap<>();
        rTypeSetDmrMap.put(set1.getName(), set1);
        rTypeSetDmrMap.put(set2.getName(), set2);
        rTypeSetDmrMap.put(set3.getName(), set3);

        ResourceTypeManager<DMRResourceType> rtm = new ResourceTypeManager<>(rTypeSetDmrMap);
        DirectedGraph<DMRResourceType, DefaultEdge> graph = rtm.getResourceTypesGraph();

        Assert.assertNotNull(graph);
        Assert.assertFalse("There is parent/child hierarchy - should have edges", graph.edgeSet().isEmpty());
        Assert.assertEquals("There should be 6 types", 6, graph.vertexSet().size());

        Set<DMRResourceType> roots = rtm.getRootResourceTypes();
        Assert.assertEquals("There are only two types that are root resources", 2, roots.size());
        Assert.assertTrue(roots.contains(rt1_1));
        Assert.assertTrue(roots.contains(rt1_2));

        Set<DefaultEdge> outgoingEdgesOf;
        outgoingEdgesOf = graph.outgoingEdgesOf(rt1_1);
        Assert.assertTrue("Root resource has no parent", outgoingEdgesOf.isEmpty());
        outgoingEdgesOf = graph.outgoingEdgesOf(rt1_2);
        Assert.assertTrue("Root resource has no parent", outgoingEdgesOf.isEmpty());
        outgoingEdgesOf = graph.outgoingEdgesOf(rt2_1);
        Assert.assertTrue(graph.getEdgeTarget(outgoingEdgesOf.iterator().next()).equals(rt1_1));
        outgoingEdgesOf = graph.outgoingEdgesOf(rt2_2);
        Assert.assertTrue(graph.getEdgeTarget(outgoingEdgesOf.iterator().next()).equals(rt1_2));
        outgoingEdgesOf = graph.outgoingEdgesOf(rt3_1);
        Assert.assertTrue(graph.getEdgeTarget(outgoingEdgesOf.iterator().next()).equals(rt2_1));
        outgoingEdgesOf = graph.outgoingEdgesOf(rt3_2);
        Assert.assertTrue(graph.getEdgeTarget(outgoingEdgesOf.iterator().next()).equals(rt2_2));
    }

    @Test
    public void multiParentGraphDMR() {
        DMRResourceType rt1_1 = createResourceTypeDMR("res1_1", "/res1_1");
        DMRResourceType rt1_2 = createResourceTypeDMR("res1_2", "/res1_2");
        TypeSet<DMRResourceType> set1 = createResourceTypeSetDMR("set1", true, rt1_1, rt1_2);

        DMRResourceType rt2_1 = createResourceTypeDMR("res2_1", "/res2_1", rt1_1.getName());
        DMRResourceType rt2_2 = createResourceTypeDMR("res2_2", "/res2_2", rt1_1.getName(), rt1_2.getName());
        TypeSet<DMRResourceType> set2 = createResourceTypeSetDMR("set2", true, rt2_1, rt2_2);

        Map<Name, TypeSet<DMRResourceType>> rTypeSetDmrMap = new HashMap<>();
        rTypeSetDmrMap.put(set1.getName(), set1);
        rTypeSetDmrMap.put(set2.getName(), set2);

        ResourceTypeManager<DMRResourceType> rtm = new ResourceTypeManager<>(rTypeSetDmrMap);
        DirectedGraph<DMRResourceType, DefaultEdge> graph = rtm.getResourceTypesGraph();

        Assert.assertNotNull(graph);
        Assert.assertFalse("There is parent/child hierarchy - should have edges", graph.edgeSet().isEmpty());
        Assert.assertEquals("There should be 4 types", 4, graph.vertexSet().size());

        Set<DMRResourceType> roots = rtm.getRootResourceTypes();
        Assert.assertEquals("There are only two types that are root resources", 2, roots.size());
        Assert.assertTrue(roots.contains(rt1_1));
        Assert.assertTrue(roots.contains(rt1_2));

        Set<DefaultEdge> outgoingEdgesOf = graph.outgoingEdgesOf(rt2_1);
        Assert.assertEquals("There is 1 parent", 1, outgoingEdgesOf.size());
        for (DefaultEdge edge : outgoingEdgesOf) {
            Assert.assertTrue(graph.getEdgeTarget(edge).equals(rt1_1));
        }

        outgoingEdgesOf = graph.outgoingEdgesOf(rt2_2);
        Assert.assertEquals("There are 2 parents", 2, outgoingEdgesOf.size());
        for (DefaultEdge edge : outgoingEdgesOf) {
            Assert.assertTrue(graph.getEdgeTarget(edge).equals(rt1_1) || graph.getEdgeTarget(edge).equals(rt1_2));
        }
    }

    @Test
    public void ignoreSetsDMR() {
        DMRResourceType rt1_1 = createResourceTypeDMR("res1.1", "/res1.1");
        DMRResourceType rt1_2 = createResourceTypeDMR("res1.2", "/res1.1");
        TypeSet<DMRResourceType> set1 = createResourceTypeSetDMR("set1", true, rt1_1, rt1_2);

        DMRResourceType rt2_1 = createResourceTypeDMR("res2.1", "/res1.1");
        DMRResourceType rt2_2 = createResourceTypeDMR("res2.2", "/res2.2");
        TypeSet<DMRResourceType> set2 = createResourceTypeSetDMR("set2", true, rt2_1, rt2_2);

        Map<Name, TypeSet<DMRResourceType>> rTypeSetDmrMap = new HashMap<>();
        rTypeSetDmrMap.put(set1.getName(), set1);
        rTypeSetDmrMap.put(set2.getName(), set2);

        ResourceTypeManager<DMRResourceType> rtm = new ResourceTypeManager<>(rTypeSetDmrMap,
                Arrays.asList(set1.getName()));
        DirectedGraph<DMRResourceType, DefaultEdge> graph = rtm.getResourceTypesGraph();

        Assert.assertNotNull(graph);
        Assert.assertTrue("There is no parent/child hierarchy - no edges yet", graph.edgeSet().isEmpty());
        Assert.assertEquals("There should be two types", 2, graph.vertexSet().size());

        Set<DMRResourceType> roots = rtm.getRootResourceTypes();
        Assert.assertEquals("The two types are root resources", 2, roots.size());
    }

    private TypeSet<DMRResourceType> createResourceTypeSetDMR(String name, boolean enabled, DMRResourceType... types) {
        TypeSet<DMRResourceType> set = new TypeSet<>(new ID(name), new Name(name));
        set.setEnabled(enabled);
        set.setResourceTypeMap(new HashMap<Name, DMRResourceType>());

        if (types != null) {
            for (DMRResourceType type : types) {
                set.getTypeMap().put(type.getName(), type);
            }
        }
        return set;
    }

    private DMRResourceType createResourceTypeDMR(String name, String path, Name... parents) {
        DMRResourceType rt = new DMRResourceType(new ID(name), new Name(name));
        rt.setResourceNameTemplate(name);
        rt.setPath(path);
        rt.setParents(new ArrayList<Name>());
        rt.setMetricSets(new ArrayList<Name>());
        rt.setAvailSets(new ArrayList<Name>());

        if (parents != null) {
            for (Name parent : parents) {
                rt.getParents().add(parent);
            }
        }
        return rt;
    }
}
