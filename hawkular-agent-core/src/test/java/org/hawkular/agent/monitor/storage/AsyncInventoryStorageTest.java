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
package org.hawkular.agent.monitor.storage;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.hawkular.agent.monitor.api.InventoryEvent;
import org.hawkular.agent.monitor.api.SamplingService;
import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration;
import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.diagnostics.DiagnosticsImpl;
import org.hawkular.agent.monitor.inventory.AttributeLocation;
import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.Interval;
import org.hawkular.agent.monitor.inventory.MetricType;
import org.hawkular.agent.monitor.inventory.MonitoredEndpoint;
import org.hawkular.agent.monitor.inventory.Name;
import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.inventory.ResourceManager;
import org.hawkular.agent.monitor.inventory.ResourceType;
import org.hawkular.agent.monitor.inventory.ResourceTypeManager;
import org.hawkular.agent.monitor.inventory.SupportedMetricType;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.codahale.metrics.MetricRegistry;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * @author Joel Takvorian
 */
public class AsyncInventoryStorageTest {

    private AsyncInventoryStorage storage;
    private AgentCoreEngineConfiguration.StorageAdapterConfiguration config;
    private HttpClientBuilder httpClientBuilder;
    private Diagnostics diagnostics;
    private SamplingService<AnyLocation> samplingService;
    private ResourceManager<AnyLocation> resourceManager;
    private ResourceTypeManager<AnyLocation> resourceTypeManager;
    private final List<String> collectedPostCalls = new ArrayList<>();
    private final List<String> collectedDeleteCalls = new ArrayList<>();

    private final MetricType<AnyLocation> MT_1 = new MetricType<>(
            new ID("mt1"),
            new Name("Metric type 1"),
            new AttributeLocation<>(new AnyLocation(""), "foo"),
            new Interval(60, TimeUnit.SECONDS),
            MeasurementUnit.CELSIUS,
            SupportedMetricType.GAUGE,
            null,
            null);
    private final ResourceType<AnyLocation> RT_1 = ResourceType.<AnyLocation> builder()
            .id(new ID("rt1"))
            .name(new Name("Resource Type 1"))
            .location(new AnyLocation("/1"))
            .metricTypes(Collections.singleton(MT_1))
            .build();
    private final ResourceType<AnyLocation> RT_2 = ResourceType.<AnyLocation> builder()
            .id(new ID("rt2"))
            .name(new Name("Resource Type 2"))
            .location(new AnyLocation("/2"))
            .build();
    private final Resource<AnyLocation> R_1 = Resource.<AnyLocation>builder()
            .id(new ID("r1"))
            .name(new Name("Resource 1"))
            .location(new AnyLocation("/1/1"))
            .type(RT_1)
            .build();
    private final Resource<AnyLocation> R_2 = Resource.<AnyLocation>builder()
            .id(new ID("r2"))
            .name(new Name("Resource 2"))
            .location(new AnyLocation("/2/2"))
            .type(RT_2)
            .build();

    @Before
    public void setUp() throws IOException {
        // AsyncInventoryStorage
        config = mock(AgentCoreEngineConfiguration.StorageAdapterConfiguration.class);
        when(config.getUrl()).thenReturn("http://ignore");
        when(config.getInventoryContext()).thenReturn("ignore");
        httpClientBuilder = mockHttp();
        diagnostics = new DiagnosticsImpl(null, new MetricRegistry(), "feed_id");
        storage = new AsyncInventoryStorage("feed_id", config, httpClientBuilder, diagnostics);

        // Mock SamplingService > MonitoredEndpoint > EndpointConfiguration
        AgentCoreEngineConfiguration.EndpointConfiguration endpointConfiguration
                = mock(AgentCoreEngineConfiguration.EndpointConfiguration.class);
        when(endpointConfiguration.getName()).thenReturn("");
        MonitoredEndpoint<AgentCoreEngineConfiguration.EndpointConfiguration> endpoint = MonitoredEndpoint.of(
                endpointConfiguration, null);
        samplingService = mock(SamplingService.class);
        when(samplingService.getMonitoredEndpoint()).thenReturn(endpoint);

        // ResourceManager and ResourceTypeManager
        resourceManager = new ResourceManager<>();
        resourceTypeManager = new ResourceTypeManager<>(Arrays.asList(RT_1, RT_2));
        resourceManager.addResource(R_1);
        resourceManager.addResource(R_2);
    }

    private HttpClientBuilder mockHttp() throws IOException {
        OkHttpClient httpClient = mock(OkHttpClient.class);
        when(httpClient.newCall(any(Request.class))).then(invocation -> {
            Call call = mock(Call.class);
            when(call.execute()).thenReturn(new Response.Builder()
                    .code(200)
                    .request((Request) invocation.getArguments()[0])
                    .protocol(Protocol.HTTP_1_1)
                    .body(mock(ResponseBody.class))
                    .build());
            return call;
        });
        HttpClientBuilder httpClientBuilder = mock(HttpClientBuilder.class);
        when(httpClientBuilder.getHttpClient()).thenReturn(httpClient);
        when(httpClientBuilder.buildJsonDeleteRequest(anyString(), any(Map.class)))
                .then(invocation -> {
                    String url = (String) invocation.getArguments()[0];
                    collectedDeleteCalls.add(url);
                    return new Request.Builder().url(url).build();
                });
        when(httpClientBuilder.buildJsonPostRequest(anyString(), any(Map.class), anyString()))
                .then(invocation -> {
                    String url = (String) invocation.getArguments()[0];
                    collectedPostCalls.add(url);
                    return new Request.Builder().url(url).build();
                });
        return httpClientBuilder;
    }

    private static void expectCalls(List<String> collected, String... urls) {
        Assert.assertTrue(urls.length == collected.size());
        int i = 0;
        for (String collectedUrl : collected) {
            Assert.assertEquals(urls[i++], collectedUrl);
        }
        collected.clear();
    }

    @Test
    public void testDiscoverySequence() throws InterruptedException {
        storage.receivedEvent(InventoryEvent.discovery(
                samplingService,
                resourceManager,
                resourceTypeManager,
                resourceManager.getResourcesBreadthFirst(),
                Collections.emptyList()));
        Assert.assertEquals("Unexpected DELETE calls: " + collectedDeleteCalls, 0, collectedDeleteCalls.size());
        expectCalls(collectedPostCalls,
                "http://ignore/ignore/import");

        // Make sure persisted time is correct
        final long initialTime = R_1.getPersistedTime();
        Assert.assertTrue(initialTime > 0);
        Assert.assertEquals(initialTime, R_2.getPersistedTime());
        Assert.assertEquals(initialTime, RT_1.getPersistedTime());

        Thread.sleep(10);

        // Next run with no discovered resource
        storage.receivedEvent(InventoryEvent.discovery(
                samplingService,
                resourceManager,
                resourceTypeManager,
                Collections.emptyList(),
                Collections.emptyList()));
        Assert.assertEquals("Unexpected DELETE calls: " + collectedDeleteCalls, 0, collectedDeleteCalls.size());
        Assert.assertEquals(0, collectedPostCalls.size());
        // Persistence time hasn't changed
        Assert.assertEquals(initialTime, RT_1.getPersistedTime());
        Assert.assertEquals(initialTime, R_1.getPersistedTime());

        Thread.sleep(10);

        // Next run with 1 discovered resource under R_2
        Resource<AnyLocation> r3 = Resource.<AnyLocation>builder()
                .id(new ID("r3"))
                .name(new Name("Resource 3"))
                .location(new AnyLocation("/2/2/3"))
                .type(RT_2)
                .parent(R_2)
                .build();
        resourceManager.addResource(r3);
        storage.receivedEvent(InventoryEvent.discovery(
                samplingService,
                resourceManager,
                resourceTypeManager,
                Collections.singletonList(r3),
                Collections.emptyList()));
        Assert.assertEquals("Unexpected DELETE calls: " + collectedDeleteCalls, 0, collectedDeleteCalls.size());
        expectCalls(collectedPostCalls,
                "http://ignore/ignore/import");

        // Persistence time changed only for R_2
        Assert.assertEquals(initialTime, RT_1.getPersistedTime());
        Assert.assertEquals(initialTime, R_1.getPersistedTime());
        final long intermediateR2Time = R_2.getPersistedTime();

        Thread.sleep(10);

        // Next run with removed R_1
        resourceManager.removeResource(R_1);
        storage.receivedEvent(InventoryEvent.discovery(
                samplingService,
                resourceManager,
                resourceTypeManager,
                Collections.emptyList(),
                Collections.singletonList(R_1)));
        expectCalls(collectedDeleteCalls,
                "http://ignore/ignore/resources?ids=r1");
        Assert.assertEquals(0, collectedPostCalls.size());

        // Persistence time hasn't changed
        Assert.assertEquals(initialTime, RT_1.getPersistedTime());
        Assert.assertEquals(intermediateR2Time, R_2.getPersistedTime());

        Thread.sleep(10);

        // Next run with removed r3
        resourceManager.removeResource(r3);
        storage.receivedEvent(InventoryEvent.discovery(
                samplingService,
                resourceManager,
                resourceTypeManager,
                Collections.emptyList(),
                Collections.singletonList(r3)));
        // r3 being a child resource, it triggers an update on its parent
        Assert.assertEquals(0, collectedPostCalls.size());
        expectCalls(collectedDeleteCalls,
                "http://ignore/ignore/resources?ids=r3");

        // Persistence time changed for R2
        Assert.assertEquals(initialTime, RT_1.getPersistedTime());
        Assert.assertEquals(intermediateR2Time, R_2.getPersistedTime());
    }

    @Test
    public void testNonDiscoverySequence() {
        // Initial discovery
        storage.receivedEvent(InventoryEvent.discovery(
                samplingService,
                resourceManager,
                resourceTypeManager,
                resourceManager.getResourcesBreadthFirst(),
                Collections.emptyList()));
        Assert.assertEquals("Unexpected DELETE calls: " + collectedDeleteCalls, 0, collectedDeleteCalls.size());
        expectCalls(collectedPostCalls,
                "http://ignore/ignore/import");

        // Add 1 discovered resource under R_2
        Resource<AnyLocation> r3 = Resource.<AnyLocation>builder()
                .id(new ID("r3"))
                .name(new Name("Resource 3"))
                .location(new AnyLocation("/2/2/3"))
                .type(RT_2)
                .parent(R_2)
                .build();
        resourceManager.addResource(r3);
        storage.receivedEvent(InventoryEvent.addedOrModified(
                samplingService,
                resourceManager,
                Collections.singletonList(r3)));
        Assert.assertEquals("Unexpected DELETE calls: " + collectedDeleteCalls, 0, collectedDeleteCalls.size());
        expectCalls(collectedPostCalls,
                "http://ignore/ignore/import");

        // Next run with removed R_1
        resourceManager.removeResource(R_1);
        storage.receivedEvent(InventoryEvent.removed(
                samplingService,
                resourceManager,
                Collections.singletonList(R_1)));
        expectCalls(collectedDeleteCalls,
                "http://ignore/ignore/resources?ids=r1");
        Assert.assertEquals(0, collectedPostCalls.size());

        // Next run with removed r3
        resourceManager.removeResource(r3);
        storage.receivedEvent(InventoryEvent.removed(
                samplingService,
                resourceManager,
                Collections.singletonList(r3)));
        expectCalls(collectedDeleteCalls,
                "http://ignore/ignore/resources?ids=r3");
        Assert.assertEquals(0, collectedPostCalls.size());
    }

    private static class AnyLocation {
        private String path;

        public AnyLocation(String path) {
            this.path = path;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            AnyLocation that = (AnyLocation) o;

            return path != null ? path.equals(that.path) : that.path == null;
        }

        @Override public int hashCode() {
            return path != null ? path.hashCode() : 0;
        }
    }
}
