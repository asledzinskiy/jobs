#!/bin/bash -e

export ENV_DIR="${WORKSPACE}/.tox/mcp-ci"
source "${ENV_DIR}/bin/activate"

set -x
./tests/runtests.sh teardown
