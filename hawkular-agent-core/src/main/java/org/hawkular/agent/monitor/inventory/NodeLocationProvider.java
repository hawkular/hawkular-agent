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

/**
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 *
 * @param <L> the type of the protocol specific location, typically a subclass of {@link NodeLocation}
 */
public abstract class NodeLocationProvider<L> extends NamedObject {

    public static class Builder<This extends Builder<?, L>, L> {

        protected ID id;
        protected Name name;
        protected L location;

        protected Builder() {
            super();
        }

        /**
         * Creates a builder with the given object as a starting template.
         * You can use this to clone an object as well as build one
         * that looks similar to the given template object.
         *
         * @param template start with the data found in the given template
         */
        protected Builder(NodeLocationProvider<L> template) {
            id(template.getID());
            name(template.getName());
            location(template.getLocation());
        }

        public This id(ID id) {
            this.id = id;
            return getThis();
        }

        public This name(Name name) {
            this.name = name;
            return getThis();
        }

        public This location(L location) {
            this.location = location;
            return getThis();
        }

        /**
         * @return
         */
        @SuppressWarnings("unchecked")
        private This getThis() {
            return (This) this;
        }
    }

    private final L location;

    public NodeLocationProvider(ID id, Name name, L location) {
        super(id, name);
        if (location == null) {
            throw new IllegalArgumentException("Cannot create a new ["+ getClass().getName() +"] with a null location");
        }
        this.location = location;
    }

    public L getLocation() {
        return location;
    }

}
