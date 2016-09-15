#!/bin/bash

set -ex

env

export DEPLOY_METHOD="kargo"
echo "Running on $NODE_NAME: $ENV_NAME"
bash -x "utils/jenkins/run_k8s_verify.sh"
