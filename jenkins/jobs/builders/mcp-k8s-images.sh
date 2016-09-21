#!/bin/bash

set -ex

export VENV_PATH="${WORKSPACE}/.tox/mcp-ci"

if [[ -z "${DOCKER_REPOSITORY:-}" ]]; then
  echo "DOCKER_REPOSITORY must be set"
  exit -1
fi

if [[ -z "${DOCKER_REGISTRY:-}" ]]; then
  echo "DOCKER_REGISTRY must be set"
  exit -1
fi

tox -e mcp-ci

set +x
docker login -u "${ARTIFACTORY_LOGIN}" -p "${ARTIFACTORY_PASSWORD}" "${DOCKER_REGISTRY}"
set -x

for image in base unit integration; do
  # copy test conf file as it's required but not used
  cp tests/test_conf.yml conf/conf.yml
  ./mcp-ci.sh build k8s-tests-${image}
  docker tag k8s-tests-${image}:latest ${DOCKER_REGISTRY}/${DOCKER_REPOSITORY}/k8s-tests-${image}:latest
  docker push ${DOCKER_REGISTRY}/${DOCKER_REPOSITORY}/k8s-tests-${image}:latest
done
