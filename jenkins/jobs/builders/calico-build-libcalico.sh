#!/bin/sh
set -e
set -x

DOCKER_REPO="${DOCKER_REPO:-$CALICO_DOCKER_REGISTRY}"
BUILD_IMAGE="calico/build"
BUILD_IMAGE_TAG="${BUILD_IMAGE_TAG:-v0.15.0}"
BUILD=$(git rev-parse --short HEAD)

NAME="${DOCKER_REPO}/${BUILD_IMAGE}:${BUILD_IMAGE_TAG}"

rm -rf ./artifacts

echo "Building images"
docker build -t "${NAME}-${BUILD}" .

echo "Pushing images"
set +x
docker login -u "${ARTIFACTORY_LOGIN}" -p "${ARTIFACTORY_PASSWORD}" \
  -e "${ARTIFACTORY_USER_EMAIL}" "${CALICO_DOCKER_REGISTRY}"
set -x
docker push "${NAME}-${BUILD}"

mkdir ./artifacts
echo "${NAME}-${BUILD}" > ./artifacts/lastbuild
echo "LIBCALICO_IMAGE=${NAME}-${BUILD}" > build.property
