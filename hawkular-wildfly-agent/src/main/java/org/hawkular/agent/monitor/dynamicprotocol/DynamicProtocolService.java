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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.hawkular.agent.monitor.log.AgentLoggers;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.util.ThreadFactoryGenerator;

/**
 * A collection of {@link DynamicEndpointService}s that all handle a single protocol.
 */
public class DynamicProtocolService {
    private static final MsgLogger log = AgentLoggers.getLogger(DynamicProtocolService.class);

    public static class Builder {
        private String name;
        private Map<String, DynamicEndpointService> endpointServices = new HashMap<>();

        private Builder() {
        }

        public DynamicProtocolService build() {
            return new DynamicProtocolService(name, Collections.synchronizedMap(endpointServices));
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder endpointService(DynamicEndpointService endpointService) {
            endpointServices.put(endpointService.getMonitoredEndpoint().getName(), endpointService);
            return this;
        }
    }

    public static Builder builder(String name) {
        return new Builder().name(name);
    }

    private final String name;
    private final Map<String, DynamicEndpointService> endpointServices;
    private ScheduledExecutorService threadPool;
    private final Map<String, ScheduledFuture<?>> jobs;

    public DynamicProtocolService(String name, Map<String, DynamicEndpointService> endpointServices) {
        this.name = name;
        this.endpointServices = endpointServices;
        this.jobs = new HashMap<>();
    }

    /**
     * @return the protocol name
     */
    public String getName() {
        return name;
    }

    /**
     * @return a shallow copy of all endpoint services
     */
    public Map<String, DynamicEndpointService> getDynamicEndpointServices() {
        synchronized (endpointServices) {
            return new HashMap<>(endpointServices);
        }
    }

    public void start() {
        ThreadFactory threadFactory = ThreadFactoryGenerator.generateFactory(true,
                "Hawkular WildFly Agent Dynamic Protocol Service Thread Pool");
        threadPool = Executors.newScheduledThreadPool(1, threadFactory);

        for (DynamicEndpointService service : getDynamicEndpointServices().values()) {
            startServiceAndItsJob(service);
        }
    }

    public void stop() {
        // interrupt any services that might be doing work
        try {
            threadPool.shutdown();
            threadPool.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // reset flag
        } finally {
            jobs.clear();
        }

        // now stop all the services
        for (DynamicEndpointService service : getDynamicEndpointServices().values()) {
            service.stop();
        }
    }

    private void startServiceAndItsJob(DynamicEndpointService service) {
        service.start();

        // schedule the service to do its work periodically
        int interval = service.getMonitoredEndpoint().getEndpointConfiguration().getInterval();
        TimeUnit timeUnits = service.getMonitoredEndpoint().getEndpointConfiguration().getTimeUnits();
        long startDelay = new Random().nextInt(60); // spread out the initial start times of the jobs
        long intervalSecs = TimeUnit.SECONDS.convert(interval, timeUnits);
        ScheduledFuture<?> job = threadPool.scheduleWithFixedDelay(service, startDelay, intervalSecs,
                TimeUnit.SECONDS);
        jobs.put(service.getMonitoredEndpoint().getName(), job);
    }

    /**
     * This will add a new endpoint service to the list. Once added, the new service
     * will immediately be started.
     *
     * @param newEndpointService the new service to add and start
     */
    public void add(DynamicEndpointService newEndpointService) {
        if (newEndpointService == null) {
            throw new IllegalArgumentException("New endpoint service must not be null");
        }

        endpointServices.put(newEndpointService.getMonitoredEndpoint().getName(), newEndpointService);
        startServiceAndItsJob(newEndpointService);
        log.infoAddedDynamicEndpointService(newEndpointService.toString());
    }

    /**
     * This will stop the given endpoint service and remove it from the list of endpoint services.
     *
     * @param name identifies the endpoint service to remove
     */
    public void remove(String name) {
        DynamicEndpointService des = endpointServices.remove(name);
        if (des != null) {
            log.infoRemovedDynamicEndpointService(des.toString());
        }

        ScheduledFuture<?> doomedJob = jobs.get(name);
        if (doomedJob != null) {
            doomedJob.cancel(true);
        }
    }
}
