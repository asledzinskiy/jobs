#!/bin/bash

set -ex

GIT_K8S_CACHE_DIR="${GIT_K8S_CACHE_DIR:-/home/jenkins/kubernetes}"

# clone repo from local copy
git clone file://"${GIT_K8S_CACHE_DIR}" .

git reset --hard
if ! git clean -x -f -d -q ; then
  sleep 1
  git clean -x -f -d -q
fi

if [[ ! -n "${GERRIT_PROJECT}" ]]; then
  GERRIT_PROJECT="${K8S_REPO}"
fi

# sync local copy of kubernetes repo with the remote
git remote add kubernetes ssh://mcp-ci-gerrit@"${GERRIT_HOST}":29418/"${GERRIT_PROJECT}"
$(git pull kubernetes; echo true)

git fetch ssh://mcp-ci-gerrit@"${GERRIT_HOST}":29418/"${GERRIT_PROJECT}" "${GERRIT_REFSPEC:-}" && git checkout FETCH_HEAD

# Create directory used to store artifacts generated by tests
mkdir "${WORKSPACE}/_artifacts"

# Get image from docker registry
docker rmi "${DOCKER_IMAGE}" || true
docker pull "${DOCKER_IMAGE}"

# Start test
cat << 'EOF' | docker run --rm=true \
    -v "${WORKSPACE}:/workspace" \
    -e "KUBE_COVER=${COVERAGE}" \
    "${DOCKER_IMAGE}"
