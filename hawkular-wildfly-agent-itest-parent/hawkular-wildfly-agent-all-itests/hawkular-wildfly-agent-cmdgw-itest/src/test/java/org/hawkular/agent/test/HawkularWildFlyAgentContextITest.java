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
package org.hawkular.agent.test;

import org.hawkular.agent.monitor.api.HawkularAgentContext;
import org.hawkular.agent.ws.test.AbstractCommandITest;
import org.hawkular.agent.ws.test.DatasourceCommandITest;
import org.hawkular.inventory.api.model.Entity;
import org.junit.Assert;
import org.testng.annotations.Test;

/**
 * Tests accessing the {@link HawkularAgentContext} API that is obtained via JNDI.
 * This assumes the example-jndi WAR is deployed in the test app server.
 */
public class HawkularWildFlyAgentContextITest extends AbstractCommandITest {
    public static final String GROUP = "HawkularWildFlyAgentContextITest";

    @Override
    protected String getTenantId() {
        // see org.hawkular.agent.example.HawkularWildFlyAgentProvider.TENANT_ID
        // if that is non-null, we want to return that string; otherwise, just return our superclass's tenant
        return super.getTenantId();
    }

    @Test(groups = { GROUP }, dependsOnGroups = { DatasourceCommandITest.GROUP })
    public void testAgentFromJNDI() throws Throwable {
        waitForAccountsAndInventory();

        // this should not exist yet
        Optional<?> resource = getBlueprintsByType(hawkularFeedId, "MyAppResourceType")
                .entrySet().stream()
                .filter(e -> ((Entity.Blueprint)(e.getValue())).getId().contains("ITest Resource ID"))
                .findFirst();
        Assert.assertFalse(resource.isPresent());

        getWithRetries(getExampleJndiWarCreateResourceUrl("ITest Resource ID"));

        // see that the new resource has been persisted to hawkular-inventory
        waitForResourceContaining(hawkularFeedId, "MyAppResourceType", "ITest Resource ID",
                5000, 5);

        getWithRetries(getExampleJndiWarSendMetricUrl("ITest Metric Key", 123.0));
        getWithRetries(getExampleJndiWarSendStringMetricUrl("ITest Metric Key", "ITest Val"));
        getWithRetries(getExampleJndiWarSendAvailUrl("ITest Avail Key", "DOWN"));
        getWithRetries(getExampleJndiWarRemoveResourceUrl("ITest Resource ID"));

        // this should not exist anymore
        waitForNoResourceContaining(hawkularFeedId, "MyAppResourceType", "ITest Resource ID",
                5000, 5);
    }

    private String getExampleJndiWarCreateResourceUrl(String newResourceID) {
        return getExampleJndiWarServletUrl(String.format("newResourceID=%s", newResourceID));
    }

    private String getExampleJndiWarRemoveResourceUrl(String oldResourceID) {
        return getExampleJndiWarServletUrl(String.format("oldResourceID=%s", oldResourceID));
    }

    private String getExampleJndiWarSendMetricUrl(String metricKey, double metricValue) {
        return getExampleJndiWarServletUrl(String.format("metricKey=%s&metricValue=%f", metricKey, metricValue));
    }

    private String getExampleJndiWarSendStringMetricUrl(String metricKey, String metricValue) {
        return getExampleJndiWarServletUrl(String.format("metricKey=%s&metricValue=%s", metricKey, metricValue));
    }

    private String getExampleJndiWarSendAvailUrl(String availKey, String availValue) {
        return getExampleJndiWarServletUrl(String.format("availKey=%s&availValue=%s", availKey, availValue));
    }

    private String getExampleJndiWarServletUrl(String params) {
        return String.format("%s/MyAppServlet?%s", getExampleJndiWarUrl(), params);
    }

    private String getExampleJndiWarUrl() {
        return String.format("http://%s:%d/hawkular-wildfly-agent-example-jndi", hawkularHost, hawkularHttpPort);
    }
}
