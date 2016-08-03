#!/bin/bash -e

export ENV_DIR="${WORKSPACE}/.tox/mcp-ci"

if [[ -e "${ENV_DIR}" ]]; then
  rm -rf "${ENV_DIR}"
fi

tox -e mcp-ci
source "${ENV_DIR}/bin/activate"

set -x
./tests/runtests.sh
