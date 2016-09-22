#!/bin/sh

set -o xtrace
set -o errexit
set -o nounset

WORKSPACE="${WORKSPACE:-$(pwd)}"

DOCKER_REPO="${DOCKER_REPO:-$CALICO_DOCKER_REGISTRY}"

LIBCALICO_DOCKER_IMAGE="${LIBCALICO_DOCKER_IMAGE:-$(curl -s ${ARTIFACTORY_URL}/mcp-0.1/libcalico/lastbuild)}"

BUILD=$(git rev-parse --short HEAD)

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
