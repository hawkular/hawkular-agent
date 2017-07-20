#
# Copyright 2015-2017 Red Hat, Inc. and/or its affiliates
# and other contributors as indicated by the @author tags.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

FROM jboss/wildfly:10.1.0.Final

MAINTAINER Hawkular project <hawkular-dev@lists.jboss.org>

# ADD test-simple.war /opt/jboss/wildfly/standalone/deployments/
ADD target/hawkular-javaagent-wildfly-dist/bin/standalone.conf $JBOSS_HOME/bin
ADD target/hawkular-javaagent-wildfly-dist/bin/hawkular-javaagent.jar $JBOSS_HOME/bin
ADD target/hawkular-javaagent-wildfly-dist/standalone/configuration/hawkular-javaagent-config.yaml $JBOSS_HOME/standalone/configuration/

ENV HAWKULAR_SERVER_PROTOCOL http
ENV HAWKULAR_SERVER_ADDR hawkular
ENV HAWKULAR_SERVER_PORT 8080
ENV HAWKULAR_AGENT_USER=jdoe HAWKULAR_AGENT_PASSWORD=password
ENV HAWKULAR_IMMUTABLE=true
ENV JAVA_OPTS="-Xmx256m -Djava.net.preferIPv4Stack=true -Djava.awt.headless=true -XX:MaxMetaspaceSize=256m"

EXPOSE 8080 9090

USER root
RUN chown -R jboss:0 /opt/jboss/wildfly/standalone && \
    chmod -R ug+rw /opt/jboss/wildfly/standalone

USER jboss
CMD /opt/jboss/wildfly/bin/standalone.sh -b 0.0.0.0 \
-Dhawkular.rest.host=${HAWKULAR_SERVER_PROTOCOL}://${HAWKULAR_SERVER_ADDR}:${HAWKULAR_SERVER_PORT} \
-Dhawkular.rest.user=${HAWKULAR_AGENT_USER} -Dhawkular.rest.password=${HAWKULAR_AGENT_PASSWORD} \
-Dhawkular.agent.immutable=${HAWKULAR_IMMUTABLE} -Dhawkular.agent.in-container=${HAWKULAR_IMMUTABLE}
