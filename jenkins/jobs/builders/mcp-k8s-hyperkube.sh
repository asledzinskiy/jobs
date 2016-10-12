#!/bin/bash

set -ex

if [[ -z "${KUBE_DOCKER_OWNER:-}" ]]; then
  echo "KUBE_DOCKER_OWNER must be set"
  exit -1
fi

wait_for_artifacts () {
    local VERSION="$1"
    local TIMEOUT="${2:-1200}"
    local INTERVAL="${3:-5}"
    local DEADLINE=$(($(date +%s)+${TIMEOUT}))
    local ARTIFACTS_YAML="hyperkube_image_${VERSION}.yaml"
    local ARTIFACTS_URL="${ARTIFACTORY_URL}/mcp-k8s-ci/images-info/${ARTIFACTS_YAML}"

    while [[ "$(date +%s)" -lt "${DEADLINE}" ]]; do
        local HTTP_CODE=$(curl -sw '%{http_code}' -o /dev/null -I "${ARTIFACTS_URL}")
        if [[ "${HTTP_CODE}" -eq 200 ]]; then
            wget -N "${ARTIFACTS_URL}"
            echo "${ARTIFACTS_YAML}"
            return 0
        else
            sleep "${INTERVAL}"
        fi
    done
    echo 1>&2 "ERROR! Time is out while waiting for artifacts: ${ARTIFACTS_YAML}!"
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

export REGISTRY="${KUBE_DOCKER_REGISTRY}/${KUBE_DOCKER_OWNER}"

CALICO_CNI="${CALICO_CNI:-https://github.com/projectcalico/calico-cni/releases/download/v1.3.1/calico}"
CALICO_IPAM="${CALICO_IPAM:-https://github.com/projectcalico/calico-cni/releases/download/v1.3.1/calico-ipam}"

if [[ -z "${KUBE_DOCKER_REGISTRY:-}" ]]; then
  echo "KUBE_DOCKER_REGISTRY must be set"
  exit -1
fi

GIT_K8S_CACHE_DIR="${GIT_K8S_CACHE_DIR:-/home/jenkins/kubernetes}"

# clone repo from local copy
git clone file://"${GIT_K8S_CACHE_DIR}" .

git reset --hard
if ! git clean -x -f -d -q ; then
  sleep 1
  git clean -x -f -d -q
fi

# sync local copy of kubernetes repo with the remote
git remote add kubernetes ssh://mcp-ci-gerrit@"${GERRIT_HOST}":29418/"${GERRIT_PROJECT}"
git fetch kubernetes --tags

git fetch ssh://mcp-ci-gerrit@"${GERRIT_HOST}":29418/"${GERRIT_PROJECT}" "${GERRIT_REFSPEC}" && git checkout FETCH_HEAD

export GIT_COMMIT_TAG_ID=$(git describe --tags --abbrev=4)
export KUBE_DOCKER_VERSION="${GIT_COMMIT_TAG_ID}_${BUILD_NUMBER}"
export VERSION="${KUBE_DOCKER_VERSION}"

# if this script is executed from tests job - don't build anything,
# just wait until artifacts appear in the artifactory
if [[ "${ONLY_WAIT_FOR_ARTIFACTS}" == "true" ]]; then
    if [ -z "${HYPERKUBE_ARTIFACTS_FILE}" ]; then
        echo 1>&2 \
        "ERROR! The variable HYPERKUBE_ARTIFACTS_FILE for test properties file isn't set!"
        exit 1
    fi
    if [[ -z "${GERRIT_CHANGE_NUMBER}" || -z "${GERRIT_PATCHSET_NUMBER}" ]]; then
        echo 1>&2 \
        "ERROR! Can't find artifacts for tests! Looks like the build wasn't triggered by gerrit event!"
        exit 1
    fi
    PATCHSET_VERSION="${GIT_COMMIT_TAG_ID}_${GERRIT_CHANGE_NUMBER}+${GERRIT_PATCHSET_NUMBER}"
    ARTIFACTS_YAML=$(wait_for_artifacts "${PATCHSET_VERSION}" 1200)
    save_tests_params "${WORKSPACE}/${ARTIFACTS_YAML}" "${HYPERKUBE_ARTIFACTS_FILE}"
    exit 0
fi

cat <<EOF > "${WORKSPACE}/build.sh"
#!/bin/bash
source "${WORKSPACE}/build/common.sh"
kube::build::verify_prereqs
kube::build::build_image
kube::build::run_build_command hack/build-go.sh cmd/hyperkube
EOF
chmod +x "${WORKSPACE}/build.sh"
sudo -E -s "${WORKSPACE}/build.sh"

# Inject build info to the image
pushd "${WORKSPACE}/cluster/images/hyperkube"
if grep -q 'LABEL com.mirantis' Dockerfile; then
   sed -i.back '/.*com.mirantis.*/d' Dockerfile
fi
cat <<EOF >> Dockerfile
# Apply additional build metadata
LABEL com.mirantis.image-specs.gerrit_change_url="${GERRIT_CHANGE_URL}" \
      com.mirantis.image-specs.build_url="${BUILD_URL}" \
      com.mirantis.image-specs.patchset="${GERRIT_PATCHSET_REVISION}"
EOF
popd

make -C "${WORKSPACE}/cluster/images/hyperkube" build

rm -rf "${WORKSPACE}/artifacts"
mkdir -p "${WORKSPACE}/artifacts"

# inject calico now
export KUBE_CONTAINER_TMP="hyperkube-tmp-${BUILD_NUMBER}"
export CALICO_BINDIR="/opt/cni/bin"
echo "Calico injection will happen now..."

if [ "$CALICO_DOWNSTREAM" == "true" ] ; then
  TMPURL="${ARTIFACTORY_URL}/projectcalico/${CALICO_VER}/calico-cni/"
  lastbuild=$(curl -s $TMPURL/lastbuild)
  wget ${TMPURL}/calico-${lastbuild} -O calico
  wget ${TMPURL}/calico-ipam-${lastbuild} -O calico-ipam
  calico_checksum=$(sha1sum calico | awk '{ print $1 }')
  calico_ipam_checksum=$(sha1sum calico-ipam | awk '{ print $1 }')
  [ "$calico_checksum" == "$(curl -s ${TMPURL}/calico-${lastbuild}.sha1)" ]
  [ "$calico_ipam_checksum" == "$(curl -s ${TMPURL}/calico-ipam-${lastbuild}.sha1)" ]
else
  wget "${CALICO_IPAM}" -O calico-ipam
  wget "${CALICO_CNI}" -O calico
fi

chmod +x calico calico-ipam
docker run --name "${KUBE_CONTAINER_TMP}" -d -t "${KUBE_DOCKER_REGISTRY}/${KUBE_DOCKER_OWNER}/${KUBE_DOCKER_REPOSITORY}:${KUBE_DOCKER_VERSION}"
docker exec -t "${KUBE_CONTAINER_TMP}" /bin/bash -c "/bin/mkdir -p ${CALICO_BINDIR}"
docker cp calico "${KUBE_CONTAINER_TMP}":"${CALICO_BINDIR}/calico"
docker cp calico-ipam "${KUBE_CONTAINER_TMP}":"${CALICO_BINDIR}/calico-ipam"
docker cp "${KUBE_CONTAINER_TMP}":/hyperkube "${WORKSPACE}/artifacts/hyperkube_${VERSION}"
docker stop "${KUBE_CONTAINER_TMP}"
docker commit "${KUBE_CONTAINER_TMP}" "${KUBE_DOCKER_REGISTRY}/${KUBE_DOCKER_OWNER}/${KUBE_DOCKER_REPOSITORY}:${KUBE_DOCKER_VERSION}"
docker rm "${KUBE_CONTAINER_TMP}"
#

docker tag "${KUBE_DOCKER_REGISTRY}/${KUBE_DOCKER_OWNER}/${KUBE_DOCKER_REPOSITORY}:${KUBE_DOCKER_VERSION}" "${KUBE_DOCKER_REGISTRY}/${KUBE_DOCKER_REPOSITORY}:${KUBE_DOCKER_VERSION}"
set +x
docker login -u "${ARTIFACTORY_HYPERKUBE_LOGIN}" -p "${ARTIFACTORY_HYPERKUBE_PASSWORD}" -e "${ARTIFACTORY_USER_EMAIL}" "${KUBE_DOCKER_REGISTRY}"
set -x
docker push "${KUBE_DOCKER_REGISTRY}/${KUBE_DOCKER_REPOSITORY}:${KUBE_DOCKER_VERSION}"

# clean images locally
docker rmi -f "${KUBE_DOCKER_REGISTRY}/${KUBE_DOCKER_REPOSITORY}:${KUBE_DOCKER_VERSION}" || true
docker rmi -f "${KUBE_DOCKER_REGISTRY}/${KUBE_DOCKER_OWNER}/hyperkube:${KUBE_DOCKER_VERSION}" || true
docker rmi -f "${KUBE_DOCKER_REGISTRY}/${KUBE_DOCKER_OWNER}/${KUBE_DOCKER_REPOSITORY}:${KUBE_DOCKER_VERSION}" || true

# generate image description artifact
cat <<EOF > "${WORKSPACE}/hyperkube_image_${VERSION}.yaml"
hyperkube_image_repo: "${KUBE_DOCKER_REGISTRY}/${KUBE_DOCKER_REPOSITORY}"
hyperkube_image_tag: "${KUBE_DOCKER_VERSION}"
gerrit_change_url: "${GERRIT_CHANGE_URL}"
EOF

# copy versions of images to artifact for patchset
if [[ -n "${GERRIT_CHANGE_NUMBER}" && -n "${GERRIT_PATCHSET_NUMBER}" ]]; then
    PATCHSET_VERSION="${GIT_COMMIT_TAG_ID}_${GERRIT_CHANGE_NUMBER}+${GERRIT_PATCHSET_NUMBER}"
    cp "${WORKSPACE}/hyperkube_image_${VERSION}.yaml" \
       "${WORKSPACE}/hyperkube_image_${PATCHSET_VERSION}.yaml"
fi
