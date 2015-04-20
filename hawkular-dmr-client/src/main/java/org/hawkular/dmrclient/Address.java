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

import java.util.Arrays;
import java.util.List;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 * Identifies a managed resource in the DMR hierarchy.
 *
 * @author John Mazzitelli
 */
public class Address implements Cloneable {
    private ModelNode addressNode;

    /**
     * Returns the root address which is denoted as the flat address string of "/".
     *
     * @return root address
     */
    public static Address root() {
        return new Address();
    }

    /**
     * Parses a flat address string that has the syntax "/type1=name/type2=name/.../typeN=nameN".
     *
     * @param addressStr flat address string
     * @return address
     */
    public static Address parse(String addressStr) {
        Address address = root();

        if (addressStr == null) {
            return address;
        }

        addressStr = addressStr.trim();
        if (addressStr.isEmpty()) {
            return address;
        }

        String[] resources = addressStr.split("/");
        for (String resource : resources) {
            resource = resource.trim();
            if (resource.isEmpty()) {
                continue; // skip empty string - its usually due to first char in address being "/"
            }
            String[] typeAndName = resource.split("=");
            String type = typeAndName[0].trim();
            String name = (typeAndName.length == 2) ? typeAndName[1].trim() : "";
            address.add(type, name);
        }

        return address;
    }

    /**
     * Obtains the address from the given ModelNode which is assumed to be a property list that
     * contains all the address parts and only the address parts.
     *
     * @param node address node
     * @return the address
     */
    public static Address fromModelNode(ModelNode node) {
        // Rather than just store node as this.addressNode, we want to make sure it can be used as a valid address.
        // This also builds our own instance of ModelNode rather than use the one the caller gave us.
        Address address = Address.root();

        // if the node is not defined, this simply represents the root address
        if (!node.isDefined()) {
            return address;
        }

        try {
            List<Property> addressList = node.asPropertyList();
            for (Property addressProperty : addressList) {
                String resourceType = addressProperty.getName();
                String resourceName = addressProperty.getValue().asString();
                address.add(resourceType, resourceName);
            }
            return address;
        } catch (Exception e) {
            throw new IllegalArgumentException("Node cannot be used as an address: " + node.toJSONString(true));
        }
    }

    /**
     * Obtains an address property list from the given ModelNode wrapper. The given haystack must have a
     * key whose value is the same as needle. The value of that named property must itself be a property list
     * containing all address parts (and only address parts).
     *
     * @param haystack the wrapper ModelNode that contains a property whose value is an address property list.
     * @param needle the name of the property in the given wrapper ModelNode whose value is the address property list.
     * @return the found address
     *
     * @throws IllegalArgumentException there is no address property list in the wrapper node with the given name.
     *
     * @see #fromModelNode(ModelNode)
     */

    public static Address fromModelNodeWrapper(ModelNode haystack, String needle) {
        if (haystack.hasDefined(needle)) {
            return fromModelNode(haystack.get(needle));
        }

        throw new IllegalArgumentException("There is no address under the key [" + needle + "] in the node: "
                + haystack.toJSONString(true));
    }

    /**
     * Creates a {@link #root()} address.
     */
    public Address() {
        addressNode = new ModelNode();
    }

    /**
     * Creates an address with the given parts. See {@link #add(String...)}.
     *
     * @param addressParts the type/name parts of an address.
     */
    public Address(String... addressParts) {
        this();
        add(addressParts);
    }

    /**
     * Returns this address as a ModelNode.
     *
     * @return the address ModelNode representation
     */
    public ModelNode getAddressNode() {
        return addressNode;
    }

    /**
     * Given an address, this appends the given address parts to it. This lets you build up
     * addresses in a step-wise fashion.
     *
     * @param addressParts new address parts to add to the address.
     *
     * @return this address (which now has the new address parts appended).
     */
    public Address add(String... addressParts) {
        if (addressParts != null) {
            if ((addressParts.length % 2) != 0) {
                throw new IllegalArgumentException("address is incomplete: " + Arrays.toString(addressParts));
            }

            if (addressParts.length > 0) {
                for (int i = 0; i < addressParts.length; i += 2) {
                    addressNode.add(addressParts[i], addressParts[i + 1]);
                }
            }
        }

        return this;
    }

    /**
     * Adds a single level to this address. For example, if this address is currently "/a=b"
     * and this method is called with type=x and name=y this address will now be "/a=b/x=y".
     *
     * @param type the new address part's type
     * @param name the new address part's name
     *
     * @return this address with the new address part added to it
     */
    public Address add(String type, String name) {
        addressNode.add(type, name);
        return this;
    }

    /**
     * Returns the address as a flattened string that is compatible with
     * the DMR CLI address paths.
     *
     * For example, an Address whose ModelNode representation is:
     *
     *    [
     *     ("one" => "two"),
     *     ("three" => "four")
     *    ]
     *
     * will have a flat string of
     *
     *    /one=two/three=four
     *
     * @return flattened address path string
     */
    public String toAddressPathString() {
        if (!addressNode.isDefined()) {
            return "/";
        }

        StringBuilder str = new StringBuilder();
        List<Property> parts = addressNode.asPropertyList();
        for (Property part : parts) {
            String name = part.getName();
            String value = part.getValue().asString();
            str.append("/")
                    .append(name)
                    .append("=")
                    .append(value);
        }
        return str.toString();
    }

    @Override
    public Address clone() {
        Address clone = new Address();
        clone.addressNode = addressNode.clone();
        return clone;
    }

    @Override
    public String toString() {
        return addressNode.asString();
    }

    @Override
    public int hashCode() {
        return addressNode.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof Address)) {
            return false;
        }

        return this.addressNode.equals(((Address) obj).addressNode);
    }
}