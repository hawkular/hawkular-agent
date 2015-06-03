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
package org.hawkular.agent.swarm;

public class DMRResourceType {

    private final String name;
    private String resourceNameTemplate;
    private String path;
    private String parents = null;
    private String metricSets = null;
    private String availSets = null;

    public DMRResourceType(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public String resourceNameTemplate() {
        return resourceNameTemplate;
    }

    public DMRResourceType resourceNameTemplate(String resourceNameTemplate) {
        this.resourceNameTemplate = resourceNameTemplate;
        return this;
    }

    public String path() {
        return path;
    }

    public DMRResourceType path(String path) {
        this.path = path;
        return this;
    }

    public String parents() {
        return parents;
    }

    public DMRResourceType parents(String parents) {
        this.parents = parents;
        return this;
    }

    public String metricSets() {
        return metricSets;
    }

    public DMRResourceType metricSets(String metricSets) {
        this.metricSets = metricSets;
        return this;
    }

    public String availSets() {
        return availSets;
    }

    public DMRResourceType availSets(String availSets) {
        this.availSets = availSets;
        return this;
    }
}
