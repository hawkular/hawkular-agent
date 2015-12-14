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

import java.util.Iterator;
import java.util.List;

import org.hawkular.agent.monitor.protocol.dmr.DMRLocationResolver;
import org.hawkular.agent.monitor.protocol.dmr.DMRNodeLocation;
import org.junit.Assert;
import org.junit.Test;

public class ResourceManagerTest {
    @Test
    public void testEmptyResourceManager() {
        ResourceManager<DMRNodeLocation> rm = new ResourceManager<>();
        Assert.assertNull(rm.getResource(new ID("foo")));
        Assert.assertTrue(rm.getResourcesBreadthFirst().isEmpty());
        Assert.assertTrue(rm.getRootResources().isEmpty());

    }

    @Test
    public void testResourceManager() {
        ResourceType<DMRNodeLocation> type = ResourceType
                .<DMRNodeLocation> builder().id(new ID("resType")).name(new Name("resTypeName"))
                .location(DMRNodeLocation.empty())
                .build();
        ResourceManager<DMRNodeLocation> rm = new ResourceManager<>();
        Resource<DMRNodeLocation> root1 = Resource
                .<DMRNodeLocation> builder()
                .id(new ID("root1"))
                .name(new Name("root1Name"))
                .location(DMRNodeLocation.empty())
                .type(type)
                .build();
        Resource<DMRNodeLocation> root2 = Resource
                .<DMRNodeLocation> builder()
                .id(new ID("root2")).name(new Name("root2Name")).type(type)
                .location(DMRNodeLocation.empty())
                .build();
        Resource<DMRNodeLocation> child1 = Resource
                .<DMRNodeLocation> builder()
                .id(new ID("child1")).name(new Name("child1Name")).type(type).parent(root1)
                .location(DMRNodeLocation.of("/child=1")).build();
        Resource<DMRNodeLocation> child2 = Resource
                .<DMRNodeLocation> builder()
                .id(new ID("child2")).name(new Name("child2Name")).type(type).parent(root1)
                .location(DMRNodeLocation.of("/child=2")).build();
        Resource<DMRNodeLocation> grandChild1 = Resource
                .<DMRNodeLocation> builder()
                .id(new ID("grand1")).name(new Name("grand1Name")).type(type).parent(child1)
                .location(DMRNodeLocation.of("/child=1/grandchild=1")).build();

        // add root1
        rm.addResource(root1);
        Assert.assertEquals(1, rm.getResourcesBreadthFirst().size());
        Assert.assertTrue(rm.getResourcesBreadthFirst().contains(root1));
        Assert.assertEquals(root1, rm.getResource(root1.getID()));

        Assert.assertEquals(1, rm.getRootResources().size());
        Assert.assertTrue(rm.getRootResources().contains(root1));

        // add child1
        rm.addResource(child1);
        Assert.assertEquals(2, rm.getResourcesBreadthFirst().size());
        Assert.assertTrue(rm.getResourcesBreadthFirst().contains(child1));
        Assert.assertEquals(child1, rm.getResource(child1.getID()));

        // add grandChild1
        rm.addResource(grandChild1);
        Assert.assertEquals(3, rm.getResourcesBreadthFirst().size());
        Assert.assertTrue(rm.getResourcesBreadthFirst().contains(grandChild1));
        Assert.assertEquals(grandChild1, rm.getResource(grandChild1.getID()));

        // add root2
        rm.addResource(root2);
        Assert.assertEquals(4, rm.getResourcesBreadthFirst().size());
        Assert.assertTrue(rm.getResourcesBreadthFirst().contains(root2));
        Assert.assertEquals(root2, rm.getResource(root2.getID()));

        Assert.assertEquals(2, rm.getRootResources().size());
        Assert.assertTrue(rm.getRootResources().contains(root2));

        // add child2
        rm.addResource(child2);
        Assert.assertEquals(5, rm.getResourcesBreadthFirst().size());
        Assert.assertTrue(rm.getResourcesBreadthFirst().contains(child2));
        Assert.assertEquals(child2, rm.getResource(child2.getID()));

        //
        // the tree now looks like:
        //
        //       root1        root2
        //        /  \
        //   child1  child2
        //      |
        //  grandchild1
        //

        Assert.assertEquals(2, rm.getChildren(root1).size());
        Assert.assertTrue(rm.getChildren(root1).contains(child1));
        Assert.assertTrue(rm.getChildren(root1).contains(child2));
        Assert.assertEquals(1, rm.getChildren(child1).size());
        Assert.assertTrue(rm.getChildren(child1).contains(grandChild1));
        Assert.assertEquals(0, rm.getChildren(grandChild1).size());
        Assert.assertEquals(0, rm.getChildren(root2).size());

        Assert.assertEquals(null, rm.getParent(root1));
        Assert.assertEquals(null, rm.getParent(root2));
        Assert.assertEquals(root1, rm.getParent(child1));
        Assert.assertEquals(root1, rm.getParent(child2));
        Assert.assertEquals(child1, rm.getParent(grandChild1));

        // iterate breadth first which should be (assuming roots are done in order)
        // root1 -> child1 -> child2 -> grandchild1 -> root2
        Iterator<Resource<DMRNodeLocation>> bIter = rm.getResourcesBreadthFirst().iterator();
        Assert.assertEquals(root1, bIter.next());
        Assert.assertEquals(child1, bIter.next());
        Assert.assertEquals(child2, bIter.next());
        Assert.assertEquals(grandChild1, bIter.next());
        Assert.assertEquals(root2, bIter.next());
        Assert.assertFalse(bIter.hasNext());

        // remove child2
        List<Resource<DMRNodeLocation>> removed = rm.removeResources(child2.getLocation(), new DMRLocationResolver());
        Assert.assertEquals(1, removed.size());
        Assert.assertEquals(child2, removed.get(0));

        bIter = rm.getResourcesBreadthFirst().iterator();
        Assert.assertEquals(root1, bIter.next());
        Assert.assertEquals(child1, bIter.next());
        Assert.assertEquals(grandChild1, bIter.next());
        Assert.assertEquals(root2, bIter.next());
        Assert.assertFalse(bIter.hasNext());

        // remove child1 and see that it also removed grandchild1
        removed = rm.removeResources(child1.getLocation(), new DMRLocationResolver());
        Assert.assertEquals(2, removed.size());
        Assert.assertTrue(removed.contains(child1));
        Assert.assertTrue(removed.contains(grandChild1));

        bIter = rm.getResourcesBreadthFirst().iterator();
        Assert.assertEquals(root1, bIter.next());
        Assert.assertEquals(root2, bIter.next());
        Assert.assertFalse(bIter.hasNext());
    }
}
