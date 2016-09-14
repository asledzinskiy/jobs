#!/bin/bash

set -ex

export K8S_REPO_DIR="${WORKSPACE}/kubernetes/"

mkdir -p "${K8S_REPO_DIR}"
cd "${K8S_REPO_DIR}"

GIT_K8S_CACHE_DIR="${GIT_K8S_CACHE_DIR:-/home/jenkins/kubernetes}"

# clone repo from local copy
git clone file://"${GIT_K8S_CACHE_DIR}" .

git reset --hard
if ! git clean -x -f -d -q ; then
  sleep 1
  git clean -x -f -d -q
fi

git fetch ssh://mcp-ci-gerrit@"${GERRIT_HOST}":29418/"${GERRIT_PROJECT}" "${GERRIT_REFSPEC}" && git checkout FETCH_HEAD

mkdir -p "${ARTIFACTS}"

make clean

# FIXME: temporary workaround. Best is to have it fixed in upstream.
# This will not fail the whole job on its first run .
./cluster/kube-down.sh || true
# /FIXME.

make release-skip-tests

./cluster/kube-up.sh

go run hack/e2e.go -v --test --test_args="--report-dir=${ARTIFACTS} --ginkgo.focus=\[Conformance\]"

./cluster/kube-down.sh
