#!/usr/bin/env bash
set -x

mkdir target
# cp ../hawkular-wildfly-agent/target/hawkular-wildfly-agent-0.19.2.Final-SNAPSHOT.jar target/agent.jar
#cp ../hawkular-wildfly-agent-wf-extension/target/hawk*.zip agent.zip
#cp ../hawkular-wildfly-agent-installer/target/hawk*.jar target/installer.jar
cp ../hawkular-wildfly-agent-installer-full/target/hawk*.jar target/installer.jar
docker build . -t pilhuhn/hawkfly
