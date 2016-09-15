#!/bin/sh

set -x
set -e

BUILD_ARGS=""

append_arg () {
  echo "$BUILD_ARGS --build-arg $1"
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

CONFD_BUILD="${CONFD_BUILD:-$(curl -s $ARTIFACTORY_URL/confd/lastbuild)}"
CONFD_URL="${CONFD_URL:-$ARTIFACTORY_URL/confd/confd-$CONFD_BUILD}"

BIRD_BUILD="${BIRD_BUILD:-$(curl -s $ARTIFACTORY_URL/calico-bird/lastbuild)}"
BIRD_URL="${BIRD_URL:-$ARTIFACTORY_URL/calico-bird/bird-$BIRD_BUILD}"
BIRD6_URL="${BIRD6_URL:-$ARTIFACTORY_URL/calico-bird/bird6-$BIRD_BUILD}"
BIRDCL_URL="${BIRDCL_URL:-$ARTIFACTORY_URL/calico-bird/birdcl-$BIRD_BUILD}"

BUILD_ARGS=`append_arg "CALICO_REPO=$CALICO_REPO"`
BUILD_ARGS=`append_arg "CALICO_VER=$CALICO_VER"`
BUILD_ARGS=`append_arg "LIBCALICO_REPO=$LIBCALICO_REPO"`
BUILD_ARGS=`append_arg "LIBCALICO_VER=$LIBCALICO_VER"`
BUILD_ARGS=`append_arg "CONFD_URL=$CONFD_URL"`
BUILD_ARGS=`append_arg "BIRD_URL=$BIRD_URL"`
BUILD_ARGS=`append_arg "BIRD6_URL=$BIRD6_URL"`
BUILD_ARGS=`append_arg "BIRDCL_URL=$BIRDCL_URL"`

BUILD=`git rev-parse --short HEAD`
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
cd calico_node
docker build $BUILD_ARGS -t ${NAME}-${BUILD} .
cd ..

echo "Pushing images"
set +x
docker login -u "${ARTIFACTORY_LOGIN}" -p "${ARTIFACTORY_PASSWORD}" -e "${ARTIFACTORY_USER_EMAIL}" "${CALICO_DOCKER_REGISTRY}"
set -x
docker push ${NAME}-${BUILD}
docker push ${CTL_NAME}-${BUILD}

# Create config yaml for Kargo
cat > ./artifacts/calico-containers-${BUILD}.yaml << EOF
calico_node_image_repo: ${DOCKER_REPO}/${NODE_IMAGE}
calicoctl_image_repo: ${DOCKER_REPO}/${CTL_IMAGE}
calico_version: ${NODE_IMAGE_TAG}-${BUILD}
EOF

# Save the last build ID
echo $BUILD > ./artifacts/lastbuild
