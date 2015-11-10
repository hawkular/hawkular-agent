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
package org.hawkular.cmdgw.ws.test;

import java.util.List;

import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.Resource;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class ExecuteOperationCommandITest extends AbstractCommandITest {

    @Test(dependsOnGroups = { "no-dependencies" }, groups = "exclusive-inventory-access")
    public void testExecuteOperation() throws Throwable {
        waitForAccountsAndInventory();

        List<Resource> wfs = getResources("/test/resources", 1);
        AssertJUnit.assertEquals(1, wfs.size());
        CanonicalPath wfPath = wfs.get(0).getPath();
        String feedId = wfPath.ids().getFeedId();
        final String deploymentName = "hawkular-wildfly-agent-helloworld-war.war";
        Resource deployment = getResource("/feeds/" + feedId + "/resourceTypes/Deployment/resources",
                (r -> r.getId().endsWith("=" + deploymentName)));

        String req = "ExecuteOperationRequest={\"authentication\":" + authentication + ", " //
                + "\"resourcePath\":\"" + deployment.getPath().toString() + "\"," //
                + "\"operationName\":\"Redeploy\"" //
                + "}";
        String response = "ExecuteOperationResponse={" //
                + "\"operationName\":\"Redeploy\"," //
                + "\"resourcePath\":\"" + deployment.getPath() + "\"," //
                + "\"destinationSessionId\":\"{{sessionId}}\"," //
                + "\"status\":\"OK\"," //
                + "\"message\":\"Performed [Redeploy] on a [DMR Node] given by Inventory path [" //
                + deployment.getPath() + "]\"" //
                + "}";
        try (TestWebSocketClient testClient =
                TestWebSocketClient.builder() //
                        .url(baseGwUri + "/ui/ws") //
                        .expectWelcome(req) //
                        .expectGenericSuccess(wfPath.ids().getFeedId()) //
                        .expectText(response) //
                        .build()) {
            testClient.validate(10000);
        }

    }

}
