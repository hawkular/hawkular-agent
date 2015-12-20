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

import java.util.Collection;
import java.util.Iterator;

import org.hawkular.agent.monitor.protocol.dmr.DMRLocationResolver;
import org.hawkular.agent.monitor.protocol.dmr.DMRNodeLocation;
import org.junit.Assert;
import org.junit.Test;

public class ResourceManagerTest {
    @Test
    public void testEmptyResourceManager() {
        ResourceManager<DMRNodeLocation> rm = new ResourceManager<>();
        Assert.assertNull(rm.getResource(new ID("foo")));
        Assert.assertTrue(rm.getRootResources().isEmpty());
        try {
            rm.getResourcesBreadthFirst();
            Assert.fail("Should have failed since we don't have any root resources");
        } catch (IllegalStateException expected) {
        }
    }

    @Test
    public void testReplaceExistingResource() {
        String rootIdString = "root1";
        String childIdString = "child1";
        String grandChildIdString = "grand1";

        ResourceType<DMRNodeLocation> type = ResourceType.<DMRNodeLocation> builder()
                .id(new ID("resType"))
                .name(new Name("resTypeName"))
                .location(DMRNodeLocation.empty())
                .build();
        ResourceManager<DMRNodeLocation> rm = new ResourceManager<>();
        Resource<DMRNodeLocation> root1 = Resource.<DMRNodeLocation> builder()
                .id(new ID(rootIdString))
                .name(new Name("root1Name"))
                .location(DMRNodeLocation.empty())
                .type(type)
                .build();
        Resource<DMRNodeLocation> child1 = Resource.<DMRNodeLocation> builder()
                .id(new ID(childIdString))
                .name(new Name("child1Name"))
                .type(type)
                .parent(root1)
                .location(DMRNodeLocation.of("/child=1"))
                .build();
        Resource<DMRNodeLocation> grandChild1 = Resource.<DMRNodeLocation> builder()
                .id(new ID(grandChildIdString))
                .name(new Name("grand1Name"))
                .type(type)
                .parent(child1)
                .location(DMRNodeLocation.of("/child=1/grandchild=1"))
                .build();

        // add root1
        rm.addResource(root1);
        rm.addResource(child1);
        rm.addResource(grandChild1);

        // make sure our inventory is what we expect: root1 -> child1 -> grandchild1
        Iterator<Resource<DMRNodeLocation>> bIter = rm.getResourcesBreadthFirst().iterator();
        Assert.assertEquals(root1, bIter.next());
        Assert.assertEquals(child1, bIter.next());
        Assert.assertEquals(grandChild1, bIter.next());
        Assert.assertFalse(bIter.hasNext());
        Assert.assertEquals("root1Name", rm.getResource(new ID(rootIdString)).getName().getNameString());
        Assert.assertEquals("child1Name", rm.getResource(new ID(childIdString)).getName().getNameString());
        Assert.assertEquals("grand1Name", rm.getResource(new ID(grandChildIdString)).getName().getNameString());

        // now replace resources (we aren't adding new, we are replacing existing resources)
        Resource<DMRNodeLocation> root1_update = Resource.<DMRNodeLocation> builder()
                .id(new ID(rootIdString))
                .name(new Name("root1NameUPDATE"))
                .location(DMRNodeLocation.empty())
                .type(type)
                .build();
        Resource<DMRNodeLocation> child1_update = Resource.<DMRNodeLocation> builder()
                .id(new ID(childIdString))
                .name(new Name("child1NameUPDATE"))
                .type(type)
                .parent(root1)
                .location(DMRNodeLocation.of("/child=1"))
                .build();
        Resource<DMRNodeLocation> grandChild1_update = Resource.<DMRNodeLocation> builder()
                .id(new ID(grandChildIdString))
                .name(new Name("grand1NameUPDATE"))
                .type(type)
                .parent(child1)
                .location(DMRNodeLocation.of("/child=1/grandchild=1"))
                .build();

        rm.addResource(child1_update);
        rm.addResource(grandChild1_update);
        rm.addResource(root1_update);

        // make sure our inventory is still what we expect: root1 -> child1 -> grandchild1
        bIter = rm.getResourcesBreadthFirst().iterator();
        Assert.assertEquals(root1_update, bIter.next());
        Assert.assertEquals(child1_update, bIter.next());
        Assert.assertEquals(grandChild1_update, bIter.next());
        Assert.assertFalse(bIter.hasNext());

        // the new IDs should all be the same as the old ones
        Assert.assertEquals(root1.getID(), rm.getResource(root1_update.getID()).getID());
        Assert.assertEquals(child1.getID(), rm.getResource(child1_update.getID()).getID());
        Assert.assertEquals(grandChild1.getID(), rm.getResource(grandChild1_update.getID()).getID());

        // but the names should be updated
        Assert.assertEquals("root1NameUPDATE", rm.getResource(new ID(rootIdString)).getName().getNameString());
        Assert.assertEquals("child1NameUPDATE", rm.getResource(new ID(childIdString)).getName().getNameString());
        Assert.assertEquals("grand1NameUPDATE", rm.getResource(new ID(grandChildIdString)).getName().getNameString());
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
        Collection<Resource<DMRNodeLocation>> removed = rm.removeResources(child2.getLocation(),
                new DMRLocationResolver());
        Assert.assertEquals(1, removed.size());
        Assert.assertEquals(child2, removed.iterator().next());

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

    @Test
    public void testRemoveDescendants() {
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
        Resource<DMRNodeLocation> child1 = Resource
                .<DMRNodeLocation> builder()
                .id(new ID("child1")).name(new Name("child1Name")).type(type).parent(root1)
                .location(DMRNodeLocation.of("/child=1")).build();
        Resource<DMRNodeLocation> grandChild1 = Resource
                .<DMRNodeLocation> builder()
                .id(new ID("grand1")).name(new Name("grand1Name")).type(type).parent(child1)
                .location(DMRNodeLocation.of("/child=1/grandchild=1")).build();
        Resource<DMRNodeLocation> greatGrandChild1 = Resource
                .<DMRNodeLocation> builder()
                .id(new ID("greatgrand1")).name(new Name("greatgrand1Name")).type(type).parent(grandChild1)
                .location(DMRNodeLocation.of("/child=1/grandchild=1/greatgrand=1")).build();
        Resource<DMRNodeLocation> grandChild2 = Resource
                .<DMRNodeLocation> builder()
                .id(new ID("grand2")).name(new Name("grand2Name")).type(type).parent(child1)
                .location(DMRNodeLocation.of("/child=1/grandchild=2")).build();
        Resource<DMRNodeLocation> greatGrandChild2 = Resource
                .<DMRNodeLocation> builder()
                .id(new ID("greatgrand2")).name(new Name("greatgrand2Name")).type(type).parent(grandChild2)
                .location(DMRNodeLocation.of("/child=1/grandchild=2/greatgrand=2")).build();

        // add hierarchy
        rm.addResource(root1);
        rm.addResource(child1);
        rm.addResource(grandChild1);
        rm.addResource(greatGrandChild1);
        rm.addResource(grandChild2);
        rm.addResource(greatGrandChild2);
        Assert.assertEquals(6, rm.getResourcesBreadthFirst().size());
        Assert.assertEquals(1, rm.getRootResources().size());

        Assert.assertEquals(1, rm.getChildren(root1).size());
        Assert.assertTrue(rm.getChildren(root1).contains(child1));
        Assert.assertEquals(2, rm.getChildren(child1).size());
        Assert.assertTrue(rm.getChildren(child1).contains(grandChild1));
        Assert.assertTrue(rm.getChildren(child1).contains(grandChild2));
        Assert.assertEquals(1, rm.getChildren(grandChild1).size());
        Assert.assertTrue(rm.getChildren(grandChild1).contains(greatGrandChild1));
        Assert.assertEquals(1, rm.getChildren(grandChild2).size());
        Assert.assertTrue(rm.getChildren(grandChild2).contains(greatGrandChild2));
        Assert.assertEquals(0, rm.getChildren(greatGrandChild1).size());
        Assert.assertEquals(0, rm.getChildren(greatGrandChild2).size());

        Assert.assertEquals(null, rm.getParent(root1));
        Assert.assertEquals(root1, rm.getParent(child1));
        Assert.assertEquals(child1, rm.getParent(grandChild1));
        Assert.assertEquals(child1, rm.getParent(grandChild2));
        Assert.assertEquals(grandChild1, rm.getParent(greatGrandChild1));
        Assert.assertEquals(grandChild2, rm.getParent(greatGrandChild2));

        // iterate breadth first
        Iterator<Resource<DMRNodeLocation>> bIter = rm.getResourcesBreadthFirst().iterator();
        Assert.assertEquals(root1, bIter.next());
        Assert.assertEquals(child1, bIter.next());
        Assert.assertEquals(grandChild1, bIter.next());
        Assert.assertEquals(grandChild2, bIter.next());
        Assert.assertEquals(greatGrandChild1, bIter.next());
        Assert.assertEquals(greatGrandChild2, bIter.next());
        Assert.assertFalse(bIter.hasNext());

        // remove child1 and see that all its descendants are removed too
        Collection<Resource<DMRNodeLocation>> removed = rm.removeResources(child1.getLocation(),
                new DMRLocationResolver());
        Assert.assertEquals(removed.toString(), 5, removed.size());
        Assert.assertTrue(removed.contains(child1));
        Assert.assertTrue(removed.contains(grandChild1));
        Assert.assertTrue(removed.contains(greatGrandChild1));
        Assert.assertTrue(removed.contains(grandChild2));
        Assert.assertTrue(removed.contains(greatGrandChild2));

        // only the root1 is left
        bIter = rm.getResourcesBreadthFirst().iterator();
        Assert.assertEquals(root1, bIter.next());
        Assert.assertFalse(bIter.hasNext());
    }
}
