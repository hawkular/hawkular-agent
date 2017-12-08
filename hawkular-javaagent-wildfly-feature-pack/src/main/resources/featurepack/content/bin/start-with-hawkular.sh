#!/bin/sh
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

_METRICS_HOST=`hostname`

usage() {
  echo "Usage: $0"
  echo "  -d|--domain - to run in domain mode as opposed to standalone mode"
  echo "  -s|--server <server-name> - the host where the Hawkular Server is running"
  echo "  -u|--username <username> - the username to use when logging into the Hawkular Server"
  echo "  -p|--password <password> - the username credentials"
  echo "  [-h|--metrics-host <host/IP>] - the hostname to bind the metrics exporter"
  echo
  echo "* -u and -p override environment variables HAWKULAR_USER and HAWKULAR_PASSWORD."
  echo "  You can set those environment variables as an alternative to using -u and -p."
  echo "* -h will default to '`hostname`' if not specified."
}

while [[ $# -gt 0 ]]; do
  key="$1"
  case "$key" in
    -d|--domain)
      _DOMAIN_MODE="true"
      ;;
    -s|--server)
      shift
      _SERVER="$1"
      ;;
    -s=*|--server=*)
      _SERVER="${key#*=}"
      ;;
    -h|--metrics-host)
      shift
      _METRICS_HOST="$1"
      ;;
    -h=*|--metrics-host=*)
      _METRICS_HOST="${key#*=}"
      ;;
    -u|--username)
      shift
      HAWKULAR_USER="$1"
      ;;
    -u=*|--username=*)
      HAWKULAR_USER="${key#*=}"
      ;;
    -p|--password)
      shift
      HAWKULAR_PASSWORD="$1"
      ;;
    -p=*|--password=*)
      HAWKULAR_PASSWORD="${key#*=}"
      ;;
    *)
      _ARGS = "${_ARGS} $1"
      ;;
  esac
  shift
done

if [ -z "$_SERVER" ]; then
  echo "Specify the server"
  usage
  exit 1
fi

if [ -z "$HAWKULAR_USER" ]; then
  echo "Specify the server's user via either -u (--username) or the HAWKULAR_USER environment variable"
  usage
  exit 1
fi

if [ -z "$HAWKULAR_PASSWORD" ]; then
  echo "Specify the server's password via either -p (--password) or the HAWKULAR_PASSWORD environment variable"
  usage
  exit 1
fi

export HAWKULAR_USER
export HAWKULAR_PASSWORD

_DIR="$( cd "$( dirname "${0}" )" && pwd )"
if [ -z "$_DOMAIN_MODE" ]; then
  ${_DIR}/standalone.sh -Dhawkular.rest.url=http://${_SERVER}:8080 -Dhawkular.agent.metrics.host=${_METRICS_HOST} ${_ARGS}
else
  ${_DIR}/domain.sh --host-config=host-hawkular.xml -Dhawkular.rest.url=http://${_SERVER}:8080 -Dhawkular.agent.metrics.host=${_METRICS_HOST} ${_ARGS}
fi

