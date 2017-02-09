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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Defines an operation that can be executed on the managed resource.
 *
 * The {@link #getName()} is the user-visible name (e.g. a human-readable, descriptive name).
 * The {@link #getInternalName()} is the actual operation that is to be executed on the managed resource.
 * For example, {@link #getName()} could return "Deploy Your Application" with the actual operation
 * to be executed on the managed resource, {@link #getInternalName()}, being "deploy-app".
 *
 * @author John Mazzitelli
 *
 * @param <L> the type of the protocol specific location, typically a subclass of {@link NodeLocation}
 */
public final class Operation<L> extends NodeLocationProvider<L> {

    private final boolean modifies;
    private final String internalName;
    private final List<OperationParam> parameters;

    /**
     * Creates an operation definition based on the given information.
     *
     * @param id an internal ID that identifies this operation definition. This should be considered opaque to
     *           consumers of this object. It's uniqueness may or may not be across all resources.
     * @param name the name given to the operation and should be unique among its peers. That is, operation names
     *             associated with a single resource should all be distinct and unique. But obviously operation
     *             names will be the same across resources (especially resources of the same resource type).
     *             This name could also be useful as a default user-visible name (which perhaps could be overridden
     *             for i18n purposes) and can also be useful for logging.
     * @param location identifies the location of the resource to whom this operation definition belongs
     * @param internalName the actual name of the operation as it is known to the actual resource being managed.
     *                      This is the name that is used when telling the managed resource what operation to invoke.
     *                      It may or may not be the same as <code>name</code>.
     * @param modifies if true this means the operation can modify the remote resource
     * @param params Additional params for this operation definition, e.g. coming from operation-dmr. Can be null.
     */
    public Operation(ID id, Name name, L location, String internalName, boolean modifies,
            List<OperationParam> params) {
        super(id, name, location);
        this.internalName = internalName;
        this.modifies = modifies;
        if (params != null && !params.isEmpty()) {
            parameters = Collections.unmodifiableList(new ArrayList<>(params));
        } else {
            parameters = Collections.emptyList();
        }
    }

    /**
     * @return The actual operation to be executed on the managed resource. This is the operation name
     *         that is known to the managed resource and is what the managed resource expects to see
     *         when being asked to execute this operation.
     *         This is not the user-visible name (see {@link #getName()} for that).
     */
    public String getInternalName() {
        return internalName;
    }

    /**
     * @return if true this means the operation can modify the remote resource - either its configuration
     *         settings or (if applicable) its deployments. If an agent is configured as "immutable"
     *         these operations with modifies=true will not be allowed to execute.
     */
    public boolean getModifies() {
        return modifies;
    }

    /**
     * @return parameters defined for this operation - will be empty (not null) if no parameters exist
     */
    public List<OperationParam> getParameters() {
        return parameters;
    }
}
