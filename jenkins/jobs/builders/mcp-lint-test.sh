#!/bin/bash

set -ex

export ENV_DIR="${WORKSPACE}/.tox/mcp-ci"
# let's add more rules
export RULES_PROJECT="https://github.com/tsukinowasha/ansible-lint-rules"
export RULES_LOCAL_PATH="${WORKSPACE}/.tox/"

# update rules repo if it was cloned, otherwise clone it
git -C "${RULES_LOCAL_PATH}" pull || git clone "${RULES_PROJECT}" "${RULES_LOCAL_PATH}"

tox -e mcp-ci
source "${ENV_DIR}/bin/activate"


if [ "${GERRIT_REFSPEC}" = "refs/heads/master" ]; then
  find "${WORKSPACE}" -name "*.yml" ! -path "./.tox/*" -type f -print0 | xargs -0 ansible-lint -R -r "${RULES_LOCAL_PATH}/rules"
else
  git diff HEAD~1 --name-only --diff-filter=AM | grep ".yml$" | xargs --no-run-if-empty ansible-lint -R -r "${RULES_LOCAL_PATH}/rules"
fi
