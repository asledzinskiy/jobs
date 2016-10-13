#!/bin/bash

set -ex

wait_for_artifacts () {
    local TYPE="$1"
    local VERSION="$2"
    local TIMEOUT="${3:-1200}"
    local INTERVAL="${4:-5}"
    local DEADLINE=$(($(date +%s)+${TIMEOUT}))
    local ARTIFACTS_URL="https://${DOCKER_REGISTRY}/v2/mcp-netchecker/${TYPE}/tags/list"

    while [[ "$(date +%s)" -lt "${DEADLINE}" ]]; do
        if curl -s "${ARTIFACTS_URL}" | grep -qw "${VERSION}"; then
            return 0
         else
            sleep "${INTERVAL}"
        fi
    done
    echo 1>&2 "ERROR! Time is out while waiting for artifacts:" \
    "'mcp-netchecker/${TYPE}' docker image with tag '${VERSION}' not found!"
    exit 1
}

save_tests_params () {
    local SOURCE_YAML="$1"
    local RESULT_PROPERTIES_FILE="$2"
    python2 > "${RESULT_PROPERTIES_FILE}" <<EOF
import yaml
for k,v in yaml.load(open("${SOURCE_YAML}")).items():
    print '{0}={1}'.format(k.upper(), v)
EOF
}

NETCHECKER_TYPE="${NETCHECKER_TYPE:-$(find -name Dockerfile -exec egrep -o '(server|agent)[ ]*$' {} \;)}"
DOCKER_REPO="${DOCKER_REPO:-${DOCKER_REGISTRY}}"
BUILD_IMAGE="mcp-netchecker/${NETCHECKER_TYPE}"
BUILD_IMAGE_TAG="${BUILD_IMAGE_TAG:-${NETCHECKER_VERSION}}"
BUILD="$(git rev-parse --short HEAD)"

NAME="${DOCKER_REPO}/${BUILD_IMAGE}:${BUILD_IMAGE_TAG}"

NETCHECKER_ARTIFACTS_FILE_YAML="./artifacts/mcp-netchecker-${NETCHECKER_TYPE}-${BUILD}.yaml"

rm -rf './artifacts'
mkdir './artifacts'

cat > "${NETCHECKER_ARTIFACTS_FILE_YAML}" << EOF
mcp_netchecker_${NETCHECKER_TYPE}_image_repo: ${DOCKER_REPO}/${BUILD_IMAGE}
mcp_netchecker_${NETCHECKER_TYPE}_version: ${BUILD_IMAGE_TAG}-${BUILD}
EOF

# if this script is executed from tests job - don't build anything,
# just wait until artifacts appear in the artifactory
if [[ "${ONLY_WAIT_FOR_ARTIFACTS}" == "true" ]]; then
    if [ -z "${NETCHECKER_ARTIFACTS_FILE}" ]; then
        echo 1>&2 \
        "ERROR! The variable NETCHECKER_ARTIFACTS_FILE for test properties file isn't set!"
        exit 1
    fi
    NETCHECKER_VERSION="${BUILD_IMAGE_TAG}-${BUILD}"
    wait_for_artifacts "${NETCHECKER_TYPE}" "${NETCHECKER_VERSION}" 1200
    save_tests_params "${NETCHECKER_ARTIFACTS_FILE_YAML}" "${NETCHECKER_ARTIFACTS_FILE}"
    exit 0
fi

if [[ ! -f "Dockerfile" && -f "docker/Dockerfile" ]]; then
    pushd docker
fi

echo "Building docker image"
docker build -t "${NAME}-${BUILD}" .

popd || true

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

if [ -n "${NETCHECKER_ARTIFACTS_FILE}" ]; then
    save_tests_params "${NETCHECKER_ARTIFACTS_FILE_YAML}" "${NETCHECKER_ARTIFACTS_FILE}"
fi
