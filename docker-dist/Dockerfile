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

FROM jboss/wildfly:11.0.0.Final

MAINTAINER Hawkular project <hawkular-dev@lists.jboss.org>

# ADD test-simple.war /opt/jboss/wildfly/standalone/deployments/
COPY target/hawkular-javaagent.jar $JBOSS_HOME/bin/
COPY target/hawkular-javaagent-config.yaml /opt/hawkular/configuration/

ADD src/main/resources/run_hawkular_javaagent.sh /opt/hawkular/bin/run_hawkular_agent.sh

ENV HAWKULAR_URL=http://hawkular:8080 \
    HAWKULAR_USER=jdoe \
    HAWKULAR_PASSWORD=password \
    HAWKULAR_AGENT_IMMUTABLE=true \
    HAWKULAR_AGENT_METRICS_PORT=9779 \
    HAWKULAR_MODE=standalone

EXPOSE 8080 9090 9779

USER root
RUN printf 'JAVA_OPTS="$JAVA_OPTS' >> $JBOSS_HOME/bin/standalone.conf
RUN printf 'HOST_CONTROLLER_JAVA_OPTS="$HOST_CONTROLLER_JAVA_OPTS' >> $JBOSS_HOME/bin/domain.conf
RUN printf ' -Djboss.modules.system.pkgs=org.jboss.byteman,org.jboss.logmanager -Djava.util.logging.manager=org.jboss.logmanager.LogManager -javaagent:$JBOSS_HOME/bin/hawkular-javaagent.jar=config=/opt/hawkular/configuration/hawkular-javaagent-config.yaml,delay=10' \
| tee -a $JBOSS_HOME/bin/standalone.conf $JBOSS_HOME/bin/domain.conf  > /dev/null
RUN printf ' -Djboss.host.server-excluded-properties=jboss.modules.system.pkgs,java.util.logging.manager' >> $JBOSS_HOME/bin/domain.conf
RUN printf ' -Dhawkular.rest.url=${HAWKULAR_URL} -Dhawkular.agent.immutable=${HAWKULAR_AGENT_IMMUTABLE} -Dhawkular.agent.in-container=${HAWKULAR_AGENT_IMMUTABLE} -Dhawkular.agent.metrics.port=${HAWKULAR_AGENT_METRICS_PORT}"\n' \
| tee -a $JBOSS_HOME/bin/standalone.conf $JBOSS_HOME/bin/domain.conf  > /dev/null


RUN yum install --quiet -y openssl && \
    rm -rf /var/cache/yum && \
    chown -RH jboss:0 $JBOSS_HOME/standalone $JAVA_HOME/jre/lib/security/cacerts /opt/hawkular && \
    chmod -R ug+rw $JBOSS_HOME/standalone $JAVA_HOME/jre/lib/security/cacerts /opt/hawkular && \
    chmod -R a+rw /opt/hawkular/ && \
    chmod -R a+x $JBOSS_HOME

USER jboss
CMD /opt/hawkular/bin/run_hawkular_agent.sh
