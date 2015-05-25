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

import org.junit.Assert;
import org.junit.Test;

public class NamedObjectTest {

    @Test
    public void testHashCodeAndEquals() {
        String id;
        String name;
        TestNamedObject tno1;
        TestNamedObject tno2;

        try {
            new TestNamedObject((String) null, (String) null);
            Assert.fail("Should not be able to pass in null");
        } catch (Exception expected) {
        }
        try {
            new TestNamedObject((ID) null, (Name) null);
            Assert.fail("Should not be able to pass in null");
        } catch (Exception expected) {
        }

        tno1 = new TestNamedObject(ID.NULL_ID, new Name(null));
        tno2 = new TestNamedObject(new ID(null), new Name(null));
        Assert.assertEquals(tno1.hashCode(), tno2.hashCode());
        Assert.assertEquals(tno1, tno2);

        tno1 = new TestNamedObject(new ID("testid"), new Name(null));
        tno2 = new TestNamedObject(new ID("testid"), new Name(null));
        Assert.assertEquals(tno1.hashCode(), tno2.hashCode());
        Assert.assertEquals(tno1, tno2);

        tno1 = new TestNamedObject(ID.NULL_ID, new Name("testname"));
        tno2 = new TestNamedObject(new ID(null), new Name("testname"));
        Assert.assertEquals(tno1.hashCode(), tno2.hashCode());
        Assert.assertEquals(tno1, tno2);

        tno1 = new TestNamedObject(new ID("testid"), new Name("testname"));
        tno2 = new TestNamedObject(new ID("testid"), new Name("testname"));
        Assert.assertEquals(tno1.hashCode(), tno2.hashCode());
        Assert.assertEquals(tno1, tno2);

        // same ID but different name should be equal
        tno1 = new TestNamedObject(new ID("testid"), new Name("testname1"));
        tno2 = new TestNamedObject(new ID("testid"), new Name("testname2"));
        Assert.assertEquals(tno1.hashCode(), tno2.hashCode());
        Assert.assertEquals(tno1, tno2);

        // same name but different ID should not be equal
        tno1 = new TestNamedObject(new ID("testid1"), new Name("testname"));
        tno2 = new TestNamedObject(new ID("testid2"), new Name("testname"));
        Assert.assertNotEquals(tno1.hashCode(), tno2.hashCode());
        Assert.assertNotEquals(tno1, tno2);

        // same name but one has an ID and the other doesn't so they should not be equal
        tno1 = new TestNamedObject(new ID(null), new Name("testname"));
        tno2 = new TestNamedObject(new ID("testid"), new Name("testname"));
        Assert.assertNotEquals(tno1.hashCode(), tno2.hashCode());
        Assert.assertNotEquals(tno1, tno2);
        Assert.assertNotEquals(tno2, tno1);

        // same ID but one has a name and other doesn't; since IDs are equal so objects should be equal
        tno1 = new TestNamedObject(new ID("testid"), new Name(null));
        tno2 = new TestNamedObject(new ID("testid"), new Name("testname"));
        Assert.assertEquals(tno1.hashCode(), tno2.hashCode());
        Assert.assertEquals(tno1, tno2);
        Assert.assertEquals(tno2, tno1);

        // just some silly tests to make sure they don't equate to objects that aren't even of their type
        Assert.assertFalse(new ID(null).equals(null));
        Assert.assertFalse(new Name(null).equals(null));
        Assert.assertFalse(new ID("test").equals("test"));
        Assert.assertFalse(new Name("test").equals("test"));
        Assert.assertFalse(new TestNamedObject("test", "test").equals(null));
        Assert.assertFalse(new TestNamedObject("test", "test").equals("test"));
    }

    private class TestNamedObject extends NamedObject {
        public TestNamedObject(String id, String name) {
            super(id, name);
        }

        public TestNamedObject(ID id, Name name) {
            super(id, name);
        }
    }
}
