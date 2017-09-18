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

PUBLIC_KEY="/client-secrets/hawkular-services-public.pem"

import_hawkular_services_public_key() {
    local PUBLIC_KEY_DER="/tmp/hawkular-services-public.cert"
    if [[ -f ${PUBLIC_KEY} ]] && [[ -s ${PUBLIC_KEY} ]]; then
        openssl x509 -inform pem -in ${PUBLIC_KEY} -out ${PUBLIC_KEY_DER}
        keytool -import -keystore ${JAVA_HOME}/jre/lib/security/cacerts -storepass changeit \
        -file ${PUBLIC_KEY_DER} -noprompt
        rm -f ${PUBLIC_KEY_DER}
    fi
}

run_hawkular_agent() {
    JAVA_OPTS="${JAVA_OPTS} \
    -Dhawkular.rest.host=${HAWKULAR_SERVER_PROTOCOL}://${HAWKULAR_SERVER_ADDR}:${HAWKULAR_SERVER_PORT} \
    -Dhawkular.rest.user=${HAWKULAR_AGENT_USER} -Dhawkular.rest.password=${HAWKULAR_AGENT_PASSWORD} \
    -Dhawkular.agent.immutable=${HAWKULAR_IMMUTABLE} -Dhawkular.agent.in-container=${HAWKULAR_IMMUTABLE}"
    ${JBOSS_HOME}/bin/standalone.sh -b 0.0.0.0
}

main() {
  import_hawkular_services_public_key
  run_hawkular_agent "$@"
}

main "$@"