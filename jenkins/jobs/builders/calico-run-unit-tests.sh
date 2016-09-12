#!/bin/bash

set -ex

prepare_pyenv () {
    PACKAGES=("tox" "coverage")
    VENV_PATH="${WORKSPACE}/python-venv-${JOB_NAME}-${BUILD_NUMBER}"
    virtualenv "${VENV_PATH}"
    source "${VENV_PATH}/bin/activate"
    pip install ${PACKAGES[@]}
}

clean_pyenv () {
  if [ -d "$VIRTUAL_ENV" ]; then
      VENV_PATH="$VIRTUAL_ENV"
      deactivate
      rm -rf "$VENV_PATH"
  fi
}

prepare_pyenv

if bash -x -c 'VIRTUAL_ENV="" ./run-unit-test.sh'; then
    clean_pyenv
    echo "Tests passed!"
else
    clean_pyenv
    echo "Tests failed!"
    exit 1
fi
