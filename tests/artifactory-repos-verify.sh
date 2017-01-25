#!/bin/bash
set -ex

export DIR=$(dirname ${0})
yamllint -c ${DIR}/yamllint.yaml $(find "${DIR}/../artifactory/" -type f -name '*.yaml')
