#!/bin/bash

set -axe

get_custom_refs () {
  PROJECT="$1"
  TARGET_DIR="$2"
  REMOTE="https://review.openstack.org/openstack/${PROJECT}"
  shift 2
  pushd "$TARGET_DIR"
  for commit in "${@}" ; do
    git fetch "${REMOTE}" "${commit}" && git cherry-pick FETCH_HEAD
  done
  popd
}

error_exit () {
  echo "$@"
  exit 1
}


# get custom refs from gerrit
if [[ -n "$FUEL_CCP_TESTS_REFS" && "$FUEL_CCP_TESTS_REFS" != "none" ]]; then
  get_custom_refs fuel-ccp-tests "${WORKSPACE}" $FUEL_CCP_TESTS_REFS
fi

if [[ -n "$FUEL_CCP_INSTALLER_REFS" && "$FUEL_CCP_INSTALLER_REFS" != "none" ]]; then
  get_custom_refs fuel-ccp-installer "${WORKSPACE}/fuel-ccp-installer" $FUEL_CCP_INSTALLER_REFS
fi

# prepare environment
if [ ! -r "${VENV_PATH}/bin/activate" ]; then
  error_exit "Python virtual environment not found! Set correct VENV_PATH!"
fi

source "${VENV_PATH}/bin/activate"

if [ ! -r "${WORKSPACE}/fuel-ccp-installer/${DEPLOY_SCRIPT_REL_PATH}" ]; then
  error_exit "Deploy script \"${DEPLOY_SCRIPT_REL_PATH}\" not found in" \
    "\"${WORKSPACE}/fuel-ccp-installer/\"!"
fi

DEPLOY_SCRIPT="${WORKSPACE}/fuel-ccp-installer/${DEPLOY_SCRIPT_REL_PATH}"

# erase environment if KEEP_BEFORE isn't set to 'yes' or 'true'
if ! [[ "${KEEP_BEFORE}" == "yes" || "${KEEP_BEFORE}" == "true" ]]; then
  dos.py erase "${ENV_NAME}" || true
fi

# save environment name to destroy it by publisher in case of hang/abort
echo "export ENV_NAME=\"${ENV_NAME}\"" > "${WORKSPACE}/${DOS_ENV_NAME_PROPS_FILE:=.dos_environment_name}"

# run tests
declare -a TEST_ARGS
TEST_ARGS=("-s")

if [[ -n "${TEST_PATH}" && "${TEST_PATH}" != "none" ]]; then
  TEST_ARGS+=("${TEST_PATH}")
elif [[ -n "${TEST_EXPRESSION}" && "${TEST_EXPRESSION}" != "none" ]]; then
  TEST_ARGS+=("-k" "${TEST_EXPRESSION}")
fi

if [[ "$VERBOSE" == 'true' ]]; then
  TEST_ARGS+=("-vvv")
fi

exit_code=0

if ! py.test "${TEST_ARGS[@]}"; then
  exit_code=1
fi

# erase environment if test passed and KEEP_AFTER isn't set to 'yes' or 'true'
if [ ${exit_code} -eq 0 ]; then
  if ! [[ "${KEEP_AFTER}" == "yes" || "${KEEP_AFTER}" == "true" ]]; then
    dos.py erase "${ENV_NAME}" || true
  fi
fi

if [ ${exit_code} -gt 0 ]; then
  error_exit "Tests failed!"
fi
