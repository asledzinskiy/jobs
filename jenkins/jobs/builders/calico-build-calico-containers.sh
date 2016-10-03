#!/bin/sh

set -x
set -e

BUILD_ARGS=""

append_arg () {
  echo "$BUILD_ARGS --build-arg $1"
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

DOCKER_REPO="${DOCKER_REPO:-$CALICO_DOCKER_REGISTRY}"

NODE_IMAGE="${NODE_IMAGE:-calico/node}"
NODE_IMAGE_TAG="${NODE_IMAGE_TAG:-v0.20.0}"

CTL_IMAGE="${CTL_IMAGE:-calico/ctl}"
CTL_IMAGE_TAG="${CTL_IMAGE_TAG:-v0.20.0}"

BUILD_IMAGE="calico/build"
BUILD_IMAGE_TAG="${BUILD_IMAGE_TAG:-v0.15.0}"

CALICO_REPO="${CALICO_REPO:-https://$GERRIT_HOST/projectcalico/calico}"
CALICO_VER="${CALICO_VER:-mcp-0.1}"

LIBCALICO_REPO="${LIBCALICO_REPO:-https://$GERRIT_HOST/projectcalico/libcalico}"
LIBCALICO_VER="${LIBCALICO_VER:-mcp-0.1}"

CONFD_BUILD="${CONFD_BUILD:-$(curl -s ${ARTIFACTORY_URL}/mcp-0.1/confd/lastbuild)}"
CONFD_URL="${CONFD_URL:-${ARTIFACTORY_URL}/mcp-0.1/confd/confd-$CONFD_BUILD}"

BIRD_BUILD="${BIRD_BUILD:-$(curl -s ${ARTIFACTORY_URL}/mcp-0.1/calico-bird/lastbuild)}"
BIRD_URL="${BIRD_URL:-${ARTIFACTORY_URL}/mcp-0.1/calico-bird/bird-$BIRD_BUILD}"
BIRD6_URL="${BIRD6_URL:-${ARTIFACTORY_URL}/mcp-0.1/calico-bird/bird6-$BIRD_BUILD}"
BIRDCL_URL="${BIRDCL_URL:-${ARTIFACTORY_URL}/mcp-0.1/calico-bird/birdcl-$BIRD_BUILD}"

BUILD_ARGS=`append_arg "CALICO_REPO=$CALICO_REPO"`
BUILD_ARGS=`append_arg "CALICO_VER=$CALICO_VER"`
BUILD_ARGS=`append_arg "LIBCALICO_REPO=$LIBCALICO_REPO"`
BUILD_ARGS=`append_arg "LIBCALICO_VER=$LIBCALICO_VER"`
BUILD_ARGS=`append_arg "CONFD_URL=$CONFD_URL"`
BUILD_ARGS=`append_arg "BIRD_URL=$BIRD_URL"`
BUILD_ARGS=`append_arg "BIRD6_URL=$BIRD6_URL"`
BUILD_ARGS=`append_arg "BIRDCL_URL=$BIRDCL_URL"`

if [ "${GERRIT_EVENT_TYPE}X" = "change-mergedX" -o "${GERRIT_CHANGE_NUMBER}X" = "X" ]; then
  # This is merged event or job triggered manually, so tag is mcp-0.1
  IMG_BUILD_TAG=mcp-0.1
else
  # otherwise let's user GERRIT_CHANGE_NUMBER
  IMG_BUILD_TAG="${GERRIT_CHANGE_NUMBER}"
fi

BUILD="${IMG_BUILD_TAG}-$(git rev-parse --short HEAD)"
NAME="${DOCKER_REPO}/${NODE_IMAGE}:${NODE_IMAGE_TAG}"
CTL_NAME="${DOCKER_REPO}/${CTL_IMAGE}:${NODE_IMAGE_TAG}"

mkdir -p artifacts
rm -f artifacts/*

echo "Building calico/ctl"
wget -N $BIRDCL_URL -O birdcl
chmod +x birdcl
docker run -v `pwd`:/code --rm $BUILD_IMAGE:$BUILD_IMAGE_TAG pyinstaller calicoctl.spec -ayF
cp dist/calicoctl ./calicoctl/
cd calicoctl
docker build --build-arg CTLBIN=calicoctl -t ${CTL_NAME}-${BUILD} .
cd ..

echo "Building calico/node"
LOCAL_CALICO_REPO="${WORKSPACE}/calico_node/calico_share"

# if we have CALICO_GERRIT_REFSPEC then this job was triggered by upstream calico and we need to prepare
# custom code
if [ -n "${CALICO_GERRIT_REFSPEC}" ]; then
  echo "Preparing calico repo from patchset"
  echo "Clean directory defore cloning into it"
  rm -rf "${LOCAL_CALICO_REPO}/.gitkeep"
  cd "${LOCAL_CALICO_REPO}"
  git clone "ssh://mcp-ci-gerrit@${GERRIT_HOST}:29418/${CALICO_GERRIT_PROJECT}" --branch "${CALICO_GERRIT_BRANCH}" .
  git fetch "ssh://mcp-ci-gerrit@${GERRIT_HOST}:29418/${CALICO_GERRIT_PROJECT}" "${CALICO_GERRIT_REFSPEC}" && \
    git cherry-pick FETCH_HEAD
  cd "${WORKSPACE}"
fi

cd calico_node
docker build $BUILD_ARGS -t ${NAME}-${BUILD} .
cd ..

echo "Pushing images"
set +x
docker login -u "${ARTIFACTORY_LOGIN}" -p "${ARTIFACTORY_PASSWORD}" -e "${ARTIFACTORY_USER_EMAIL}" "${CALICO_DOCKER_REGISTRY}"
set -x
docker push ${NAME}-${BUILD}
docker push ${CTL_NAME}-${BUILD}

# Save the last build ID
echo $BUILD > ./artifacts/lastbuild

CALICO_CONTAINERS_ARTIFACTS_FILE_YAML="./artifacts/calico-containers-${BUILD}.yaml"

# Create config yaml for Kargo
cat > "${CALICO_CONTAINERS_ARTIFACTS_FILE_YAML}" << EOF
calico_node_image_repo: ${DOCKER_REPO}/${NODE_IMAGE}
calicoctl_image_repo: ${DOCKER_REPO}/${CTL_IMAGE}
calico_version: ${NODE_IMAGE_TAG}-${BUILD}
EOF

if [ -n "${CALICO_CONTAINERS_ARTIFACTS_FILE}" ]; then
    save_tests_params "${CALICO_CONTAINERS_ARTIFACTS_FILE_YAML}" "${CALICO_CONTAINERS_ARTIFACTS_FILE}"
fi
