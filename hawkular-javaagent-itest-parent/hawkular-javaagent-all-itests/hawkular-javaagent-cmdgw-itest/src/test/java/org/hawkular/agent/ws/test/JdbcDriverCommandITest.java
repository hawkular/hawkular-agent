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
package org.hawkular.agent.ws.test;

import java.io.File;
import java.net.URL;
import java.util.Collection;
import java.util.Optional;

import org.hawkular.cmdgw.ws.test.TestWebSocketClient;
import org.hawkular.cmdgw.ws.test.TestWebSocketClient.MessageAnswer;
import org.hawkular.inventory.api.model.ResourceWithType;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;
import org.testng.annotations.Test;

/**
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class JdbcDriverCommandITest extends AbstractCommandITest {
    public static final String GROUP = "JdbcDriverCommandITest";

    private static final boolean ENABLED = true;

    private static final String driverFileNameAfterAdd = "driver-after-add.node.txt";
    private static final String driverName = "mysql";

    private static ModelNode driverAddress() {
        return new ModelNode().add(ModelDescriptionConstants.SUBSYSTEM, "datasources").add("jdbc-driver", driverName);
    }

    @Test(enabled = ENABLED, groups = { GROUP }, dependsOnGroups = { StatisticsControlCommandITest.GROUP })
    public void testAddJdbcDriver() throws Throwable {
        waitForHawkularServerToBeReady();

        ResourceWithType wfResource = getHawkularWildFlyServerResource();
        final ModelNode driverAddress = driverAddress();

        try (ModelControllerClient mcc = newHawkularModelControllerClient()) {
            assertResourceExists(mcc, driverAddress, false);

            /* OK, h2 is there let's add a new MySQL Driver */
            final String driverJarRawUrl = "http://central.maven.org/maven2/mysql/mysql-connector-java/5.1.36/"
                    + "mysql-connector-java-5.1.36.jar";
            URL driverJarUrl = new URL(driverJarRawUrl);
            final String driverJarName = new File(driverJarUrl.getPath()).getName();

            String req = "AddJdbcDriverRequest={\"authentication\":" + authentication + ","
                    + "\"feedId\":\"" + wfResource.getFeedId() + "\","
                    + "\"resourceId\":\"" + wfResource.getId() + "\","
                    + "\"driverName\":\"" + driverName + "\","
                    + "\"driverClass\":\"com.mysql.jdbc.Driver\","
                    + "\"driverXaDatasourceClassName\":\"com.mysql.jdbc.jdbc2.optional.MysqlXADataSource\","
                    + "\"jdbcCompliant\":true,"
                    + "\"driverMajorVersion\":\"5\","
                    + "\"driverMinorVersion\":\"1\","
                    + "\"moduleName\":\"com.mysql\","
                    + "\"driverJarName\":\"" + driverJarName + "\""
                    + "}";
            String response = "AddJdbcDriverResponse={"
                    + "\"driverName\":\"" + driverName + "\","
                    + "\"feedId\":\"" + wfResource.getFeedId() + "\","
                    + "\"resourceId\":\"" + wfResource.getId() + "\","
                    + "\"destinationSessionId\":\"{{sessionId}}\","
                    + "\"status\":\"OK\","
                    + "\"message\":\"Added JDBC Driver: " + driverName + "\""
                    + ""; // server refresh indicator might be after this - so don't look for ending bracked here
            try (TestWebSocketClient testClient = TestWebSocketClient.builder()
                    .url(baseGwUri + "/ui/ws")
                    .expectWelcome(new MessageAnswer(req, driverJarUrl, 0))
                    .expectGenericSuccess(wfResource.getFeedId())
                    .expectText(response, TestWebSocketClient.Answer.CLOSE)
                    .expectClose()
                    .build()) {
                testClient.validate(10000);
            }

            assertNodeEquals(mcc, driverAddress, getClass(), driverFileNameAfterAdd, false);
        }
    }

    @Test(enabled = ENABLED, groups = { GROUP }, dependsOnMethods = { "testAddJdbcDriver" })
    public void testRemoveJdbcDriver() throws Throwable {
        waitForHawkularServerToBeReady();

        Collection<ResourceWithType> drivers = testHelper.getResourceByType(hawkularFeedId, "JDBC Driver", 2);
        Optional<ResourceWithType> driver = drivers.stream()
                .filter(e -> e.getName().contains(driverName))
                .findFirst();
        if (!driver.isPresent()) {
            throw new IllegalStateException("Driver not found");
        }
        ResourceWithType drv = driver.get();
        final ModelNode driverAddress = driverAddress();

        try (ModelControllerClient mcc = newHawkularModelControllerClient()) {
            ModelNode datasourcesPath = new ModelNode().add(ModelDescriptionConstants.SUBSYSTEM, "datasources");
            // expecting h2 and the mysql driver we added in the previous test
            assertResourceCount(mcc, datasourcesPath, "jdbc-driver", 2);
            assertResourceExists(mcc, driverAddress, true);

            String req = "RemoveJdbcDriverRequest={\"authentication\":" + authentication + ", "
                    + "\"feedId\":\"" + drv.getFeedId() + "\","
                    + "\"resourceId\":\"" + drv.getId() + "\""
                    + "}";
            String response = "RemoveJdbcDriverResponse={"
                    + "\"feedId\":\"" + drv.getFeedId() + "\","
                    + "\"resourceId\":\"" + drv.getId() + "\","
                    + "\"destinationSessionId\":\"{{sessionId}}\","
                    + "\"status\":\"OK\","
                    + "\"message\":\"Performed [Remove] on a [JDBC Driver] given by Feed Id [" + drv.getFeedId() +"] Resource Id ["
                    + drv.getId() + "]\""
                    + ""; // server refresh indicator might be after this - so don't look for ending bracked here
            try (TestWebSocketClient testClient = TestWebSocketClient.builder()
                    .url(baseGwUri + "/ui/ws")
                    .expectWelcome(req)
                    .expectGenericSuccess(drv.getFeedId())
                    .expectText(response, TestWebSocketClient.Answer.CLOSE)
                    .expectClose()
                    .build()) {
                testClient.validate(10000);
            }

            assertResourceExists(mcc, driverAddress, false);

        }
    }
}
