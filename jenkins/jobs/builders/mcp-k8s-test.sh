#!/bin/bash

set -ex

# clone repo from local copy
git clone file:///home/jenkins/kubernetes .

git reset --hard
if ! git clean -x -f -d -q ; then
  sleep 1
  git clean -x -f -d -q
fi

if [[ ! -n "${GERRIT_PROJECT}" ]]; then
  GERRIT_PROJECT="${K8S_REPO}"
fi

git fetch ssh://mcp-ci-gerrit@review.fuel-infra.org:29418/"${GERRIT_PROJECT}" "${GERRIT_REFSPEC:-}" && git checkout FETCH_HEAD

# Create directory used to store artifacts generated by tests
mkdir "${WORKSPACE}/_artifacts"

# Get image from docker registry
docker pull "${DOCKER_IMAGE}"

# Start test
cat << 'EOF' | docker run --rm=true \
    -v "${WORKSPACE}:/workspace" \
    -e "KUBE_COVER=${COVERAGE}" \
    "${DOCKER_IMAGE}"
