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
package org.hawkular.agent.javaagent.log;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.Logger.Level;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.logging.annotations.ValidIdRange;

@MessageLogger(projectCode = "HAWKJAVAAGENT")
@ValidIdRange(min = 10000, max = 19999)
public interface MsgLogger extends BasicLogger {
    MsgLogger LOG = Logger.getMessageLogger(MsgLogger.class, "org.hawkular.agent.javaagent");

    @LogMessage(level = Level.INFO)
    @Message(id = 10000, value = "Loaded configuration file [%s]")
    void infoLoadedConfigurationFile(String filepath);

    @LogMessage(level = Level.ERROR)
    @Message(id = 10001, value = "Error in security realm [%s]")
    void errorBuildingSecurityRealm(String securityRealmName, @Cause Throwable cause);
}
