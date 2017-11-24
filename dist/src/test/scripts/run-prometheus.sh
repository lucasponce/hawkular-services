#!/bin/sh
#
# Copyright 2016-2017 Red Hat, Inc. and/or its affiliates
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

# This is a test script that runs a Prometheus Server in docker such that it can scrape
# all agent metrics endpoints. This assumes you are running the Hawkular Services
# that has been built locally and found in the target/ directory of this source repo.
# Thus this script is for developers and not intended for anyone else.

SCRIPT_BASEDIR=$(dirname $(readlink -f "$0"))
TARGET_DIR=$(readlink -f ${SCRIPT_BASEDIR}/../../../target)
JBOSS_HOME=$(readlink -f ${TARGET_DIR}/hawkular-services-dist-*)
JBOSS_DATA_DIR=${JBOSS_HOME}/standalone/data/prometheus

docker run \
  -p 9090:9090 \
  -v ${SCRIPT_BASEDIR}/prometheus.yml:/prometheus.yml:Z \
  -v ${JBOSS_DATA_DIR}:/var/hawkular/data/prometheus:z \
  --net="host" \
  prom/prometheus:v2.0.0 \
    --config.file=/prometheus.yml \
    $*
