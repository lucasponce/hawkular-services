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

# This is a test script that can run a Hawkular Services server and a Prometheus Server
# both in docker, as well as stop those containers.
# Thus this script is for anyone that wants to demo a simple Hawkular Services setup
# without having to build anything. You just need this script file and the prometeus.yml
# configuration file.

# These are the images that are to be started or stopped
HAWKULAR_SERVICES_IMAGE=hawkular/hawkular-services:hawkular-1275
PROMETHEUS_IMAGE=prom/prometheus:v2.0.0

# The directory where this script is found (and where the prometheus yml file should be)
SCRIPT_BASEDIR=$(dirname $(readlink -f "$0"))

start()  {
  echo Starting containers...
  if [[ ! -d $HAWKULAR_DATA ]]; then
    HAWKULAR_DATA=/tmp/hawkular
  fi
  mkdir -p $HAWKULAR_DATA

  docker run \
    --detach \
    --publish 8080:8080 \
    --volume ${HAWKULAR_DATA}:/var/hawkular:z \
    --net "host" \
    --env HAWKULAR_DATA=/var/hawkular \
    ${HAWKULAR_SERVICES_IMAGE}

  docker run \
    --detach \
    --publish 9090:9090 \
    --volume ${HAWKULAR_DATA}:/var/hawkular:z \
    --net "host" \
    --volume ${SCRIPT_BASEDIR}/prometheus.yml:/prometheus.yml:Z \
    ${PROMETHEUS_IMAGE} \
      --config.file=/prometheus.yml
}

stop() {
  echo Stopping containers...
  docker kill $(docker ps -q -f ancestor=${HAWKULAR_SERVICES_IMAGE} -f ancestor=${PROMETHEUS_IMAGE})
}

status() {
  docker ps -f ancestor=${HAWKULAR_SERVICES_IMAGE} -f ancestor=${PROMETHEUS_IMAGE}
}

case "$1" in
  start)
    start
    ;;
  stop)
    stop
    ;;
  status)
    status
    ;;
  *)
    echo "Usage $0 <start|stop|status>"
    ;;
esac
