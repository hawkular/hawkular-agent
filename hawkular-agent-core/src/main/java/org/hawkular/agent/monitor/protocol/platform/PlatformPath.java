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
package org.hawkular.agent.monitor.protocol.platform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class PlatformPath {
    public static class Builder {

        private List<PathSegment> segments = new ArrayList<>();

        public Builder any(Constants.PlatformResourceType type) {
            segments.add(new PathSegment(type, ANY_NAME));
            return this;
        }

        public PlatformPath build() {
            return new PlatformPath(Collections.unmodifiableList(segments));
        }

        public Builder segment(Constants.PlatformResourceType type, String name) {
            segments.add(new PathSegment(type, name));
            return this;
        }

        public Builder segments(List<PathSegment> segmentsToAdd, int start, int end) {
            for (int i = start; i < end; i++) {
                this.segments.add(segmentsToAdd.get(i));
            }
            return this;
        }

        public Builder segments(PlatformPath path) {
            segments.addAll(path.getSegments());
            return this;
        }
    }

    public static class PathSegment {
        private final String name;
        private final Constants.PlatformResourceType type;

        private PathSegment(Constants.PlatformResourceType type, String name) {
            if (type == null) {
                throw new IllegalArgumentException("Cannot create a [" + getClass().getName() + "] with a null type.");
            }
            if (name == null) {
                throw new IllegalArgumentException("Cannot create a [" + getClass().getName() + "] with a null type.");
            }
            this.type = type;
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public Constants.PlatformResourceType getType() {
            return type;
        }

        public boolean apply(PathSegment other) {
            return this.type.equals(other.type) && (this.name.equals(other.name) || this.name.equals(ANY_NAME));
        }
    }

    public static final String ANY_NAME = "*";
    private static final PlatformPath EMPTY = new PlatformPath(Collections.emptyList());

    public static Builder builder() {
        return new Builder();
    }

    public static PlatformPath empty() {
        return EMPTY;
    }

    private final List<PathSegment> segments;

    private PlatformPath(List<PathSegment> segments) {
        this.segments = segments;
    }

    public PathSegment getLastSegment() {
        if (segments.isEmpty()) {
            throw new IndexOutOfBoundsException("No last segment in empty [" + getClass().getName() + "]");
        }
        return segments.get(segments.size() - 1);
    }

    public List<PathSegment> getSegments() {
        return segments;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        for (PathSegment segment : segments) {
            result.append('/').append(segment.getType()).append('=').append(segment.getName());
        }
        return result.toString();
    }

    public boolean apply(PlatformPath other) {
        if (this.segments.size() == other.segments.size()) {
            for (int i = 0; i < this.segments.size(); i++) {
                if (!this.segments.get(i).apply(other.segments.get(i))) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    public boolean isParentOf(PlatformPath other) {
        if (this.segments.size() <= other.segments.size()) {
            for (int i = 0; i < this.segments.size(); i++) {
                if (!this.segments.get(i).apply(other.segments.get(i))) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }
}
