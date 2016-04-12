/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.agent.monitor.dynamicprotocol;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.hawkular.agent.monitor.util.ThreadFactoryGenerator;

/**
 * A collection of {@link DynamicEndpointService}s that all handle a single protocol.
 */
public class DynamicProtocolService {

    public static class Builder {
        private Map<String, DynamicEndpointService> endpointServices = new HashMap<>();

        public DynamicProtocolService build() {
            return new DynamicProtocolService(Collections.unmodifiableMap(endpointServices));
        }

        public Builder endpointService(DynamicEndpointService endpointService) {
            endpointServices.put(endpointService.getMonitoredEndpoint().getName(), endpointService);
            return this;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final Map<String, DynamicEndpointService> endpointServices;
    private ScheduledExecutorService threadPool;

    public DynamicProtocolService(Map<String, DynamicEndpointService> endpointServices) {
        this.endpointServices = endpointServices;
    }

    public Map<String, DynamicEndpointService> getDynamicEndpointServices() {
        return endpointServices;
    }

    public void start() {
        ThreadFactory threadFactory = ThreadFactoryGenerator.generateFactory(true,
                "Hawkular WildFly Agent Dynamic Protocol Service Thread Pool");
        threadPool = Executors.newScheduledThreadPool(1, threadFactory);

        for (DynamicEndpointService service : endpointServices.values()) {
            service.start();

            // schedule the service to do its work periodically
            int interval = service.getMonitoredEndpoint().getEndpointConfiguration().getInterval();
            TimeUnit timeUnits = service.getMonitoredEndpoint().getEndpointConfiguration().getTimeUnits();
            long startDelay = new Random().nextInt(60); // spread out the initial start times of the jobs
            long intervalSecs = timeUnits.convert(interval, timeUnits);
            threadPool.scheduleWithFixedDelay(service, startDelay, intervalSecs, TimeUnit.SECONDS);
        }
    }

    public void stop() {
        // interrupt any services that might be doing work
        try {
            threadPool.shutdown();
            threadPool.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // reset flag
        }

        // now stop all the services
        for (DynamicEndpointService service : endpointServices.values()) {
            service.stop();
        }
    }
}
