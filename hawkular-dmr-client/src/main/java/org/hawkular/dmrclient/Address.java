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

import org.jboss.dmr.ModelNode;

/**
 * Identifies a managed resource.
 *
 * @author John Mazzitelli
 */
public class Address implements Cloneable {
    private ModelNode addressNode;

    public static Address root() {
        return new Address();
    }

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

    public Address() {
        addressNode = new ModelNode();
    }

    public Address(String... addressParts) {
        this();
        add(addressParts);
    }

    public ModelNode getAddressNode() {
        return addressNode;
    }

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

    public Address add(String type, String name) {
        addressNode.add(type, name);
        return this;
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