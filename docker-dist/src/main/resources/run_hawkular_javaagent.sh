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
    ${JBOSS_HOME}/bin/${HAWKULAR_MODE}.sh -b 0.0.0.0
}

main() {
  HAWKULAR_MODE=`echo ${HAWKULAR_MODE} | tr '[:upper:]' '[:lower:]'`
  if [[ "${HAWKULAR_MODE}" != "standalone" ]] && [[ "${HAWKULAR_MODE}" != "domain" ]]; then
    echo 'HAWKULAR_MODE must be set to "standalone" or "domain", found:' ${HAWKULAR_MODE}
    exit
  fi
  if [[ "${HAWKULAR_MODE}" == "domain" ]]; then
    sed -i "s|- Standalone Environment|- Domain Environment|g" /opt/hawkular/configuration/hawkular-javaagent-config.yaml
  fi
  import_hawkular_services_public_key
  run_hawkular_agent "$@"
}

main "$@"
