#!/bin/bash

set -ex

NETCHECKER_TYPE="${NETCHECKER_TYPE:-$(find -name Dockerfile -exec egrep -o '(server|agent)[ ]*$' {} \;)}"
DOCKER_REPO="${DOCKER_REPO:-${DOCKER_REGISTRY}}"
BUILD_IMAGE="mcp-netchecker/${NETCHECKER_TYPE}"
BUILD_IMAGE_TAG="${BUILD_IMAGE_TAG:-${NETCHECKER_VERSION}}"
BUILD="$(git rev-parse --short HEAD)"

NAME="${DOCKER_REPO}/${BUILD_IMAGE}:${BUILD_IMAGE_TAG}"

if [[ ! -f "Dockerfile" && -f "docker/Dockerfile" ]]; then
    cd docker
fi

echo "Building docker image"
docker build -t "${NAME}-${BUILD}" .

echo "Pushing docker image"
set +x
docker login -u "${ARTIFACTORY_LOGIN}" -p "${ARTIFACTORY_PASSWORD}" \
  -e "${ARTIFACTORY_USER_EMAIL}" "${DOCKER_REGISTRY}"
set -x
docker push "${NAME}-${BUILD}"

if [[ "${PUBLISH}" =~ ^[Tt][Rr][Uu][Ee]|[Yy][Ee][Ss]$ ]]; then
    echo "Publishing docker image"
    docker tag "${NAME}-${BUILD}" "${NAME}"
    docker push "${NAME}"
fi

NETCHECKER_ARTIFACTS_FILE_YAML="./artifacts/mcp-netchecker-${NETCHECKER_TYPE}-${BUILD}.yaml"

cat > "${NETCHECKER_ARTIFACTS_FILE_YAML}" << EOF
mcp_netchecker_${NETCHECKER_TYPE}_image_repo: ${NAME}
mcp_netchecker_${NETCHECKER_TYPE}_version: ${BUILD_IMAGE_TAG}-${BUILD}
EOF

if [ -z "${NETCHECKER_ARTIFACTS_FILE}" ]; then
    # Skip artifacts data storing
    exit 0
fi

python2 > "${NETCHECKER_ARTIFACTS_FILE}" <<EOF
import yaml
for k,v in yaml.load(open("${NETCHECKER_ARTIFACTS_FILE_YAML}")).items():
    print '{0}={1}'.format(k.upper(), v)
EOF