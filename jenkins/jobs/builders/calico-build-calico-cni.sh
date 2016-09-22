#!/bin/sh

set -o xtrace
set -o errexit

WORKSPACE="${WORKSPACE:-$(pwd)}"

DOCKER_REPO="${DOCKER_REPO:-$CALICO_DOCKER_REGISTRY}"

LIBCALICO_DOCKER_IMAGE="${LIBCALICO_DOCKER_IMAGE:-$(curl -s ${ARTIFACTORY_URL}/mcp-0.1/libcalico/lastbuild)}"

if [ "${GERRIT_EVENT_TYPE}X" = "change-mergedX" -o "${GERRIT_CHANGE_NUMBER}X" = "X" ]; then
  # This is merged event or job triggered manually, so tag is mcp-0.1
  IMG_BUILD_TAG=mcp-0.1
else
  # otherwise let's user GERRIT_CHANGE_NUMBER
  IMG_BUILD_TAG="${GERRIT_CHANGE_NUMBER}"
fi

BUILD="${IMG_BUILD_TAG}-$(git rev-parse --short HEAD)"

rm -rf "${WORKSPACE}/dist" \
    "${WORKSPACE}/build" \
    "${WORKSPACE}/artifacts"

docker run --rm \
    -v ${WORKSPACE}:/code \
    ${LIBCALICO_DOCKER_IMAGE} \
    /bin/sh -c "pip install pykube && pyinstaller calico.py -ayF && pyinstaller ipam.py -ayF -n calico-ipam"

mkdir -p "${WORKSPACE}/artifacts"
cp "${WORKSPACE}/dist/calico" "${WORKSPACE}/artifacts/calico-${BUILD}"
cp "${WORKSPACE}/dist/calico-ipam" "${WORKSPACE}/artifacts/calico-ipam-${BUILD}"

# Create config yaml for Kargo
cat > ./artifacts/calico-cni-${BUILD}.yaml << EOF
calico_cni_download_url: ${ARTIFACTORY_URL}/calico-cni/calico-${BUILD}
calico_cni_checksum: $(sha256sum ${WORKSPACE}/artifacts/calico-${BUILD} | awk '{ print $1 }')
calico_cni_ipam_download_url: ${ARTIFACTORY_URL}/calico-cni/calico-ipam-${BUILD}
calico_cni_ipam_checksum: $(sha256sum ${WORKSPACE}/artifacts/calico-ipam-${BUILD} | awk '{ print $1 }')
EOF

echo "${BUILD}" > ./artifacts/lastbuild
