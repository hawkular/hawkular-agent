#!/bin/bash
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

set -xe

IMAGES="wildfly-hawkular-agent-domain \
        wildfly-hawkular-agent \
        wildfly-hawkular-javaagent"

OWNER="${OWNER:-hawkular}"

if [[ "${TRAVIS_TAG}" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.Final$ ]]; then

  # build the images
  ./docker-dist/do.sh

  # we don't want to show the credentials in travis
  set +x
  docker login -u "${DOCKER_USER}" -p "${DOCKER_PASS}"
  set -x

  # push the latest and ${TRAVIS_TAG} images
  for image in ${IMAGES}; do
    docker tag $image:latest $OWNER/$image:${TRAVIS_TAG}
    docker tag $image:latest $OWNER/$image:latest
    docker push $OWNER/$image:${TRAVIS_TAG}
    docker push $OWNER/$image:latest
  done

  docker logout
else
  echo "Not doing the docker push, because the tag '${TRAVIS_TAG}' is not of form x.y.z.Final"
fi
