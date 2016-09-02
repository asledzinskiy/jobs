#!/bin/bash

set -ex

env

export ENV_NAME="k8s-env-$BUILD_TAG"
export DEPLOY_METHOD="kargo"
echo "Running on $NODE_NAME: $ENV_NAME"

status=0
bash -x "utils/jenkins/run_k8s_deploy_test.sh" || status=1

mkdir "${WORKSPACE}/_artifacts"
if [ -f "${WORKSPACE}/logs.tar.gz" ]; then
    mv "${WORKSPACE}/logs.tar.gz" "${WORKSPACE}/_artifacts"
fi

exit $status

