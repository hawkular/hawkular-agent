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

import org.hawkular.javaagent.itest.util.AbstractITest;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

@Test(suiteName = AbstractDomainITestSuite.SUITE)
public class AbstractDomainITestSuite extends AbstractITest {
    public static final String SUITE = "domain";

    @BeforeSuite
    public void beforeSuiteWaitForHawkularServerToBeReady() throws Throwable {
        System.out.println("STARTING JAVAAGENT DOMAIN ITESTS");
    }

    @AfterSuite(alwaysRun = true)
    public void afterDomainITestSuite() throws Throwable {
        System.out.println("FINISHED JAVAAGENT DOMAIN ITESTS");
    }
}
