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
package org.hawkular.agent.monitor.storage;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.hawkular.agent.monitor.api.Avail;
import org.hawkular.agent.monitor.api.AvailDataPayloadBuilder;
import org.hawkular.agent.monitor.api.MetricDataPayloadBuilder;
import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
import org.hawkular.agent.monitor.inventory.AvailInstance;
import org.hawkular.agent.monitor.inventory.AvailType;
import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.MeasurementInstance;
import org.hawkular.agent.monitor.inventory.MeasurementType;
import org.hawkular.agent.monitor.inventory.MetricInstance;
import org.hawkular.agent.monitor.inventory.MetricType;
import org.hawkular.agent.monitor.inventory.NamedObject;
import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.inventory.ResourceType;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.scheduler.polling.Task;
import org.hawkular.agent.monitor.service.ServerIdentifiers;
import org.hawkular.agent.monitor.service.Util;
import org.hawkular.bus.restclient.RestClient;
import org.hawkular.inventory.api.model.MetricUnit;
import org.jboss.logging.Logger;

import com.google.gson.GsonBuilder;

public class HawkularStorageAdapter implements StorageAdapter {
    private static final Logger LOGGER = Logger.getLogger(HawkularStorageAdapter.class);

    private MonitorServiceConfiguration.StorageAdapter config;
    private Diagnostics diagnostics;
    private ServerIdentifiers selfId;

    public HawkularStorageAdapter() {
    }

    private String getFeedId() {
        return this.selfId.getFullIdentifier();
    }

    @Override
    public MonitorServiceConfiguration.StorageAdapter getStorageAdapterConfiguration() {
        return config;
    }

    @Override
    public void setStorageAdapterConfiguration(MonitorServiceConfiguration.StorageAdapter config) {
        this.config = config;
    }

    @Override
    public void setDiagnostics(Diagnostics diag) {
        this.diagnostics = diag;
    }

    @Override
    public void setSelfIdentifiers(ServerIdentifiers selfId) {
        this.selfId = selfId;
    }

    @Override
    public MetricDataPayloadBuilder createMetricDataPayloadBuilder() {
        return new HawkularMetricDataPayloadBuilder();
    }

    @Override
    public AvailDataPayloadBuilder createAvailDataPayloadBuilder() {
        return new HawkularAvailDataPayloadBuilder();
    }

    @Override
    public void storeMetrics(Set<MetricDataPoint> datapoints) {
        if (datapoints == null || datapoints.isEmpty()) {
            return; // nothing to do
        }

        MetricDataPayloadBuilder payloadBuilder = createMetricDataPayloadBuilder();
        for (MetricDataPoint datapoint : datapoints) {
            Task task = datapoint.getTask();
            String key = task.getKeyGenerator().generateKey(task);
            long timestamp = datapoint.getTimestamp();
            double value = datapoint.getValue();
            payloadBuilder.addDataPoint(key, timestamp, value);
        }

        store(payloadBuilder);

        return;
    }

    @Override
    public void store(MetricDataPayloadBuilder payloadBuilder) {

        String tenantId = this.config.tenantId;
        ((HawkularMetricDataPayloadBuilder) payloadBuilder).setTenantId(tenantId);

        // for now, we need to send it twice:
        // 1) directly to metrics for storage
        // 2) on the message bus for further processing

        // send to metrics
        MetricsOnlyStorageAdapter metricsAdapter = new MetricsOnlyStorageAdapter();
        metricsAdapter.setDiagnostics(diagnostics);
        metricsAdapter.setStorageAdapterConfiguration(getStorageAdapterConfiguration());
        metricsAdapter.setSelfIdentifiers(selfId);
        metricsAdapter.store(((HawkularMetricDataPayloadBuilder) payloadBuilder)
                .toMetricsOnlyMetricDataPayloadBuilder());

        // send to bus
        String jsonPayload = null;
        try {
            // build the URL to the bus interface
            StringBuilder urlStr = Util.getContextUrlString(config.url, config.busContext);
            URL url = new URL(urlStr.toString());

            // build the bus client
            RestClient busClient = new RestClient(url);

            // send the message to the bus
            jsonPayload = payloadBuilder.toPayload().toString();
            busClient.postTopicMessage("HawkularMetricData", jsonPayload, null);

            // looks like everything stored successfully
            // the metrics storage adapter already did this, so don't duplicate the stats here
            //diagnostics.getMetricRate().mark(payloadBuilder.getNumberDataPoints());

        } catch (Throwable t) {
            MsgLogger.LOG.errorFailedToStoreMetricData(t, jsonPayload);
            diagnostics.getStorageErrorRate().mark(1);
        }
    }

    @Override
    public void storeAvails(Set<AvailDataPoint> datapoints) {
        if (datapoints == null || datapoints.isEmpty()) {
            return; // nothing to do
        }

        AvailDataPayloadBuilder payloadBuilder = createAvailDataPayloadBuilder();
        for (AvailDataPoint datapoint : datapoints) {
            Task task = datapoint.getTask();
            String key = task.getKeyGenerator().generateKey(task);
            long timestamp = datapoint.getTimestamp();
            Avail value = datapoint.getValue();
            payloadBuilder.addDataPoint(key, timestamp, value);
        }

        store(payloadBuilder);

        return;
    }

    @Override
    public void store(AvailDataPayloadBuilder payloadBuilder) {

        String tenantId = this.config.tenantId;
        ((HawkularAvailDataPayloadBuilder) payloadBuilder).setTenantId(tenantId);

        // for now, we need to send it twice:
        // 1) directly to h-metrics for storage
        // 2) on the message bus for further processing

        // send to h-metrics
        MetricsOnlyStorageAdapter metricsAdapter = new MetricsOnlyStorageAdapter();
        metricsAdapter.setDiagnostics(diagnostics);
        metricsAdapter.setStorageAdapterConfiguration(getStorageAdapterConfiguration());
        metricsAdapter.setSelfIdentifiers(selfId);
        metricsAdapter.store(((HawkularAvailDataPayloadBuilder) payloadBuilder)
                .toMetricsOnlyAvailDataPayloadBuilder());

        // send to bus
        String jsonPayload = null;
        try {
            // build the URL to the bus interface
            StringBuilder urlStr = Util.getContextUrlString(config.url, config.busContext);
            URL url = new URL(urlStr.toString());

            // build the bus client
            RestClient busClient = new RestClient(url);

            // send the message to the bus
            jsonPayload = payloadBuilder.toPayload().toString();
            busClient.postTopicMessage("HawkularAvailData", jsonPayload, null);

            // looks like everything stored successfully
            // the metrics storage adapter already did this, so don't duplicate the stats here
            //diagnostics.getAvailRate().mark(payloadBuilder.getNumberDataPoints());

        } catch (Throwable t) {
            MsgLogger.LOG.errorFailedToStoreAvailData(t, jsonPayload);
            diagnostics.getStorageErrorRate().mark(1);
        }
    }

    @Override
    public void storeResourceType(ResourceType<?, ?, ?, ?> resourceType) {
        if (resourceType.isPersisted()) {
            return;
        }

        registerResourceType(resourceType);

        Collection<? extends MetricType> metricTypes = resourceType.getMetricTypes();
        for (MetricType metricType : metricTypes) {
            registerMetricType(metricType);
            relateResourceTypeWithMetricType(resourceType, metricType);
        }
        Collection<? extends AvailType> availTypes = resourceType.getAvailTypes();
        for (AvailType availType : availTypes) {
            registerMetricType(availType);
            relateResourceTypeWithMetricType(resourceType, availType);
        }

        LOGGER.debugf("Stored resource type: %s", resourceType);
    }

    @Override
    public void storeResource(Resource<?, ?, ?, ?, ?> resource) {
        if (resource.isPersisted()) {
            return;
        }

        registerResource(resource);

        Collection<? extends MetricInstance<?, ?, ?>> metricInstances = resource.getMetrics();
        for (MetricInstance<?, ?, ?> metricInstance : metricInstances) {
            registerMetricInstance(metricInstance);
            relateResourceWithMetric(resource, metricInstance);
        }
        Collection<? extends AvailInstance<?, ?, ?>> availInstances = resource.getAvails();
        for (AvailInstance<?, ?, ?> availInstance : availInstances) {
            registerMetricInstance(availInstance);
            relateResourceWithMetric(resource, availInstance);
        }

        LOGGER.debugf("Stored resource: %s", resource);
    }

    private String getInventoryId(NamedObject no) {
        String id;
        if (no.getID().equals(ID.NULL_ID)) {
            id = no.getName().getNameString();
        } else {
            id = no.getID().getIDString();
        }
        return id;
    }

    private void registerResource(Resource<?, ?, ?, ?, ?> resource) {
        if (resource.isPersisted()) {
            return;
        }

        HttpPost request = null;

        try {
            // get the payload in JSON format
            org.hawkular.inventory.api.model.Resource.Blueprint rPojo;
            rPojo = new org.hawkular.inventory.api.model.Resource.Blueprint(getInventoryId(resource),
                    getInventoryId(resource.getResourceType()), resource.getProperties());
            String jsonPayload = new GsonBuilder().create().toJson(rPojo);

            // build the REST URL
            StringBuilder url = Util.getContextUrlString(this.config.url, this.config.inventoryContext);
            url.append("test").append("/"); // environment
            url.append(getFeedId());
            url.append("/resources");

            // now send the REST request
            HttpClient httpclient = HttpClientBuilder.create().build();
            request = new HttpPost(url.toString());
            request.setEntity(new StringEntity(jsonPayload, ContentType.APPLICATION_JSON));

            // make sure we are authenticated
            // http://en.wikipedia.org/wiki/Basic_access_authentication#Client_side
            String base64Encode = Util.base64Encode(this.config.username + ":" + this.config.password);
            request.setHeader("Authorization", "Basic " + base64Encode);
            request.setHeader("Accept", "application/json");

            HttpResponse httpResponse = httpclient.execute(request);
            StatusLine statusLine = httpResponse.getStatusLine();

            // HTTP status of 201 means success, 409 means it already exists; anything else is an error
            if (statusLine.getStatusCode() != 201 && statusLine.getStatusCode() != 409) {
                throw new Exception("status-code=[" + statusLine.getStatusCode() + "], reason=["
                        + statusLine.getReasonPhrase() + "], url=[" + request.getURI() + "]");
            }

            resource.setPersisted(true);
        } catch (Throwable t) {
            MsgLogger.LOG.errorFailedToStoreInventoryData(t);
            throw new RuntimeException("Cannot create resource: " + resource, t);
        } finally {
            if (request != null) {
                request.releaseConnection();
            }
        }

        resource.setPersisted(true);
    }

    private void registerResourceType(ResourceType<?, ?, ?, ?> resourceType) {
        if (resourceType.isPersisted()) {
            return;
        }

        HttpPost request = null;

        try {
            // get the payload in JSON format
            org.hawkular.inventory.api.model.ResourceType.Blueprint rtPojo;
            rtPojo = new org.hawkular.inventory.api.model.ResourceType.Blueprint(getInventoryId(resourceType), "0.1",
                    resourceType.getProperties());
            String jsonPayload = new GsonBuilder().create().toJson(rtPojo);

            // build the REST URL
            StringBuilder url = Util.getContextUrlString(this.config.url, this.config.inventoryContext);
            url.append("resourceTypes");

            // now send the REST request
            HttpClient httpclient = HttpClientBuilder.create().build();
            request = new HttpPost(url.toString());
            request.setEntity(new StringEntity(jsonPayload, ContentType.APPLICATION_JSON));

            // make sure we are authenticated
            // http://en.wikipedia.org/wiki/Basic_access_authentication#Client_side
            String base64Encode = Util.base64Encode(this.config.username + ":" + this.config.password);
            request.setHeader("Authorization", "Basic " + base64Encode);
            request.setHeader("Accept", "application/json");

            HttpResponse httpResponse = httpclient.execute(request);
            StatusLine statusLine = httpResponse.getStatusLine();

            // HTTP status of 201 means success, 409 means it already exists; anything else is an error
            if (statusLine.getStatusCode() != 201 && statusLine.getStatusCode() != 409) {
                throw new Exception("status-code=[" + statusLine.getStatusCode() + "], reason=["
                        + statusLine.getReasonPhrase() + "], url=[" + request.getURI() + "]");
            }

            resourceType.setPersisted(true);
        } catch (Throwable t) {
            MsgLogger.LOG.errorFailedToStoreInventoryData(t);
            throw new RuntimeException("Cannot create resource type: " + resourceType, t);
        } finally {
            if (request != null) {
                request.releaseConnection();
            }
        }

        resourceType.setPersisted(true);
    }

    private void registerMetricInstance(MeasurementInstance<?, ?, ?> measurementInstance) {
        if (measurementInstance.isPersisted()) {
            return;
        }

        HttpPost request = null;

        String metricId = getInventoryId(measurementInstance);
        String metricTypeId = getInventoryId(measurementInstance.getMeasurementType());
        Map<String, Object> metricProps = measurementInstance.getProperties();

        try {
            // get the payload in JSON format
            org.hawkular.inventory.api.model.Metric.Blueprint mtPojo;
            mtPojo = new org.hawkular.inventory.api.model.Metric.Blueprint(metricTypeId, metricId, metricProps);
            String jsonPayload = new GsonBuilder().create().toJson(mtPojo);

            // build the REST URL
            StringBuilder url = Util.getContextUrlString(this.config.url, this.config.inventoryContext);
            url.append("test").append("/"); // environment
            url.append(getFeedId());
            url.append("/metrics");

            // now send the REST request
            HttpClient httpclient = HttpClientBuilder.create().build();
            request = new HttpPost(url.toString());
            request.setEntity(new StringEntity(jsonPayload, ContentType.APPLICATION_JSON));

            // make sure we are authenticated
            // http://en.wikipedia.org/wiki/Basic_access_authentication#Client_side
            String base64Encode = Util.base64Encode(this.config.username + ":" + this.config.password);
            request.setHeader("Authorization", "Basic " + base64Encode);
            request.setHeader("Accept", "application/json");

            HttpResponse httpResponse = httpclient.execute(request);
            StatusLine statusLine = httpResponse.getStatusLine();

            // HTTP status of 201 means success, 409 means it already exists; anything else is an error
            if (statusLine.getStatusCode() != 201 && statusLine.getStatusCode() != 409) {
                throw new Exception("status-code=[" + statusLine.getStatusCode() + "], reason=["
                        + statusLine.getReasonPhrase() + "], url=[" + request.getURI() + "]");
            }
        } catch (Throwable t) {
            MsgLogger.LOG.errorFailedToStoreInventoryData(t);
            throw new RuntimeException("Cannot create metric type: " + metricTypeId, t);
        } finally {
            if (request != null) {
                request.releaseConnection();
            }
        }

        measurementInstance.setPersisted(true);
    }

    private void registerMetricType(MeasurementType measurementType) {
        if (measurementType.isPersisted()) {
            return;
        }

        HttpPost request = null;

        String metricTypeId = getInventoryId(measurementType);
        Map<String, Object> metricTypeProps = measurementType.getProperties();

        try {
            MetricUnit mu = MetricUnit.NONE;
            try {
                if (measurementType instanceof MetricType) {
                    mu = MetricUnit.valueOf(((MetricType) measurementType).getMetricUnits().name());
                }
            } catch (Exception e) {
                // the unit isn't supported
            }

            // get the payload in JSON format
            org.hawkular.inventory.api.model.MetricType.Blueprint mtPojo;
            mtPojo = new org.hawkular.inventory.api.model.MetricType.Blueprint(metricTypeId, mu, metricTypeProps);
            String jsonPayload = new GsonBuilder().create().toJson(mtPojo);

            // build the REST URL
            StringBuilder url = Util.getContextUrlString(this.config.url, this.config.inventoryContext);
            url.append("metricTypes");

            // now send the REST request
            HttpClient httpclient = HttpClientBuilder.create().build();
            request = new HttpPost(url.toString());
            request.setEntity(new StringEntity(jsonPayload, ContentType.APPLICATION_JSON));

            // make sure we are authenticated
            // http://en.wikipedia.org/wiki/Basic_access_authentication#Client_side
            String base64Encode = Util.base64Encode(this.config.username + ":" + this.config.password);
            request.setHeader("Authorization", "Basic " + base64Encode);
            request.setHeader("Accept", "application/json");

            HttpResponse httpResponse = httpclient.execute(request);
            StatusLine statusLine = httpResponse.getStatusLine();

            // HTTP status of 201 means success, 409 means it already exists; anything else is an error
            if (statusLine.getStatusCode() != 201 && statusLine.getStatusCode() != 409) {
                throw new Exception("status-code=[" + statusLine.getStatusCode() + "], reason=["
                        + statusLine.getReasonPhrase() + "], url=[" + request.getURI() + "]");
            }
        } catch (Throwable t) {
            MsgLogger.LOG.errorFailedToStoreInventoryData(t);
            throw new RuntimeException("Cannot create metric type: " + metricTypeId, t);
        } finally {
            if (request != null) {
                request.releaseConnection();
            }
        }

        measurementType.setPersisted(true);
    }

    private void relateResourceWithMetric(Resource<?, ?, ?, ?, ?> resource,
            MeasurementInstance<?, ?, ?> measInstance) {
        HttpPost request = null;

        String resourceId = getInventoryId(resource);
        String metricId = getInventoryId(measInstance);

        try {
            // get the payload in JSON format
            ArrayList<String> id = new ArrayList<>();
            id.add(metricId);
            String jsonPayload = new GsonBuilder().create().toJson(id);

            // build the REST URL
            StringBuilder url = Util.getContextUrlString(this.config.url, this.config.inventoryContext);
            url.append("test").append("/"); // environment
            url.append(getFeedId());
            url.append("/resources").append("/").append(Util.urlEncode(resourceId)).append("/metrics");


            // now send the REST request
            HttpClient httpclient = HttpClientBuilder.create().build();
            request = new HttpPost(url.toString());
            request.setEntity(new StringEntity(jsonPayload, ContentType.APPLICATION_JSON));

            // make sure we are authenticated
            // http://en.wikipedia.org/wiki/Basic_access_authentication#Client_side
            String base64Encode = Util.base64Encode(this.config.username + ":" + this.config.password);
            request.setHeader("Authorization", "Basic " + base64Encode);
            request.setHeader("Accept", "application/json");

            HttpResponse httpResponse = httpclient.execute(request);
            StatusLine statusLine = httpResponse.getStatusLine();

            // HTTP status of 204 means success, 409 means it already exists; anything else is an error
            if (statusLine.getStatusCode() != 204 && statusLine.getStatusCode() != 409) {
                throw new Exception("status-code=[" + statusLine.getStatusCode() + "], reason=["
                        + statusLine.getReasonPhrase() + "], url=[" + request.getURI() + "]");
            }
        } catch (Throwable t) {
            MsgLogger.LOG.errorFailedToStoreInventoryData(t);
            throw new RuntimeException("Cannot associate resource with metric: " + resourceId + "/" + metricId, t);
        } finally {
            if (request != null) {
                request.releaseConnection();
            }
        }
    }

    private void relateResourceTypeWithMetricType(ResourceType<?, ?, ?, ?> resourceType, MeasurementType measType) {
        HttpPost request = null;

        String resourceTypeId = getInventoryId(resourceType);
        String metricTypeId = getInventoryId(measType);

        try {
            // get the payload in JSON format
            String jsonPayload = String.format("{\"id\":\"%s\"}", metricTypeId);

            // build the REST URL
            StringBuilder url = Util.getContextUrlString(this.config.url, this.config.inventoryContext);
            url.append("resourceTypes").append("/").append(Util.urlEncode(resourceTypeId)).append("/metricTypes");

            // now send the REST request
            HttpClient httpclient = HttpClientBuilder.create().build();
            request = new HttpPost(url.toString());
            request.setEntity(new StringEntity(jsonPayload, ContentType.APPLICATION_JSON));

            // make sure we are authenticated
            // http://en.wikipedia.org/wiki/Basic_access_authentication#Client_side
            String base64Encode = Util.base64Encode(this.config.username + ":" + this.config.password);
            request.setHeader("Authorization", "Basic " + base64Encode);
            request.setHeader("Accept", "application/json");

            HttpResponse httpResponse = httpclient.execute(request);
            StatusLine statusLine = httpResponse.getStatusLine();

            // HTTP status of 204 means success, 409 means it already exists; anything else is an error
            if (statusLine.getStatusCode() != 204 && statusLine.getStatusCode() != 409) {
                throw new Exception("status-code=[" + statusLine.getStatusCode() + "], reason=["
                        + statusLine.getReasonPhrase() + "], url=[" + request.getURI() + "]");
            }
        } catch (Throwable t) {
            MsgLogger.LOG.errorFailedToStoreInventoryData(t);
            throw new RuntimeException("Cannot associate resource type with metric type: " + resourceTypeId + "/"
                    + metricTypeId, t);
        } finally {
            if (request != null) {
                request.releaseConnection();
            }
        }
    }
}