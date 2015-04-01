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

import org.junit.Test;

public class AddressTest extends Address {
    @Test
    public void testRootAddress() {
        Address addr = Address.root();
        assert addr != null;
        assert addr.equals(Address.root());
        assert addr.toString().equals("undefined");

        Address addr2 = Address.root().add("one", "two");
        assert addr2 != null;
        assert !addr2.equals(addr);
        assert addr2.getAddressNode().asList().get(0).get("one").asString().equals("two");
    }

    @Test
    public void testAddress() {
        Address addr = Address.root().add("one", "two", "three", "four");
        assert addr != null;
        assert addr.getAddressNode().asList().get(0).get("one").asString().equals("two");
        assert addr.getAddressNode().asList().get(1).get("three").asString().equals("four");
    }

    @Test
    public void testClone() throws CloneNotSupportedException {
        Address addr = Address.root().add("one", "two", "three", "four", "five", "six");
        Address addr2 = addr.clone();
        assert addr2 != null;
        assert addr2 != addr; // clone worked, duplicated it, didn't just return the same ref
        assert addr2.equals(addr);
        assert addr2.hashCode() == addr.hashCode();
    }

    @Test
    public void testParse() {
        Address addr = Address.parse("/parent=foo");
        assert addr.getAddressNode().asList().size() == 1;
        assert addr.getAddressNode().asList().get(0).get("parent").asString().equals("foo");

        addr = Address.parse("/parent=foo/child=bar");
        assert addr.getAddressNode().asList().size() == 2;
        assert addr.getAddressNode().asList().get(0).get("parent").asString().equals("foo");
        assert addr.getAddressNode().asList().get(1).get("child").asString().equals("bar");

        addr = Address.parse("/parent=foo/child=bar/"); // trailing / shouldn't matter
        assert addr.getAddressNode().asList().size() == 2;
        assert addr.getAddressNode().asList().get(0).get("parent").asString().equals("foo");
        assert addr.getAddressNode().asList().get(1).get("child").asString().equals("bar");

        addr = Address.parse("parent=foo/child=bar"); // missing leading / shouldn't matter
        assert addr.getAddressNode().asList().size() == 2;
        assert addr.getAddressNode().asList().get(0).get("parent").asString().equals("foo");
        assert addr.getAddressNode().asList().get(1).get("child").asString().equals("bar");

        addr = Address.parse("/a-b-c=x-y-z/a_b_c=x_y_z"); // special chars in names
        assert addr.getAddressNode().asList().size() == 2;
        assert addr.getAddressNode().asList().get(0).get("a-b-c").asString().equals("x-y-z");
        assert addr.getAddressNode().asList().get(1).get("a_b_c").asString().equals("x_y_z");

        addr = Address.parse("/parent"); // partial address
        assert addr.getAddressNode().asList().size() == 1;
        assert addr.getAddressNode().asList().get(0).get("parent").asString().equals("");

        addr = Address.parse("/parent="); // partial address
        assert addr.getAddressNode().asList().size() == 1;
        assert addr.getAddressNode().asList().get(0).get("parent").asString().equals("");

        addr = Address.parse("/parent=/child="); // partial addresses (is this even valid DMR?)
        assert addr.getAddressNode().asList().size() == 2;
        assert addr.getAddressNode().asList().get(0).get("parent").asString().equals("");
        assert addr.getAddressNode().asList().get(1).get("child").asString().equals("");
    }

    @Test
    public void testIncompleteAddress() {
        try {
            Address.root().add("one");
            assert false : "The address is unbalanced and should not have been constructed";
        } catch (IllegalArgumentException expected) {
        }

        try {
            new Address("one");
            assert false : "The address is unbalanced and should not have been constructed";
        } catch (IllegalArgumentException expected) {
        }

        try {
            Address.root().add("one", "two", "three");
            assert false : "The address is unbalanced and should not have been constructed";
        } catch (IllegalArgumentException expected) {
        }

        try {
            new Address("one", "two", "three");
            assert false : "The address is unbalanced and should not have been constructed";
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testToAddressPathString() {
        Address addr = Address.root().add("one", "two");
        assert addr.toAddressPathString().equals("/one=two") : addr.toAddressPathString();

        addr = Address.root().add("one", "two", "three", "four");
        assert addr.toAddressPathString().equals("/one=two/three=four") : addr.toAddressPathString();

        addr = Address.root().add("one", "two", "three", "four", "5", "6");
        assert addr.toAddressPathString().equals("/one=two/three=four/5=6") : addr.toAddressPathString();
    }
}
