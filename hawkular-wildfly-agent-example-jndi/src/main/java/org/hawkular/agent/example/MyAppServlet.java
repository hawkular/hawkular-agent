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
package org.hawkular.agent.example;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.ejb.EJB;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.hawkular.agent.monitor.api.Avail;
import org.hawkular.agent.monitor.api.AvailDataPayloadBuilder;
import org.hawkular.agent.monitor.api.AvailStorage;
import org.hawkular.agent.monitor.api.HawkularWildFlyAgentContext;
import org.hawkular.agent.monitor.api.InventoryEvent;
import org.hawkular.agent.monitor.api.MetricDataPayloadBuilder;
import org.hawkular.agent.monitor.api.MetricStorage;
import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.inventory.ResourceType;
import org.hawkular.metrics.client.common.MetricType;

public class MyAppServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @EJB
    private HawkularWildFlyAgentProvider hawkularAgent;

    public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String newResourceId = request.getParameter("newResourceID");
        String metricKey = request.getParameter("metricKey");
        String availKey = request.getParameter("availKey");

        if (newResourceId == null && metricKey == null && availKey == null) {
            throw new ServletException("Don't know what to do!");
        }

        if (newResourceId != null) {
            createNewResource(request, response, newResourceId);
        } else if (metricKey != null) {
            Double metricValue = Double.valueOf(request.getParameter("metricValue"));
            sendMetric(request, response, metricKey, metricValue);
        } else if (availKey != null) {
            Avail availValue = Avail.valueOf(request.getParameter("availValue"));
            sendAvail(request, response, availKey, availValue);
        }
    }

    private void sendAvail(HttpServletRequest request, HttpServletResponse response, String availKey,
            Avail availValue) {
        try {
            HawkularWildFlyAgentContext hawkularWildFlyAgent = hawkularAgent.getHawkularWildFlyAgent();
            AvailStorage availStorage = hawkularWildFlyAgent.getAvailStorage();

            AvailDataPayloadBuilder payloadBuilder = availStorage.createAvailDataPayloadBuilder();
            payloadBuilder.addDataPoint(availKey, System.currentTimeMillis(), availValue);
            availStorage.store(payloadBuilder);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendMetric(HttpServletRequest request, HttpServletResponse response, String metricKey,
            Double metricValue) {
        try {
            HawkularWildFlyAgentContext hawkularWildFlyAgent = hawkularAgent.getHawkularWildFlyAgent();
            MetricStorage metricStorage = hawkularWildFlyAgent.getMetricStorage();

            MetricDataPayloadBuilder payloadBuilder = metricStorage.createMetricDataPayloadBuilder();
            payloadBuilder.addDataPoint(metricKey, System.currentTimeMillis(), metricValue, MetricType.GAUGE);
            metricStorage.store(payloadBuilder);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createNewResource(HttpServletRequest request, HttpServletResponse response, String resourceId) {
        try {
            MyAppSamplingService myAppSamplingService = hawkularAgent.getSamplingService();
            HawkularWildFlyAgentContext hawkularWildFlyAgent = hawkularAgent.getHawkularWildFlyAgent();

            ResourceType<MyAppNodeLocation> resourceType = ResourceType.<MyAppNodeLocation> builder()
                    .id(new ID("My App ResourceType"))
                    .name(new Name("My App Resource Type"))
                    .parent(null)
                    .build();

            Resource<MyAppNodeLocation> resource = Resource.<MyAppNodeLocation> builder()
                    .type(resourceType)
                    .id(new ID(resourceId))
                    .name(new Name("My App Resource " + resourceId))
                    .parent(null)
                    .location(new MyAppNodeLocation("/" + resourceId))
                    .build();

            List<Resource<MyAppNodeLocation>> resources = Arrays.asList(resource);
            InventoryEvent<MyAppNodeLocation> event = new InventoryEvent<>(myAppSamplingService, resources);
            hawkularWildFlyAgent.getInventoryStorage().resourcesAdded(event);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
