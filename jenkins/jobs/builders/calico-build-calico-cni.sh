#!/bin/sh

set -o xtrace
set -o errexit
set -o nounset

WORKSPACE="${WORKSPACE:-$(pwd)}"

DOCKER_REPO="${DOCKER_REPO:-$CALICO_DOCKER_REGISTRY}"

BUILD_IMAGE="calico/build"
BUILD_IMAGE_TAG="${BUILD_IMAGE_TAG:-v0.15.0}"
BUILD_IMAGE_BUILD="${BUILD_IMAGE_BUILD:-$(curl -s ${ARTIFACTORY_URL}/libcalico/lastbuild)}"

BUILD=$(git rev-parse --short HEAD)

rm -rf "${WORKSPACE}/dist" \
    "${WORKSPACE}/build" \
    "${WORKSPACE}/artifacts"

BUILD_IMAGE_NAME="${DOCKER_REPO}/${BUILD_IMAGE}:${BUILD_IMAGE_TAG}-${BUILD_IMAGE_BUILD}"

docker run --rm \
    -v ${WORKSPACE}:/code \
    ${BUILD_IMAGE_NAME} \
    /bin/sh -c "pip install pykube && pyinstaller calico.py -ayF"

mkdir -p "${WORKSPACE}/artifacts"
cp "${WORKSPACE}/dist/calico" "${WORKSPACE}/artifacts/calico-${BUILD}"

# Create config yaml for Kargo
cat > ./artifacts/calico-cni-${BUILD}.yaml << EOF
calico_cni_download_url: ${ARTIFACTORY_URL}/calico-cni/calico-${BUILD}
calico_cni_checksum: $(sha256sum ${WORKSPACE}/artifacts/calico-${BUILD} | awk '{ print $1 }')
calico_cni_ipam_download_url: ${ARTIFACTORY_URL}/calico-cni/calico-${BUILD}
calico_cni_ipam_checksum: $(sha256sum ${WORKSPACE}/artifacts/calico-${BUILD} | awk '{ print $1 }')
EOF

echo "${BUILD}" > ./artifacts/lastbuild
