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

import java.util.Collections;

import org.hawkular.agent.monitor.config.AgentCoreEngineConfiguration.EndpointConfiguration;
import org.hawkular.agent.monitor.inventory.InventoryIdUtil.ResourceIdParts;
import org.junit.Assert;
import org.junit.Test;

public class InventoryIdUtilTest {

    @Test
    public void testParsingResourceId() {
        ID id;
        ResourceIdParts parts;

        EndpointConfiguration endpointConfig = new EndpointConfiguration("testmanagedserver", true,
                Collections.emptyList(), null, null, null, null, null, null, null, null);
        MonitoredEndpoint<EndpointConfiguration> me = MonitoredEndpoint.<EndpointConfiguration> of(endpointConfig,
                null);

        id = InventoryIdUtil.generateResourceId(me, "/test/id/path");
        Assert.assertEquals("testmanagedserver~/test/id/path", id.toString());
        parts = InventoryIdUtil.parseResourceId(id.getIDString());
        Assert.assertEquals("testmanagedserver", parts.getManagedServerName());
        Assert.assertEquals("/test/id/path", parts.getIdPart());

        // test that you can have ~ in the last part of the ID
        id = InventoryIdUtil.generateResourceId(me, "~/~test/~id/~path");
        Assert.assertEquals("testmanagedserver~~/~test/~id/~path", id.toString());
        parts = InventoryIdUtil.parseResourceId(id.getIDString());
        Assert.assertEquals("testmanagedserver", parts.getManagedServerName());
        Assert.assertEquals("~/~test/~id/~path", parts.getIdPart());
    }
//
//    public static void main(String[] args) throws IOException {
//        OkHttpClient httpClient = new OkHttpClient.Builder()
//                .build();
//        RequestBody body = RequestBody.create(MediaType.parse("application/json"),
//                "[{\"timestamp\": " + System.currentTimeMillis() + ", \"value\": \"toto\"}]");
//        Request request = new Request.Builder()
//                .url("http://localhost:8080/hawkular/metrics/strings/aaa/raw")
//                .addHeader("Hawkular-Tenant", "test")
//                .addHeader("Content-Type", "application/json")
//                .post(body)
//                .build();
//
//        Response response = httpClient.newCall(request).execute();
//
//        System.out.println("POST");
//        System.out.println(response.toString());
//
//        request = new Request.Builder()
//                .url("http://localhost:8080/hawkular/metrics/strings/aaa/raw")
//                .addHeader("Hawkular-Tenant", "test")
//                .addHeader("Accept", "application/json")
////                .addHeader("Accept-Encoding", "gzip")
//                .get()
//                .build();
//        response = httpClient.newCall(request).execute();
//
//        System.out.println("GET");
//        System.out.println(response.headers());
//        System.out.println(response.body().string());
//
//        System.out.println("(NETWORK)");
//        System.out.println(response.networkResponse().headers());
//    }
}
