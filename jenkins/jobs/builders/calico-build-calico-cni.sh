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

echo "# Auto-generated contents.  Do not manually edit" > calico_cni/version.py
echo "__version__ = '$(git describe --tags)'" >> calico_cni/version.py
echo "__commit__ = '$(git rev-parse HEAD)'" >> calico_cni/version.py
echo "__branch__ = '$(git rev-parse --abbrev-ref HEAD)'" >> calico_cni/version.py

docker run --rm \
    -v ${WORKSPACE}:/code \
    ${LIBCALICO_DOCKER_IMAGE} \
    /bin/sh -c "pip install pykube && pyinstaller calico.py -ayF && pyinstaller ipam.py -ayF -n calico-ipam"

mkdir -p "${WORKSPACE}/artifacts"
cp "${WORKSPACE}/dist/calico" "${WORKSPACE}/artifacts/calico-${BUILD}"
cp "${WORKSPACE}/dist/calico-ipam" "${WORKSPACE}/artifacts/calico-ipam-${BUILD}"

echo "${BUILD}" > ./artifacts/lastbuild

CALICO_CNI_ARTIFACTS_FILE_YAML="./artifacts/calico-cni-${BUILD}.yaml"

# Create config yaml for Kargo
cat > "${CALICO_CNI_ARTIFACTS_FILE_YAML}" << EOF
calico_cni_download_url: ${ARTIFACTORY_URL}/${IMG_BUILD_TAG}/calico-cni/calico-${BUILD}
calico_cni_checksum: $(sha256sum ${WORKSPACE}/artifacts/calico-${BUILD} | awk '{ print $1 }')
calico_cni_ipam_download_url: ${ARTIFACTORY_URL}/${IMG_BUILD_TAG}/calico-cni/calico-ipam-${BUILD}
calico_cni_ipam_checksum: $(sha256sum ${WORKSPACE}/artifacts/calico-ipam-${BUILD} | awk '{ print $1 }')
EOF

if [ -z "${CALICO_CNI_ARTIFACTS_FILE}" ]; then
    # Skip test artifacts data storing
    exit 0
fi

python2 > "${CALICO_CNI_ARTIFACTS_FILE}" <<EOF
import yaml
for k,v in yaml.load(open("${CALICO_CNI_ARTIFACTS_FILE_YAML}")).items():
    print '{0}={1}'.format(k.upper(), v)
EOF
