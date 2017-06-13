#!/usr/bin/env bash
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

_HAWKULAR_SERVICES_TRUSTSTORE='hawkular-services.truststore'

generate_empty_keystore() {
  keytool -genkeypair -keyalg RSA -alias dummy -dname "CN=localhost" -keypass hawkular -storepass hawkular \
    -keystore $_HAWKULAR_SERVICES_TRUSTSTORE
  keytool -delete -alias dummy -storepass hawkular -keystore $_HAWKULAR_SERVICES_TRUSTSTORE
}

run_hawkular_agent_standalone_install() {
  java -jar installer.jar --enabled true --server-url '${env.HAWKULAR_SERVER_PROTOCOL}://${env.HAWKULAR_SERVER_ADDR}:${env.HAWKULAR_SERVER_PORT}' \
    --module-dist classpath:hawkular-wildfly-agent-wf-extension.zip --username '${env.HAWKULAR_AGENT_USER}' \
    --password '${env.HAWKULAR_AGENT_PASSWORD}' --target-location "$JBOSS_HOME" \
    --feed-id='${hawkular.agent.machine.id}' \
    --keystore-path $_HAWKULAR_SERVICES_TRUSTSTORE --keystore-password hawkular
}

run_hawkular_agent_domain_install() {
  java -jar installer.jar --enabled true --server-url '${env.HAWKULAR_SERVER_PROTOCOL}://${env.HAWKULAR_SERVER_ADDR}:${env.HAWKULAR_SERVER_PORT}' \
    --module-dist classpath:hawkular-wildfly-agent-wf-extension.zip --username '${env.HAWKULAR_AGENT_USER}' \
    --target-config="$JBOSS_HOME/domain/configuration/host.xml" \
    --password '${env.HAWKULAR_AGENT_PASSWORD}' --target-location "$JBOSS_HOME" \
    --keystore-path $_HAWKULAR_SERVICES_TRUSTSTORE --keystore-password hawkular
}
