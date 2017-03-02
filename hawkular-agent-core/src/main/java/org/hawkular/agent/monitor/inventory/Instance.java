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
 * An abstract supertype for {@link MeasurementInstance} and {@link ResourceConfigurationPropertyInstance}.
 * Instances can be associated with a specific resource - see {@link #setResource(Resource)}. Once assigned
 * a resource, an instance is owned by that resource for the instance's lifetime. You can use
 * the copy constructor to create a copy of an instance and reassign that new instance to a new resource.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public abstract class Instance<L, T extends AttributeLocationProvider<L>> extends AttributeLocationProvider<L> {

    private final T type;
    private Resource<L> resource;

    protected Instance(ID id, Name name, AttributeLocation<L> attributeLocation, T type) {
        super(id, name, attributeLocation);
        this.type = type;
    }

    /**
     * Copy constructor with the added feature of allowing the new copy to be "disowned" from any
     * resource that the original instance was owned by. This allows one to create a copy of an instance
     * and reassign it to a different owner resource.
     * @param original the object to copy
     * @param disown if true, the new copy will not have an owning {@link #getResource() resource}.
     *               if false, the new instance will be owned by the same resource that owns the original.
     */
    protected Instance(Instance<L, T> original, boolean disown) {
        this(original.getID(), original.getName(), original.getAttributeLocation(), original.type);
        this.resource = (disown) ? null : original.resource;
    }

    /**
     * @return the type of this {@link Instance}
     */
    public T getType() {
        return type;
    }

    /**
     * @param resource assigns this instance to the given resource which means
     *                 the resource is now to be considered the "owner" of this instance.
     */
    public void setResource(Resource<L> resource) {
        if (this.resource != null) {
            throw new IllegalStateException(
                    String.format("Instance [%s] is already assigned to [%s]. Cannot reassign to [%s]",
                            this, this.resource, resource));
        }
        this.resource = resource;
    }

    /**
     * @return the resource that owns this instance, if one has been assigned
     * @see Instance#assignToResource(Resource)
     */
    public Resource<L> getResource() {
        return resource;
    }

}
