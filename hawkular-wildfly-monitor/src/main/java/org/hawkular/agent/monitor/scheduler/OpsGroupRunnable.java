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
package org.hawkular.agent.monitor.scheduler;

import java.io.IOException;
import java.net.ConnectException;
import java.util.Map;

import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
import org.hawkular.agent.monitor.scheduler.config.SchedulerConfiguration;
import org.hawkular.agent.monitor.service.ServerIdentifiers;
import org.hawkular.agent.monitor.service.Util;
import org.hawkular.agent.monitor.storage.HttpClientBuilder;
import org.hawkular.dmrclient.Address;
import org.hawkular.dmrclient.JBossASClient;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;

import com.squareup.okhttp.Callback;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

/**
 * Job that regularly polls the server for jobs
 * to run and executes them.
 *
 * @author Heiko W. Rupp
 */
public class OpsGroupRunnable implements Runnable {
    private static final Logger LOG = Logger.getLogger(OpsGroupRunnable.class);

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");
    private ServerIdentifiers selfIdentifiers;

    private String baseuri;
    private JBossASClient asClient;
    private OkHttpClient httpClient;


    public OpsGroupRunnable(SchedulerConfiguration schedulerConfig,
                            ServerIdentifiers selfIdentifiers,
            ModelControllerClientFactory factory,
            HttpClientBuilder httpClientBuilder) {

        this.selfIdentifiers = selfIdentifiers;

        asClient = new JBossASClient(factory.createClient());
        MonitorServiceConfiguration.StorageAdapter adapterConfig = schedulerConfig.getStorageAdapterConfig();
        try {
            httpClient = httpClientBuilder.getHttpClient();
            baseuri = Util.getContextUrlString(adapterConfig.url, "/hawkular/opsq").toString();
        } catch (Exception e) {
            throw new RuntimeException("Cannot initialize OpsGroupRunnable", e);
        }
    }


    @Override
    public void run() {

        Request request = null;

        try {
            String uri = baseuri + "/" + selfIdentifiers.getFullIdentifier();

            request = new Request.Builder()
                    .url(uri)
                    .addHeader("Accept", JSON_MEDIA_TYPE.toString())
                    .get()
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Request request, IOException e) {
                    if (e instanceof ConnectException) {
                        LOG.warn("Reading job failed: target " + baseuri + " seems down");
                    } else {
                        LOG.warn("Reading job failed: " + e);
                    }
                }

                @Override
                public void onResponse(Response response) throws IOException {

                    if (response.code() == 204) {
                        return; // No content, nothing to do
                    }

                    String content = response.body().string();

                    OpsRequest map = Util.fromJson(content, OpsRequest.class);

                    String action = map.getAction();
                    String operationId = map.getId();
                    String tenantId = map.getTenantId();

                    // "[Local~/deployment=hawkular-avail-creator.war]"
                    String resId = map.getResourceId();
                    resId = resId.substring(resId.indexOf("~/") + 2);
                    if (resId.endsWith("]")) {
                        resId = resId.substring(0, resId.length() - 1);
                    }

                    Address address = Address.parse(resId);
                    LOG.debug("Executing " + address + "/:" + action);

                    ModelNode mrequest = JBossASClient.createRequest(action, address);
                    try {
                        ModelNode result = asClient.execute(mrequest);
                        OpsResult outcome = toOutcome(result);

                        LOG.debug("Outcome " + outcome);

                        submitResult(operationId, tenantId, outcome);
                    } catch (Exception e) {
                        throw new IOException(e);
                    }

                }
            });

        }
        catch (Throwable t) {
            LOG.warn("Error: " + t.getMessage());
        }
    }

    private OpsResult toOutcome(ModelNode result) {
        Map<String, Object> tmp = Util.fromJson(result.toJSONString(true), Map.class);
        if (tmp.get("outcome").equals("success")) {
            return new OpsResult(true);
        }

        boolean rolledBack = (boolean) tmp.get("rolled-back");

        OpsResult or = new OpsResult(false, (String) tmp.get("failure-description"), rolledBack);

        return or;
    }

    private void submitResult(String operationId, String tenantId, OpsResult result) throws Exception {

        Request request;

        String uri = baseuri + "/" + selfIdentifiers.getFullIdentifier() + "/" + operationId;
        String json = Util.toJson(result);
        RequestBody body = RequestBody.create(JSON_MEDIA_TYPE,json);

        request = new Request.Builder()
                .url(uri)
                .post(body)
                .addHeader("Hawkular-Tenant", tenantId)
                .build();

        // Asynchronous POST
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
                LOG.warn("Sending of response failed: " + e);
            }

            @Override
            public void onResponse(Response response) throws IOException {
                if (response.code()!=200) {
                    LOG.warn("Send failed : " + response.message());
                }

            }
        });
    }
}

class OpsRequest {

    String id;
    String action;
    String tenantId;
    String resourceId;

    public OpsRequest() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }
}

class OpsResult {
    boolean success;
    String result;
    boolean rolledBack;

    public OpsResult(boolean success) {
        this.success = success;
    }

    public OpsResult(boolean success, String result, boolean rolledBack) {
        this.success = success;
        this.result = result;
        this.rolledBack = rolledBack;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public boolean isRolledBack() {
        return rolledBack;
    }

    public void setRolledBack(boolean rolledBack) {
        this.rolledBack = rolledBack;
    }

    @Override
    public String toString() {
        String s = "OpsResult{success=" + success;
        if (!success) {
            s += ", result='" + result + '\'' +
                    ", rolledBack=" + rolledBack;
        }
        s += '}';
        return s;
    }
}
