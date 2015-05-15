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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.ResourceTypeDMR;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.ResourceTypeSetDMR;
import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.junit.Assert;
import org.junit.Test;

public class ResourceTypeManagerTest {

    @Test
    public void simpleGraphDMR() {
        ResourceTypeDMR rt1_1 = createResourceTypeDMR("res1.1", "/res1.1");
        ResourceTypeDMR rt1_2 = createResourceTypeDMR("res1.2", "/res1.1");
        ResourceTypeSetDMR set1 = createResourceTypeSetDMR("set1", true, rt1_1, rt1_2);

        Map<String, ResourceTypeSetDMR> resourceTypeSetDmrMap = new HashMap<>();
        resourceTypeSetDmrMap.put(set1.name, set1);

        ResourceTypeManager rtm = new ResourceTypeManager(resourceTypeSetDmrMap);
        DirectedGraph<ResourceTypeDMR, DefaultEdge> graph = rtm.getResourceTypesGraphDMR();

        Assert.assertNotNull(graph);
        Assert.assertTrue("There is no parent/child hierarchy - no edges yet", graph.edgeSet().isEmpty());
        Assert.assertEquals("There should be two types", 2, graph.vertexSet().size());

        Set<ResourceTypeDMR> roots = rtm.getRootResourceTypesDMR();
        Assert.assertEquals("The two types are root resources", 2, roots.size());
    }

    @Test
    public void simpleParentChildGraphDMR() {
        ResourceTypeDMR rt1_1 = createResourceTypeDMR("res1_1", "/res1_1");
        ResourceTypeDMR rt1_2 = createResourceTypeDMR("res1_2", "/res1_1");
        ResourceTypeSetDMR set1 = createResourceTypeSetDMR("set1", true, rt1_1, rt1_2);

        ResourceTypeDMR rt2_1 = createResourceTypeDMR("res2_1", "/res2_1", rt1_1.name);
        ResourceTypeDMR rt2_2 = createResourceTypeDMR("res2_2", "/res2_1", rt1_2.name);
        ResourceTypeSetDMR set2 = createResourceTypeSetDMR("set2", true, rt2_1, rt2_2);

        Map<String, ResourceTypeSetDMR> resourceTypeSetDmrMap = new HashMap<>();
        resourceTypeSetDmrMap.put(set1.name, set1);
        resourceTypeSetDmrMap.put(set2.name, set2);

        ResourceTypeManager rtm = new ResourceTypeManager(resourceTypeSetDmrMap);
        DirectedGraph<ResourceTypeDMR, DefaultEdge> graph = rtm.getResourceTypesGraphDMR();

        Assert.assertNotNull(graph);
        Assert.assertFalse("There is parent/child hierarchy - should have edges", graph.edgeSet().isEmpty());
        Assert.assertEquals("There should be 4 types", 4, graph.vertexSet().size());

        Set<ResourceTypeDMR> roots = rtm.getRootResourceTypesDMR();
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
        ResourceTypeDMR rt1_1 = createResourceTypeDMR("res1_1", "/res1_1");
        ResourceTypeSetDMR set1 = createResourceTypeSetDMR("set1", true, rt1_1);

        ResourceTypeDMR rt2_1 = createResourceTypeDMR("res2_1", "/res2_1", rt1_1.name);
        ResourceTypeSetDMR set2 = createResourceTypeSetDMR("set2", false, rt2_1);

        ResourceTypeDMR rt3_1 = createResourceTypeDMR("res3_1", "/res3_1", rt2_1.name);
        ResourceTypeSetDMR set3 = createResourceTypeSetDMR("set3", true, rt3_1);

        ResourceTypeDMR rt4_1 = createResourceTypeDMR("res4_1", "/res4_1", rt3_1.name);
        ResourceTypeSetDMR set4 = createResourceTypeSetDMR("set4", true, rt4_1);

        Map<String, ResourceTypeSetDMR> resourceTypeSetDmrMap = new HashMap<>();
        resourceTypeSetDmrMap.put(set1.name, set1);
        resourceTypeSetDmrMap.put(set2.name, set2);
        resourceTypeSetDmrMap.put(set3.name, set3);
        resourceTypeSetDmrMap.put(set4.name, set4);

        ResourceTypeManager rtm = new ResourceTypeManager(resourceTypeSetDmrMap);
        DirectedGraph<ResourceTypeDMR, DefaultEdge> graph = rtm.getResourceTypesGraphDMR();

        Assert.assertNotNull(graph);
        Assert.assertEquals("There should be only 1 non-disabled type", 1, graph.vertexSet().size());

        Set<ResourceTypeDMR> roots = rtm.getRootResourceTypesDMR();
        Assert.assertEquals("There is only one root type", 1, roots.size());
        Assert.assertTrue(roots.contains(rt1_1));

        Assert.assertTrue("Root resource has no parent", graph.outgoingEdgesOf(rt1_1).isEmpty());
        Assert.assertTrue("Root resource has no enabled children", graph.incomingEdgesOf(rt1_1).isEmpty());
    }

    @Test
    public void deepGraphDMR() {
        ResourceTypeDMR rt1_1 = createResourceTypeDMR("res1_1", "/res1_1");
        ResourceTypeDMR rt1_2 = createResourceTypeDMR("res1_2", "/res1_1");
        ResourceTypeSetDMR set1 = createResourceTypeSetDMR("set1", true, rt1_1, rt1_2);

        ResourceTypeDMR rt2_1 = createResourceTypeDMR("res2_1", "/res2_1", rt1_1.name);
        ResourceTypeDMR rt2_2 = createResourceTypeDMR("res2_2", "/res2_1", rt1_2.name);
        ResourceTypeSetDMR set2 = createResourceTypeSetDMR("set2", true, rt2_1, rt2_2);

        ResourceTypeDMR rt3_1 = createResourceTypeDMR("res3_1", "/res3_1", rt2_1.name);
        ResourceTypeDMR rt3_2 = createResourceTypeDMR("res3_2", "/res3_1", rt2_2.name);
        ResourceTypeSetDMR set3 = createResourceTypeSetDMR("set3", true, rt3_1, rt3_2);

        Map<String, ResourceTypeSetDMR> resourceTypeSetDmrMap = new HashMap<>();
        resourceTypeSetDmrMap.put(set1.name, set1);
        resourceTypeSetDmrMap.put(set2.name, set2);
        resourceTypeSetDmrMap.put(set3.name, set3);

        ResourceTypeManager rtm = new ResourceTypeManager(resourceTypeSetDmrMap);
        DirectedGraph<ResourceTypeDMR, DefaultEdge> graph = rtm.getResourceTypesGraphDMR();

        Assert.assertNotNull(graph);
        Assert.assertFalse("There is parent/child hierarchy - should have edges", graph.edgeSet().isEmpty());
        Assert.assertEquals("There should be 6 types", 6, graph.vertexSet().size());

        Set<ResourceTypeDMR> roots = rtm.getRootResourceTypesDMR();
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
        ResourceTypeDMR rt1_1 = createResourceTypeDMR("res1_1", "/res1_1");
        ResourceTypeDMR rt1_2 = createResourceTypeDMR("res1_2", "/res1_1");
        ResourceTypeSetDMR set1 = createResourceTypeSetDMR("set1", true, rt1_1, rt1_2);

        ResourceTypeDMR rt2_1 = createResourceTypeDMR("res2_1", "/res2_1", rt1_1.name);
        ResourceTypeDMR rt2_2 = createResourceTypeDMR("res2_2", "/res2_1", rt1_1.name, rt1_2.name);
        ResourceTypeSetDMR set2 = createResourceTypeSetDMR("set2", true, rt2_1, rt2_2);

        Map<String, ResourceTypeSetDMR> resourceTypeSetDmrMap = new HashMap<>();
        resourceTypeSetDmrMap.put(set1.name, set1);
        resourceTypeSetDmrMap.put(set2.name, set2);

        ResourceTypeManager rtm = new ResourceTypeManager(resourceTypeSetDmrMap);
        DirectedGraph<ResourceTypeDMR, DefaultEdge> graph = rtm.getResourceTypesGraphDMR();

        Assert.assertNotNull(graph);
        Assert.assertFalse("There is parent/child hierarchy - should have edges", graph.edgeSet().isEmpty());
        Assert.assertEquals("There should be 4 types", 4, graph.vertexSet().size());

        Set<ResourceTypeDMR> roots = rtm.getRootResourceTypesDMR();
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

    private ResourceTypeSetDMR createResourceTypeSetDMR(String name, boolean enabled, ResourceTypeDMR... types) {
        ResourceTypeSetDMR set = new ResourceTypeSetDMR();
        set.name = name;
        set.enabled = enabled;
        set.resourceTypeDmrMap = new HashMap<String, ResourceTypeDMR>();

        if (types != null) {
            for (ResourceTypeDMR type : types) {
                set.resourceTypeDmrMap.put(type.name, type);
            }
        }
        return set;
    }

    private ResourceTypeDMR createResourceTypeDMR(String name, String path, String... parents) {
        ResourceTypeDMR rt = new ResourceTypeDMR();
        rt.name = name;
        rt.path = path;
        rt.parents = new ArrayList<String>();
        rt.metricSets = new ArrayList<String>();
        rt.availSets = new ArrayList<String>();

        if (parents != null) {
            for (String parent : parents) {
                rt.parents.add(parent);
            }
        }
        return rt;
    }
}
