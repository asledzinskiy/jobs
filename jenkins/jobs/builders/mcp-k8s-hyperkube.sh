#!/bin/bash

set -ex

if [[ -z "${KUBE_DOCKER_OWNER:-}" ]]; then
  echo "KUBE_DOCKER_OWNER must be set"
  exit -1
fi

export REGISTRY="${KUBE_DOCKER_REGISTRY}/${KUBE_DOCKER_OWNER}"

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

git fetch ssh://mcp-ci-gerrit@"${GERRIT_HOST}":29418/"${GERRIT_PROJECT}" "${GERRIT_REFSPEC}" && git checkout FETCH_HEAD

export GIT_COMMIT_TAG_ID=$(git describe --tags --abbrev=4)

cat <<EOF > "${WORKSPACE}/build.sh"
#!/bin/bash
source "${WORKSPACE}/build/common.sh"
kube::build::verify_prereqs
kube::build::build_image
kube::build::run_build_command hack/build-go.sh cmd/hyperkube
EOF
chmod +x "${WORKSPACE}/build.sh"
sudo -E -s "${WORKSPACE}/build.sh"

export KUBE_DOCKER_VERSION="${GIT_COMMIT_TAG_ID}_${BUILD_NUMBER}"
export VERSION="${KUBE_DOCKER_VERSION}"

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

# inject calico now
export KUBE_CONTAINER_TMP="hyperkube-tmp-${BUILD_NUMBER}"
export CALICO_BINDIR="/opt/cni/bin"
echo "Calico injection will happen now..."
wget https://github.com/projectcalico/calico-cni/releases/download/v1.3.1/calico-ipam
wget https://github.com/projectcalico/calico-cni/releases/download/v1.3.1/calico
chmod +x calico calico-ipam
docker run --name "${KUBE_CONTAINER_TMP}" -d -t "${KUBE_DOCKER_REGISTRY}/${KUBE_DOCKER_OWNER}/${KUBE_DOCKER_REPOSITORY}:${KUBE_DOCKER_VERSION}"
docker exec -t "${KUBE_CONTAINER_TMP}" /bin/bash -c "/bin/mkdir -p ${CALICO_BINDIR}"
docker cp calico "${KUBE_CONTAINER_TMP}":"${CALICO_BINDIR}/calico"
docker cp calico-ipam "${KUBE_CONTAINER_TMP}":"${CALICO_BINDIR}/calico-ipam"
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
