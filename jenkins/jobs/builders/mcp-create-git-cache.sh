#!/bin/bash

set -ex

GIT_K8S_CACHE_DIR="${GIT_K8S_CACHE_DIR:-/home/jenkins/kubernetes}"

# let's create cache if it doesn't exists
if [ ! -d "${GIT_K8S_CACHE_DIR}/.git" ]; then
    # let's create cache dir
    git clone https://github.com/kubernetes/kubernetes "${GIT_K8S_CACHE_DIR}"
fi
