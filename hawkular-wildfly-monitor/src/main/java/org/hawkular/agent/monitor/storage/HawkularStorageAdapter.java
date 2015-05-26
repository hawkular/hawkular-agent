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

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.Set;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.hawkular.agent.monitor.api.Avail;
import org.hawkular.agent.monitor.api.AvailDataPayloadBuilder;
import org.hawkular.agent.monitor.api.MetricDataPayloadBuilder;
import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
import org.hawkular.agent.monitor.inventory.AvailInstance;
import org.hawkular.agent.monitor.inventory.AvailType;
import org.hawkular.agent.monitor.inventory.ID;
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
import org.jboss.as.controller.client.helpers.MeasurementUnit;
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
    public void storeResourceType(ResourceType<?, ?> resourceType) {
        registerResourceType(resourceType);
        Collection<? extends MetricType> metricTypes = resourceType.getMetricTypes();
        for (MetricType metricType : metricTypes) {
            registerMetricType(getInventoryId(metricType), metricType.getMetricUnits());
            relateResourceTypeWithMetricType(getInventoryId(resourceType), getInventoryId(metricType));
        }
        Collection<? extends AvailType> availTypes = resourceType.getAvailTypes();
        for (AvailType availType : availTypes) {
            registerMetricType(getInventoryId(availType), MeasurementUnit.NONE);
            relateResourceTypeWithMetricType(getInventoryId(resourceType), getInventoryId(availType));
        }
    }

    @Override
    public void storeResource(Resource<?, ?, ?, ?> resource) {
        registerResource(resource);
        Collection<? extends MetricInstance<?, ?, ?>> metricInstances = resource.getMetrics();
        for (MetricInstance<?, ?, ?> metricInstance : metricInstances) {
            registerMetricInstance(getInventoryId(metricInstance), getInventoryId(metricInstance.getMetricType()));
            relateResourceWithMetric(getInventoryId(resource), getInventoryId(metricInstance));
        }
        Collection<? extends AvailInstance<?, ?, ?>> availInstances = resource.getAvails();
        for (AvailInstance<?, ?, ?> availInstance : availInstances) {
            registerMetricInstance(getInventoryId(availInstance), getInventoryId(availInstance.getAvailType()));
            relateResourceWithMetric(getInventoryId(resource), getInventoryId(availInstance));
        }
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

    private String urlEncode(String str) {
        try {
            return URLEncoder.encode(str, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("JVM does not support UTF-8");
        }
    }

    private void registerResource(Resource<?, ?, ?, ?> resource) {
        HttpPost request = null;

        try {
            // get the payload in JSON format
            org.hawkular.inventory.api.model.Resource.Blueprint rPojo;
            rPojo = new org.hawkular.inventory.api.model.Resource.Blueprint(getInventoryId(resource),
                    getInventoryId(resource.getResourceType()));
            String jsonPayload = new GsonBuilder().create().toJson(rPojo);

            // build the REST URL
            StringBuilder url = Util.getContextUrlString(this.config.url, this.config.inventoryContext);
            url.append(this.config.tenantId).append("/");
            url.append("test").append("/"); // environment
            url.append(getFeedId());
            url.append("/resources");

            // now send the REST request
            DefaultHttpClient httpclient = new DefaultHttpClient();
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
            throw new RuntimeException("Cannot create resource: " + resource, t);
        } finally {
            if (request != null) {
                request.releaseConnection();
            }
        }
    }

    private void registerResourceType(ResourceType<?, ?> resourceType) {
        HttpPost request = null;

        try {
            // get the payload in JSON format
            org.hawkular.inventory.api.model.ResourceType.Blueprint rtPojo;
            rtPojo = new org.hawkular.inventory.api.model.ResourceType.Blueprint(getInventoryId(resourceType), "0.1");
            String jsonPayload = new GsonBuilder().create().toJson(rtPojo);

            // build the REST URL
            StringBuilder url = Util.getContextUrlString(this.config.url, this.config.inventoryContext);
            url.append(this.config.tenantId);
            url.append("/resourceTypes");

            // now send the REST request
            DefaultHttpClient httpclient = new DefaultHttpClient();
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
            throw new RuntimeException("Cannot create resource type: " + resourceType, t);
        } finally {
            if (request != null) {
                request.releaseConnection();
            }
        }
    }

    private void registerMetricInstance(String metricId, String metricTypeId) {
        HttpPost request = null;

        try {
            // get the payload in JSON format
            org.hawkular.inventory.api.model.Metric.Blueprint mtPojo;
            mtPojo = new org.hawkular.inventory.api.model.Metric.Blueprint(metricTypeId, metricId);
            String jsonPayload = new GsonBuilder().create().toJson(mtPojo);

            // build the REST URL
            StringBuilder url = Util.getContextUrlString(this.config.url, this.config.inventoryContext);
            url.append(this.config.tenantId).append("/");
            url.append("test").append("/"); // environment
            url.append(getFeedId());
            url.append("/metrics");

            // now send the REST request
            DefaultHttpClient httpclient = new DefaultHttpClient();
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
    }

    private void registerMetricType(String metricTypeId, MeasurementUnit metricTypeUnits) {
        HttpPost request = null;

        try {
            MetricUnit mu;
            try {
                mu = MetricUnit.valueOf(metricTypeUnits.name());
            } catch (Exception e) {
                mu = MetricUnit.NONE;
            }

            // get the payload in JSON format
            org.hawkular.inventory.api.model.MetricType.Blueprint mtPojo;
            mtPojo = new org.hawkular.inventory.api.model.MetricType.Blueprint(metricTypeId, mu);
            String jsonPayload = new GsonBuilder().create().toJson(mtPojo);

            // build the REST URL
            StringBuilder url = Util.getContextUrlString(this.config.url, this.config.inventoryContext);
            url.append(this.config.tenantId);
            url.append("/metricTypes");

            // now send the REST request
            DefaultHttpClient httpclient = new DefaultHttpClient();
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
    }

    private void relateResourceWithMetric(String resourceId, String metricId) {
        HttpPost request = null;

        try {
            // get the payload in JSON format
            String jsonPayload = String.format("{\"id\":\"%s\"}", metricId);

            // build the REST URL
            StringBuilder url = Util.getContextUrlString(this.config.url, this.config.inventoryContext);
            url.append(this.config.tenantId).append("/");
            url.append("test").append("/"); // environment
            url.append(getFeedId());
            url.append("/resources").append("/").append(urlEncode(resourceId)).append("/metrics");


            // now send the REST request
            DefaultHttpClient httpclient = new DefaultHttpClient();
            request = new HttpPost(url.toString());
            request.setEntity(new StringEntity(jsonPayload, ContentType.APPLICATION_JSON));

            // make sure we are authenticated
            // http://en.wikipedia.org/wiki/Basic_access_authentication#Client_side
            String base64Encode = Util.base64Encode(this.config.username + ":" + this.config.password);
            request.setHeader("Authorization", "Basic " + base64Encode);
            request.setHeader("Accept", "application/json");

            HttpResponse httpResponse = httpclient.execute(request);
            StatusLine statusLine = httpResponse.getStatusLine();

            // HTTP status of 204 means success
            if (statusLine.getStatusCode() != 204) {
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

    private void relateResourceTypeWithMetricType(String resourceTypeId, String metricTypeId) {
        HttpPost request = null;

        try {
            // get the payload in JSON format
            String jsonPayload = String.format("{\"id\":\"%s\"}", metricTypeId);

            // build the REST URL
            StringBuilder url = Util.getContextUrlString(this.config.url, this.config.inventoryContext);
            url.append(this.config.tenantId);
            url.append("/resourceTypes").append("/").append(urlEncode(resourceTypeId)).append("/metricTypes");

            // now send the REST request
            DefaultHttpClient httpclient = new DefaultHttpClient();
            request = new HttpPost(url.toString());
            request.setEntity(new StringEntity(jsonPayload, ContentType.APPLICATION_JSON));

            // make sure we are authenticated
            // http://en.wikipedia.org/wiki/Basic_access_authentication#Client_side
            String base64Encode = Util.base64Encode(this.config.username + ":" + this.config.password);
            request.setHeader("Authorization", "Basic " + base64Encode);
            request.setHeader("Accept", "application/json");

            HttpResponse httpResponse = httpclient.execute(request);
            StatusLine statusLine = httpResponse.getStatusLine();

            // HTTP status of 204 means success
            if (statusLine.getStatusCode() != 204) {
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