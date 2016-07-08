#!/usr/bin/env bash
#
# Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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

set -x

mkdir target
# cp ../hawkular-wildfly-agent/target/hawkular-wildfly-agent-0.19.2.Final-SNAPSHOT.jar target/agent.jar
#cp ../hawkular-wildfly-agent-wf-extension/target/hawk*.zip agent.zip
#cp ../hawkular-wildfly-agent-installer/target/hawk*.jar target/installer.jar
cp ../hawkular-wildfly-agent-installer-full/target/hawk*.jar target/installer.jar
docker build . -t pilhuhn/hawkfly
