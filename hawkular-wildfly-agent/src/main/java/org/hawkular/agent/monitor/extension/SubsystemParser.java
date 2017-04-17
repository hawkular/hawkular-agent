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
package org.hawkular.agent.monitor.extension;

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;

import java.util.List;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

public class SubsystemParser implements XMLStreamConstants, XMLElementReader<List<ModelNode>>,
        XMLElementWriter<SubsystemMarshallingContext> {

    public static final SubsystemParser INSTANCE = new SubsystemParser();

    private static final PersistentResourceXMLDescription xmlDescription;

    static {
        try {
            xmlDescription =
                    builder(SubsystemDefinition.INSTANCE)
                            .addAttributes(SubsystemAttributes.ATTRIBUTES)

                            .addChild(builder(DiagnosticsDefinition.INSTANCE)
                                    .setXmlElementName(DiagnosticsDefinition.DIAGNOSTICS)
                                    .addAttributes(DiagnosticsAttributes.ATTRIBUTES)
                            )
                            .addChild(builder(StorageDefinition.INSTANCE)
                                    .setXmlElementName(StorageDefinition.STORAGE_ADAPTER)
                                    .addAttributes(StorageAttributes.ATTRIBUTES)
                            )

                            .addChild(builder(DMRMetricSetDefinition.INSTANCE)
                                    .setXmlElementName(DMRMetricSetDefinition.METRIC_SET)
                                    .addAttributes(DMRMetricSetAttributes.ATTRIBUTES)
                                    .addChild(builder(DMRMetricDefinition.INSTANCE)
                                            .setXmlElementName(DMRMetricDefinition.METRIC)
                                            .addAttributes(DMRMetricAttributes.ATTRIBUTES)
                                    )
                            )
                            .addChild(builder(DMRAvailSetDefinition.INSTANCE)
                                    .setXmlElementName(DMRAvailSetDefinition.AVAIL_SET)
                                    .addAttributes(DMRAvailSetAttributes.ATTRIBUTES)
                                    .addChild(builder(DMRAvailDefinition.INSTANCE)
                                            .setXmlElementName(DMRAvailDefinition.AVAIL)
                                            .addAttributes(DMRAvailAttributes.ATTRIBUTES)
                                    )
                            )
                            .addChild(builder(DMRResourceTypeSetDefinition.INSTANCE)
                                    .setXmlElementName(DMRResourceTypeSetDefinition.RESOURCE_TYPE_SET)
                                    .addAttributes(DMRResourceTypeSetAttributes.ATTRIBUTES)
                                    .addChild(builder(DMRResourceTypeDefinition.INSTANCE)
                                            .setXmlElementName(DMRResourceTypeDefinition.RESOURCE_TYPE)
                                            .addAttributes(DMRResourceTypeAttributes.ATTRIBUTES)
                                            .addChild(builder(DMRNotificationDefinition.INSTANCE)
                                                    .setXmlElementName(DMRNotificationDefinition.NOTIFICATION)
                                                    .addAttributes(DMRNotificationAttributes.ATTRIBUTES))
                                            .addChild(builder(DMRResourceConfigDefinition.INSTANCE)
                                                    .setXmlElementName(DMRResourceConfigDefinition.RESOURCE_CONFIG)
                                                    .addAttributes(DMRResourceConfigAttributes.ATTRIBUTES)
                                            )
                                            .addChild(builder(DMROperationDefinition.INSTANCE)
                                                    .setXmlElementName(DMROperationDefinition.OPERATION)
                                                    .addAttributes(DMROperationAttributes.ATTRIBUTES)
                                                    .addChild(builder(DMROperationParamDefinition.INSTANCE)
                                                            .setXmlElementName(DMROperationParamDefinition.PARAM)
                                                            .addAttributes(DMROperationParamAttributes.ATTRIBUTES)
                                                    )
                                            )
                                    )
                            )

                            .addChild(builder(JMXMetricSetDefinition.INSTANCE)
                                    .setXmlElementName(JMXMetricSetDefinition.METRIC_SET)
                                    .addAttributes(JMXMetricSetAttributes.ATTRIBUTES)
                                    .addChild(builder(JMXMetricDefinition.INSTANCE)
                                            .setXmlElementName(JMXMetricDefinition.METRIC)
                                            .addAttributes(JMXMetricAttributes.ATTRIBUTES)
                                    )
                            )
                            .addChild(builder(JMXAvailSetDefinition.INSTANCE)
                                    .setXmlElementName(JMXAvailSetDefinition.AVAIL_SET)
                                    .addAttributes(JMXAvailSetAttributes.ATTRIBUTES)
                                    .addChild(builder(JMXAvailDefinition.INSTANCE)
                                            .setXmlElementName(JMXAvailDefinition.AVAIL)
                                            .addAttributes(JMXAvailAttributes.ATTRIBUTES)
                                    )
                            )
                            .addChild(builder(JMXResourceTypeSetDefinition.INSTANCE)
                                    .setXmlElementName(JMXResourceTypeSetDefinition.RESOURCE_TYPE_SET)
                                    .addAttributes(JMXResourceTypeSetAttributes.ATTRIBUTES)
                                    .addChild(builder(JMXResourceTypeDefinition.INSTANCE)
                                            .setXmlElementName(JMXResourceTypeDefinition.RESOURCE_TYPE)
                                            .addAttributes(JMXResourceTypeAttributes.ATTRIBUTES)
                                            .addChild(builder(JMXResourceConfigDefinition.INSTANCE)
                                                    .setXmlElementName(JMXResourceConfigDefinition.RESOURCE_CONFIG)
                                                    .addAttributes(JMXResourceConfigAttributes.ATTRIBUTES)
                                            )
                                            .addChild(builder(JMXOperationDefinition.INSTANCE)
                                                    .setXmlElementName(JMXOperationDefinition.OPERATION)
                                                    .addAttributes(JMXOperationAttributes.ATTRIBUTES)
                                            )
                                    )
                            )

                            .addChild(builder(ManagedServersDefinition.INSTANCE)
                                    .setXmlElementName(ManagedServersDefinition.MANAGED_SERVERS)
                                    .addAttributes(ManagedServersAttributes.ATTRIBUTES)
                                    .addChild(builder(RemoteDMRDefinition.INSTANCE)
                                            .setXmlElementName(RemoteDMRDefinition.REMOTE_DMR)
                                            .addAttributes(RemoteDMRAttributes.ATTRIBUTES)
                                            .addChild(builder(RemoteDMRWaitForDefinition.INSTANCE)
                                                    .setXmlElementName(RemoteDMRWaitForDefinition.WAIT_FOR)
                                                    .addAttributes(RemoteDMRWaitForAttributes.ATTRIBUTES)
                                            )
                                    )
                                    .addChild(builder(LocalDMRDefinition.INSTANCE)
                                            .setXmlElementName(LocalDMRDefinition.LOCAL_DMR)
                                            .addAttributes(LocalDMRAttributes.ATTRIBUTES)
                                            .addChild(builder(LocalDMRWaitForDefinition.INSTANCE)
                                                    .setXmlElementName(LocalDMRWaitForDefinition.WAIT_FOR)
                                                    .addAttributes(LocalDMRWaitForAttributes.ATTRIBUTES)
                                            )
                                    )
                                    .addChild(builder(RemoteJMXDefinition.INSTANCE)
                                            .setXmlElementName(RemoteJMXDefinition.REMOTE_JMX)
                                            .addAttributes(RemoteJMXAttributes.ATTRIBUTES)
                                            .addChild(builder(RemoteJMXWaitForDefinition.INSTANCE)
                                                    .setXmlElementName(RemoteJMXWaitForDefinition.WAIT_FOR)
                                                    .addAttributes(RemoteJMXWaitForAttributes.ATTRIBUTES)
                                            )
                                    )
                                    .addChild(builder(LocalJMXDefinition.INSTANCE)
                                            .setXmlElementName(LocalJMXDefinition.LOCAL_JMX)
                                            .addAttributes(LocalJMXAttributes.ATTRIBUTES)
                                            .addChild(builder(LocalJMXWaitForDefinition.INSTANCE)
                                                    .setXmlElementName(LocalJMXWaitForDefinition.WAIT_FOR)
                                                    .addAttributes(LocalJMXWaitForAttributes.ATTRIBUTES)
                                            )
                                    )
                            )

                            .addChild(builder(PlatformDefinition.INSTANCE)
                                    .setXmlElementName(PlatformDefinition.PLATFORM)
                                    .addAttributes(PlatformAttributes.ATTRIBUTES)
                                    .addChild(builder(FileStoresDefinition.INSTANCE)
                                            .setXmlElementName(FileStoresDefinition.FILE_STORES)
                                            .addAttributes(FileStoresAttributes.ATTRIBUTES)
                                    )
                                    .addChild(builder(MemoryDefinition.INSTANCE)
                                            .setXmlElementName(MemoryDefinition.MEMORY)
                                            .addAttributes(MemoryAttributes.ATTRIBUTES)
                                    )
                                    .addChild(builder(ProcessorsDefinition.INSTANCE)
                                            .setXmlElementName(ProcessorsDefinition.PROCESSORS)
                                            .addAttributes(ProcessorsAttributes.ATTRIBUTES)
                                    )
                                    .addChild(builder(PowerSourcesDefinition.INSTANCE)
                                            .setXmlElementName(PowerSourcesDefinition.POWER_SOURCES)
                                            .addAttributes(PowerSourcesAttributes.ATTRIBUTES)
                                    )
                            )
                            .build();
        } catch (Throwable t) {
            System.err.println("CANNOT INITIALIZE PARSER: " + SubsystemParser.class);
            throw t;
        }
    }

    private SubsystemParser() {
    }

    @Override
    public void writeContent(XMLExtendedStreamWriter writer, SubsystemMarshallingContext context)
            throws XMLStreamException {

        ModelNode model = new ModelNode();
        //this is bit of workaround for SPRD to work properly (mazz: what does that mean???)
        model.get(SubsystemDefinition.INSTANCE.getPathElement().getKeyValuePair()).set(context.getModelNode());
        xmlDescription.persist(writer, model, SubsystemExtension.NAMESPACE);
    }

    @Override
    public void readElement(XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {
        xmlDescription.parse(reader, PathAddress.EMPTY_ADDRESS, list);
    }
}
