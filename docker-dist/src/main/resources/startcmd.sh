#!/usr/bin/env bash
#
# Copyright 2016 Red Hat, Inc. and/or its affiliates
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

echo ${HOSTNAME} > /etc/machine-id
export JAVA_OPTS="-Xmx256m -Djava.net.preferIPv4Stack=true -Djava.awt.headless=true -XX:MaxMetaspaceSize=256m"
/opt/jboss/wildfly/bin/standalone.sh -Dhawkular.rest.feedId=${HOSTNAME} -Djboss.server.name=${HOSTNAME}
