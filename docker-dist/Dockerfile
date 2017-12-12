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
COPY target/hawkular $JBOSS_HOME/
COPY src/main/resources/run_hawkular_javaagent.sh /opt/hawkular/bin/run_hawkular_agent.sh

ENV HAWKULAR_URL=http://hawkular:8080 \
    HAWKULAR_USER=jdoe \
    HAWKULAR_PASSWORD=password \
    HAWKULAR_AGENT_IMMUTABLE=true \
    HAWKULAR_AGENT_METRICS_PORT=9779 \
    HAWKULAR_AGENT_MODE=standalone

EXPOSE 8080 9090 9779

USER root


RUN yum install --quiet -y openssl && \
    rm -rf /var/cache/yum && \
    chown -RH jboss:0 $JBOSS_HOME $JAVA_HOME/jre/lib/security/cacerts /opt/hawkular && \
    chmod -R ug+rw $JBOSS_HOME $JAVA_HOME/jre/lib/security/cacerts /opt/hawkular && \
    chmod -R a+rw /opt/hawkular/ && \
    chmod -R a+x $JBOSS_HOME

USER jboss
CMD /opt/hawkular/bin/run_hawkular_agent.sh
