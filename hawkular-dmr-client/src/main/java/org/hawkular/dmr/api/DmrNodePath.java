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
package org.hawkular.dmr.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class DmrNodePath {
    public static class Builder {

        private List<DmrNodePathSegment> segments = new ArrayList<>();

        public Builder any(String type) {
            segments.add(new DmrNodePathSegment(type, ANY_NAME));
            return this;
        }

        public DmrNodePath build() {
            return new DmrNodePath(Collections.unmodifiableList(segments));
        }

        public Builder segment(String type, String name) {
            segments.add(new DmrNodePathSegment(type, name));
            return this;
        }

        public Builder segments(DmrNodePath path) {
            segments.addAll(path.getSegments());
            return this;
        }

        public Builder segments(List<DmrNodePathSegment> segmentsToAdd, int start, int end) {
            for (int i = start; i < end; i++) {
                this.segments.add(segmentsToAdd.get(i));
            }
            return this;
        }

        public Builder segments(ModelNode address) {
            for (Property prop : address.asPropertyList()) {
                segment(prop.getName(), prop.getValue().asString());
            }
            return this;
        }

        /**
         * @param path
         */
        public Builder segments(String path) {
            StringTokenizer st = new StringTokenizer(path, "/");
            while (st.hasMoreTokens()) {
                String token = st.nextToken();
                int eqPos = token.indexOf('=');
                if (eqPos >= 0) {
                    String key = token.substring(0, eqPos);
                    String value = token.substring(eqPos + 1);
                    segment(key, value);
                } else {
                    segment(token, ANY_NAME);
                }
            }
            return this;
        }
    }
    public static class DmrNodePathSegment {

        private final String name;

        private final String type;

        private DmrNodePathSegment(String type, String name) {
            super();
            if (type == null) {
                throw new IllegalArgumentException("Cannot create a "+ getClass().getName() +" with a null type.");
            }
            if (name == null) {
                throw new IllegalArgumentException("Cannot create a "+ getClass().getName() +" with a null type.");
            }
            this.type = type;
            this.name = name;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            DmrNodePathSegment other = (DmrNodePathSegment) obj;
            if (name == null) {
                if (other.name != null)
                    return false;
            } else if (!name.equals(other.name))
                return false;
            if (type == null) {
                if (other.type != null)
                    return false;
            } else if (!type.equals(other.type))
                return false;
            return true;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            result = prime * result + ((type == null) ? 0 : type.hashCode());
            return result;
        }

        public boolean matches(DmrNodePathSegment other) {
            return this.type.equals(other.type) && (this.name.equals(other.name) || this.name.equals(ANY_NAME));
        }

        @Override
        public String toString() {
            return type +"="+ name;
        }
    }
    public static final String ANY_NAME = "*";

    private static final DmrNodePath ROOT_PATH = new DmrNodePath(Collections.emptyList());

    public static DmrNodePath absolutize(DmrNodePath base, DmrNodePath relativePath) {
        if (base == null) {
            return relativePath;
        } else {
            return builder().segments(base).segments(relativePath).build();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static DmrNodePath rootPath() {
        return ROOT_PATH;
    }

    private final List<DmrNodePathSegment> segments;

    private DmrNodePath(List<DmrNodePathSegment> segments) {
        super();
        this.segments = segments;
    }
    public ModelNode asModelNode() {
        ModelNode result = new ModelNode();
        for (DmrNodePathSegment segment : segments) {
            result.add(segment.getType(), segment.getName());
        }
        result.protect();
        return result;
    }

    public DmrNodePathSegment getLastSegment() {
        if (segments.isEmpty()) {
            throw new IndexOutOfBoundsException("There is no last segment in an empty ["+ getClass().getName() +"]");
        }
        return segments.get(segments.size() - 1);
    }

    public List<DmrNodePathSegment> getSegments() {
        return segments;
    }

    public boolean isParentOf(DmrNodePath other) {
        if (this.segments.size() <= other.segments.size()) {
            for (int i = 0; i < this.segments.size(); i++) {
                if (!this.segments.get(i).matches(other.segments.get(i))) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    public boolean matches(DmrNodePath other) {
        if (this.segments.size() == other.segments.size()) {
            for (int i = 0; i < this.segments.size(); i++) {
                if (!this.segments.get(i).matches(other.segments.get(i))) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        for (DmrNodePathSegment segment : segments) {
            result.append('/').append(segment.getType()).append('=').append(segment.getName());
        }
        return result.toString();
    }


}
