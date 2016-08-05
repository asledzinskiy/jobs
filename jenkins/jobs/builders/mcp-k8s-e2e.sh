#!/bin/bash

set -ex

# clone repo from local copy
git clone file:///home/jenkins/kubernetes .

git reset --hard
if ! git clean -x -f -d -q ; then
  sleep 1
  git clean -x -f -d -q
fi

git fetch ssh://mcp-ci-gerrit@review.fuel-infra.org:29418/${GERRIT_PROJECT} "${GERRIT_REFSPEC}" && git checkout FETCH_HEAD

mkdir -p "${ARTIFACTS}"

make clean

./cluster/kube-down.sh

make release-skip-tests

./cluster/kube-up.sh

go run hack/e2e.go -v --test --test_args="--report-dir=${ARTIFACTS} --ginkgo.focus=\[Conformance\]"

./cluster/kube-down.sh
