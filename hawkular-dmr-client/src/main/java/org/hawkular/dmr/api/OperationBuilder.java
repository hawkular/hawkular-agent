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

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 * A set of type safe builders for building and executing DMR operations.
 * <p>
 * Example usage:
 *
 * <pre>
 * &#47;&#47; read a datasource
 * ModelControllerClient mcc = ...;
 * ModelNode myDs = OperationBuilder.readResource()
 *          .address().subsystemDatasources().segment("data-source", "myDatasource").parentBuilder()
 *          .includeDefaults()
 *          .recursive()
 *          .execute(mcc)
 *          .assertSuccess()
 *          .getResultNode();
 *
 * &#47;&#47; remove a datasource with another way of providing an address
 * OperationBuilder.remove()
 *          .address().segments("/subsystem-datasources/data-source=myDatasource").parentBuilder()
 *          .execute(mcc)
 *          .assertSuccess();
 * </pre>
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class OperationBuilder implements SubsystemDatasourceConstants, SubsystemLoggingConstants {
    public abstract static class AbstractOperationBuilder<T extends AbstractOperationBuilder<?, R>, //
    R extends OperationResult<?>> {
        protected final ModelNode baseNode = new ModelNode();

        public T allowResourceServiceRestart() {
            return operationHeader(ModelDescriptionConstants.ALLOW_RESOURCE_SERVICE_RESTART, true);
        }

        public ModelNode build() {
            return baseNode;
        }

        @SuppressWarnings("unchecked")
        protected R createResult(ModelNode request, ModelNode result) {
            return (R) new OperationResult<R>(request, result);
        }

        public R execute(ModelControllerClient client) {
            ModelNode request = build();
            ModelNode result;
            try {
                result = client.execute(request);
            } catch (IOException e) {
                throw new DmrApiException(e);
            }
            log.tracef("Executed [%s] built by [%s] with result [%s]", request, getClass().getName(), result);
            return createResult(request, result);
        }

        @SuppressWarnings("unchecked")
        public T operationHeader(String key, boolean value) {
            baseNode.get(ModelDescriptionConstants.OPERATION_HEADERS).get(key).set(value);
            return (T) this;
        }

        @SuppressWarnings("unchecked")
        public T operationHeader(String key, String value) {
            baseNode.get(ModelDescriptionConstants.OPERATION_HEADERS).get(key).set(value);
            return (T) this;
        }

    }

    public abstract static class AbstractSingleOperationBuilder //
    <T extends AbstractSingleOperationBuilder<?, R>, R extends OperationResult<?>>
            extends AbstractOperationBuilder<T, R> {
        protected final CompositeOperationBuilder<CompositeOperationBuilder<?>> batch;

        private AbstractSingleOperationBuilder(CompositeOperationBuilder<CompositeOperationBuilder<?>> batch,
                String operationName) {
            super();
            this.batch = batch;
            this.baseNode.get(ModelDescriptionConstants.OP).set(operationName);
        }

        @SuppressWarnings("unchecked")
        public AddressBuilder<AddressBuilder<?, T>, T> address() {
            return new AddressBuilder<AddressBuilder<?, T>, T>((T) this);
        }

        @SuppressWarnings("unchecked")
        public T address(ModelNode addess) {
            baseNode.get(ModelDescriptionConstants.ADDRESS).set(addess);
            return (T) this;
        }

        public CompositeOperationBuilder<?> parentBuilder() {
            return batch.operation(build());
        }

    }

    public static class AddOperationBuilder<T extends AddOperationBuilder<?>>
            extends AbstractSingleOperationBuilder<T, OperationResult<?>> {

        private AddOperationBuilder(CompositeOperationBuilder<CompositeOperationBuilder<?>> batch) {
            super(batch, ModelDescriptionConstants.ADD);
        }

        @SuppressWarnings("unchecked")
        public T attribute(String name, boolean value) {
            baseNode.get(name).set(value);
            return (T) this;
        }

        /**
         * If {@code value == null}, no entry is added. Otherwise, the attribute will be added.
         *
         * @param name
         * @param value
         * @return
         */
        @SuppressWarnings("unchecked")
        public T attribute(String name, Integer value) {
            if (value != null) {
                baseNode.get(name).set(value);
            }
            return (T) this;
        }

        /**
         * If {@code value == null}, no entry is added. Otherwise, the attribute will be added.
         *
         * @param name
         * @param value
         * @return
         */
        @SuppressWarnings("unchecked")
        public T attribute(String name, String value) {
            if (value != null) {
                baseNode.get(name).set(value);
            }
            return (T) this;
        }

        public T valueAttribute(String value) {
            return attribute(ModelDescriptionConstants.VALUE, value);
        }

    }

    public static class AddressBuilder<T extends AddressBuilder<?, P>, P extends AbstractSingleOperationBuilder<?, ?>> {
        private final ModelNode baseNode = new ModelNode();
        private final P parentBuilder;

        private AddressBuilder(P parentBuilder) {
            super();
            this.parentBuilder = parentBuilder;
        }

        public ModelNode build() {
            return baseNode;
        }

        @SuppressWarnings("unchecked")
        public T datasource(String datasourceName) {
            baseNode.add(DATASOURCE, datasourceName);
            return (T) this;
        }

        public P parentBuilder() {
            parentBuilder.address(baseNode);
            return parentBuilder;
        }

        @SuppressWarnings("unchecked")
        public T segment(String key, String value) {
            baseNode.add(key, value);
            return (T) this;
        }

        @SuppressWarnings("unchecked")
        public T segments(ModelNode adr) {
            for (Property prop : adr.asPropertyList()) {
                segment(prop.getName(), prop.getValue().asString());
            }
            return (T) this;
        }

        @SuppressWarnings("unchecked")
        public T segments(String path) {
            StringTokenizer st = new StringTokenizer(path, "/");
            while (st.hasMoreTokens()) {
                String token = st.nextToken();
                int eqPos = token.indexOf('=');
                if (eqPos >= 0) {
                    String key = token.substring(0, eqPos);
                    String value = token.substring(eqPos + 1);
                    segment(key, value);
                } else {
                    /* ignore */
                }
            }
            return (T) this;
        }

        public T subsystem(String subsystem) {
            return segment(ModelDescriptionConstants.SUBSYSTEM, subsystem);
        }

        public T subsystemDatasources() {
            return segment(ModelDescriptionConstants.SUBSYSTEM, DATASOURCES);
        }

        public T subsystemLogging() {
            return segment(ModelDescriptionConstants.SUBSYSTEM, LOGGING);
        }

        public T xaDatasource(String xaDatasourceName) {
            return segment(DATASOURCE, xaDatasourceName);
        }

    }

    public static class BooleanOperationResult<R extends BooleanOperationResult<?>> extends OperationResult<R> {

        private BooleanOperationResult(ModelNode requestNode, ModelNode resultNode) {
            super(requestNode, resultNode);
        }

        public boolean getBoolean() {
            if (!responseNode.hasDefined(ModelDescriptionConstants.RESULT)) {
                throw new IllegalStateException(
                        String.format("Need [%s] to return boolean", ModelDescriptionConstants.RESULT));
            }
            return responseNode.get(ModelDescriptionConstants.RESULT).asBoolean();
        }
    }

    public static class CompositeOperationBuilder<T extends CompositeOperationBuilder<?>>
            extends AbstractOperationBuilder<T, OperationResult<?>> {

        private ModelNode steps;

        private CompositeOperationBuilder() {
            baseNode.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.COMPOSITE);
            baseNode.get(ModelDescriptionConstants.ADDRESS).setEmptyList();
            this.steps = baseNode.get(ModelDescriptionConstants.STEPS);
        }

        @SuppressWarnings("unchecked")
        public AddOperationBuilder<AddOperationBuilder<?>> add() {
            return new AddOperationBuilder<>((CompositeOperationBuilder<CompositeOperationBuilder<?>>) this);
        }

        @SuppressWarnings("unchecked")
        public MapPutOperationBuilder<MapPutOperationBuilder<?>> mapPut() {
            return new MapPutOperationBuilder<>((CompositeOperationBuilder<CompositeOperationBuilder<?>>) this);
        }

        @SuppressWarnings("unchecked")
        public T operation(ModelNode op) {
            steps.add(op);
            return (T) this;
        }

        @SuppressWarnings("unchecked")
        public ReadAttributeOperationBuilder<ReadAttributeOperationBuilder<?>> readAttribute() {
            return new ReadAttributeOperationBuilder<>((CompositeOperationBuilder<CompositeOperationBuilder<?>>) this);
        }

        @SuppressWarnings("unchecked")
        public ReadChildrenResourcesOperationBuilder<ReadChildrenResourcesOperationBuilder<?>> //
        readReadChildrenResources() {
            return new ReadChildrenResourcesOperationBuilder<>(
                    (CompositeOperationBuilder<CompositeOperationBuilder<?>>) this);
        }

        @SuppressWarnings("unchecked")
        public ReadResourceOperationBuilder<ReadResourceOperationBuilder<?>> readResource() {
            return new ReadResourceOperationBuilder<>((CompositeOperationBuilder<CompositeOperationBuilder<?>>) this);
        }

        @SuppressWarnings("unchecked")
        public ReloadOperationBuilder<ReloadOperationBuilder<?>> reload() {
            return new ReloadOperationBuilder<>((CompositeOperationBuilder<CompositeOperationBuilder<?>>) this);
        }

        @SuppressWarnings("unchecked")
        public RemoveOperationBuilder<RemoveOperationBuilder<?>> remove() {
            return new RemoveOperationBuilder<>((CompositeOperationBuilder<CompositeOperationBuilder<?>>) this);
        }

        @SuppressWarnings("unchecked")
        public WriteAttributeOperationBuilder<WriteAttributeOperationBuilder<?>> writeAttribute() {
            return new WriteAttributeOperationBuilder<>((CompositeOperationBuilder<CompositeOperationBuilder<?>>) this);
        }

    }

    public static class MapPutOperationBuilder<T extends MapPutOperationBuilder<?>>
            extends AbstractSingleOperationBuilder<T, OperationResult<?>> {

        private MapPutOperationBuilder(CompositeOperationBuilder<CompositeOperationBuilder<?>> batch) {
            super(batch, "map-put");
        }

        @SuppressWarnings("unchecked")
        public T key(String key) {
            baseNode.get("key").set(key);
            return (T) this;
        }

        @SuppressWarnings("unchecked")
        public T name(String name) {
            baseNode.get(ModelDescriptionConstants.NAME).set(name);
            return (T) this;
        }

        @SuppressWarnings("unchecked")
        public T value(String value) {
            baseNode.get(ModelDescriptionConstants.VALUE).set(value);
            return (T) this;
        }

    }

    public static class NodeListOperationResult<R extends NodeListOperationResult<?>> extends OperationResult<R> {

        private NodeListOperationResult(ModelNode requestNode, ModelNode resultNode) {
            super(requestNode, resultNode);
        }

        public List<ModelNode> getNodeList() {
            if (!responseNode.hasDefined(ModelDescriptionConstants.RESULT)) {
                return Collections.emptyList();
            }
            return responseNode.get(ModelDescriptionConstants.RESULT).asList();
        }
    }

    /**
     * A result of a DMR operation.
     *
     * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
     * @param <R>
     */
    public static class OperationResult<R extends OperationResult<?>> {
        protected final ModelNode requestNode;
        protected final ModelNode responseNode;

        private OperationResult(ModelNode requestNode, ModelNode responseNode) {
            super();
            this.requestNode = requestNode;
            this.responseNode = responseNode;
        }

        /**
         * Asserts that the {@link ModelDescriptionConstants#OUTCOME} fielf of {@link #responseNode} is equal to
         * {@link ModelDescriptionConstants#SUCCESS}.
         *
         * @return this result
         */
        @SuppressWarnings("unchecked")
        public R assertSuccess() {
            if (responseNode == null) {
                throw new IllegalStateException("responseNode cannot be null during assertSuccess()");
            }
            if (!responseNode.hasDefined(ModelDescriptionConstants.OUTCOME)) {
                String msg = String.format("No [%s] field in responseNode [%s]", ModelDescriptionConstants.OUTCOME,
                        responseNode.toString());
                throw new IllegalStateException(msg);
            }
            if (!responseNode.get(ModelDescriptionConstants.OUTCOME).asString()
                    .equals(ModelDescriptionConstants.SUCCESS)) {
                String msg = String.format("Could not perform operation [%s]: %s",
                        requestNode.get(ModelDescriptionConstants.OP).asString(),
                        responseNode.get(ModelDescriptionConstants.FAILURE_DESCRIPTION).asString());
                throw new OperationFailureException(msg);
            }
            return (R) this;
        }

        /**
         * @return the request {@link ModelNode} that the present {@link OperationResult} is the response to.
         */
        public ModelNode getRequestNode() {
            return requestNode;
        }

        /**
         * @return the {@link ModelNode} obtained as a response to {@link #requestNode}
         */
        public ModelNode getResponseNode() {
            return responseNode;
        }

        /**
         * Should be called after {@link #assertSuccess()}.
         *
         * @return an equivalent of {@code responseNode.get(RESULT)}
         */
        public ModelNode getResultNode() {
            return responseNode.get(ModelDescriptionConstants.RESULT);
        }

    }

    public static class ReadAttributeOperationBuilder<T extends ReadAttributeOperationBuilder<?>>
            extends AbstractSingleOperationBuilder<T, OperationResult<?>> {

        private ReadAttributeOperationBuilder(CompositeOperationBuilder<CompositeOperationBuilder<?>> batch) {
            super(batch, ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION);
        }

        public T excludeDefaults() {
            return includeDefaults(false);
        }

        public T includeDefaults() {
            return includeDefaults(true);
        }

        @SuppressWarnings("unchecked")
        public T includeDefaults(boolean includeDefaults) {
            baseNode.get(ModelDescriptionConstants.INCLUDE_DEFAULTS).set(includeDefaults);
            return (T) this;
        }

        @SuppressWarnings("unchecked")
        public T name(String name) {
            baseNode.get(ModelDescriptionConstants.NAME).set(name);
            return (T) this;
        }

        public T resolveExpressions() {
            return resolveExpressions(true);
        }

        @SuppressWarnings("unchecked")
        public T resolveExpressions(boolean includeDefaults) {
            baseNode.get(ModelDescriptionConstants.RESOLVE_EXPRESSIONS).set(includeDefaults);
            return (T) this;
        }

    }

    public static class ReadChildrenNamesOperationBuilder<T extends ReadChildrenNamesOperationBuilder<?>>
            extends AbstractSingleOperationBuilder<T, StringListOperationResult<?>> {

        private ReadChildrenNamesOperationBuilder(CompositeOperationBuilder<CompositeOperationBuilder<?>> bb) {
            super(bb, ModelDescriptionConstants.READ_CHILDREN_NAMES_OPERATION);
        }

        @SuppressWarnings("unchecked")
        public T childType(String childType) {
            baseNode.get(ModelDescriptionConstants.CHILD_TYPE).set(childType);
            return (T) this;
        }

        protected StringListOperationResult<StringListOperationResult<?>> createResult(ModelNode request,
                ModelNode result) {
            return new StringListOperationResult<StringListOperationResult<?>>(request, result);
        }

    }

    public static class ReadChildrenResourcesOperationBuilder<T extends ReadChildrenResourcesOperationBuilder<?>>
            extends AbstractSingleOperationBuilder<T, NodeListOperationResult<?>> {

        private ReadChildrenResourcesOperationBuilder(CompositeOperationBuilder<CompositeOperationBuilder<?>> bb) {
            super(bb, ModelDescriptionConstants.READ_CHILDREN_RESOURCES_OPERATION);
        }

        @SuppressWarnings("unchecked")
        public T childType(String childType) {
            baseNode.get(ModelDescriptionConstants.CHILD_TYPE).set(childType);
            return (T) this;
        }

        @Override
        protected NodeListOperationResult<NodeListOperationResult<?>> createResult(ModelNode request,
                ModelNode result) {
            return new NodeListOperationResult<>(request, result);
        }

        public T excludeDefaults() {
            return includeDefaults(false);
        }

        public T excludeRuntime() {
            return includeRuntime(false);
        }

        public T includeDefaults() {
            return includeDefaults(true);
        }

        @SuppressWarnings("unchecked")
        public T includeDefaults(boolean includeDefaults) {
            baseNode.get(ModelDescriptionConstants.INCLUDE_DEFAULTS).set(includeDefaults);
            return (T) this;
        }

        public T includeRuntime() {
            return includeRuntime(true);
        }

        @SuppressWarnings("unchecked")
        public T includeRuntime(boolean includeRuntime) {
            baseNode.get(ModelDescriptionConstants.INCLUDE_RUNTIME).set(includeRuntime);
            return (T) this;
        }

        @SuppressWarnings("unchecked")
        public T proxies() {
            baseNode.get(ModelDescriptionConstants.PROXIES).set(true);
            return (T) this;
        }

        @SuppressWarnings("unchecked")
        public T recursive() {
            baseNode.get(ModelDescriptionConstants.RECURSIVE).set(true);
            return (T) this;
        }

        @SuppressWarnings("unchecked")
        public T recursiveDepth(int depth) {
            baseNode.get(ModelDescriptionConstants.RECURSIVE).set(true);
            baseNode.get(ModelDescriptionConstants.RECURSIVE_DEPTH).set(depth);
            return (T) this;
        }

    }

    public static class ReadResourceOperationBuilder<T extends ReadResourceOperationBuilder<?>>
            extends AbstractSingleOperationBuilder<T, OperationResult<?>> {

        private ReadResourceOperationBuilder(CompositeOperationBuilder<CompositeOperationBuilder<?>> bb) {
            super(bb, ModelDescriptionConstants.READ_RESOURCE_OPERATION);
        }

        public T excludeDefaults() {
            return includeDefaults(false);
        }

        public T excludeRuntime() {
            return includeRuntime(false);
        }

        public T includeDefaults() {
            return includeDefaults(true);
        }

        @SuppressWarnings("unchecked")
        public T includeDefaults(boolean includeDefaults) {
            baseNode.get(ModelDescriptionConstants.INCLUDE_DEFAULTS).set(includeDefaults);
            return (T) this;
        }

        public T includeRuntime() {
            return includeRuntime(true);
        }

        @SuppressWarnings("unchecked")
        public T includeRuntime(boolean includeRuntime) {
            baseNode.get(ModelDescriptionConstants.INCLUDE_RUNTIME).set(includeRuntime);
            return (T) this;
        }

        @SuppressWarnings("unchecked")
        public T proxies() {
            baseNode.get(ModelDescriptionConstants.PROXIES).set(true);
            return (T) this;
        }

        @SuppressWarnings("unchecked")
        public T recursive() {
            baseNode.get(ModelDescriptionConstants.RECURSIVE).set(true);
            return (T) this;
        }

        @SuppressWarnings("unchecked")
        public T recursiveDepth(int depth) {
            baseNode.get(ModelDescriptionConstants.RECURSIVE).set(true);
            baseNode.get(ModelDescriptionConstants.RECURSIVE_DEPTH).set(depth);
            return (T) this;
        }

    }

    public static class ReloadOperationBuilder<T extends ReloadOperationBuilder<?>>
            extends AbstractSingleOperationBuilder<T, OperationResult<?>> {

        private ReloadOperationBuilder(CompositeOperationBuilder<CompositeOperationBuilder<?>> bb) {
            super(bb, "reload");
        }

        @SuppressWarnings("unchecked")
        public T adminOnly(boolean adminOnly) {
            baseNode.get(ModelDescriptionConstants.ADMIN_ONLY).set(adminOnly);
            return (T) this;
        }

        @SuppressWarnings("unchecked")
        public T useCurrentServerConfig(boolean useCurrentServerConfig) {
            baseNode.get(ModelDescriptionConstants.USE_CURRENT_SERVER_CONFIG).set(useCurrentServerConfig);
            return (T) this;
        }

    }

    public static class RemoveOperationBuilder<T extends RemoveOperationBuilder<?>>
            extends AbstractSingleOperationBuilder<T, OperationResult<?>> {

        private RemoveOperationBuilder(CompositeOperationBuilder<CompositeOperationBuilder<?>> bb) {
            super(bb, ModelDescriptionConstants.REMOVE);
        }

    }

    public static class StringListOperationResult<R extends StringListOperationResult<?>>
            extends NodeListOperationResult<R> {

        private StringListOperationResult(ModelNode requestNode, ModelNode resultNode) {
            super(requestNode, resultNode);
        }

        public Set<String> getHashSet() {
            return Collections
                    .unmodifiableSet(getNodeList().stream().map(n -> n.asString()).collect(Collectors.toSet()));
        }

        public Set<String> getLinkedHashSet() {
            return Collections.unmodifiableSet(
                    getNodeList().stream().map(n -> n.asString()).collect(Collectors.toCollection(LinkedHashSet::new)));
        }

        public List<String> getList() {
            return Collections
                    .unmodifiableList(getNodeList().stream().map(n -> n.asString()).collect(Collectors.toList()));
        }
    }

    public static class WriteAttributeOperationBuilder<T extends WriteAttributeOperationBuilder<?>>
            extends AbstractSingleOperationBuilder<T, OperationResult<?>> {

        private WriteAttributeOperationBuilder(CompositeOperationBuilder<CompositeOperationBuilder<?>> bb) {
            super(bb, ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION);
        }

        /**
         * If {@code value == null}, the attribute will be removed. Otherwise, the attribute will be written.
         *
         * @param name
         * @param value
         * @return
         */
        @SuppressWarnings("unchecked")
        public T attribute(String name, String value) {
            baseNode.get(ModelDescriptionConstants.NAME).set(name);
            if (value != null) {
                baseNode.get(ModelDescriptionConstants.VALUE).set(value);
            }
            return (T) this;
        }

        public T valueAttribute(String value) {
            return attribute(ModelDescriptionConstants.VALUE, value);
        }

    }

    private static final MsgLogger log = DmrApiLoggers.getLogger(OperationBuilder.class);

    public static AddOperationBuilder<AddOperationBuilder<?>> add() {
        return new AddOperationBuilder<>(null);
    }

    public static //
    AddressBuilder<AddressBuilder<?, AbstractSingleOperationBuilder<?, ?>>, AbstractSingleOperationBuilder<?, ?>> //
    address() {
        return new AddressBuilder<AddressBuilder<?, AbstractSingleOperationBuilder<?, ?>>, //
        AbstractSingleOperationBuilder<?, ?>>((AbstractSingleOperationBuilder<?, ?>) null);
    }

    public static CompositeOperationBuilder<CompositeOperationBuilder<?>> composite() {
        return new CompositeOperationBuilder<>();
    }

    public static MapPutOperationBuilder<MapPutOperationBuilder<?>> mapPut() {
        return new MapPutOperationBuilder<>(null);
    }

    public static ReadAttributeOperationBuilder<ReadAttributeOperationBuilder<?>> readAttribute() {
        return new ReadAttributeOperationBuilder<>(null);
    }

    public static ReadChildrenNamesOperationBuilder<ReadChildrenNamesOperationBuilder<?>> readChildrenNames() {
        return new ReadChildrenNamesOperationBuilder<>(null);
    }

    public static ReadChildrenResourcesOperationBuilder<ReadChildrenResourcesOperationBuilder<?>> //
    readChildrenResources() {
        return new ReadChildrenResourcesOperationBuilder<>(null);
    }

    public static ReadResourceOperationBuilder<ReadResourceOperationBuilder<?>> readResource() {
        return new ReadResourceOperationBuilder<>(null);
    }

    public static ReloadOperationBuilder<ReloadOperationBuilder<?>> reload() {
        return new ReloadOperationBuilder<>(null);
    }

    public static RemoveOperationBuilder<RemoveOperationBuilder<?>> remove() {
        return new RemoveOperationBuilder<>(null);
    }

    public static WriteAttributeOperationBuilder<WriteAttributeOperationBuilder<?>> writeAttribute() {
        return new WriteAttributeOperationBuilder<>(null);
    }

}
