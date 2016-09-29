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

set_latest_artifatcs () {
    HYPERKUBE_IMAGES_DIR="mcp-k8s"
    set +x
    HYPERKUBE_LATEST=$(curl -s -u "${ARTIFACTORY_LOGIN}:${ARTIFACTORY_PASSWORD}" \
    "${ARTIFACTORY_URL}/api/storage/${HYPERKUBE_IMAGES_DIR}/images-info/?lastModified")
    HYPERKUBE_LATEST_LINK=$(echo "$HYPERKUBE_LATEST" | \
    python -c 'import sys,json; print json.load(sys.stdin)["uri"]')
    HYPERKUBE_LATEST_YAML=$(curl -s -u "${ARTIFACTORY_LOGIN}:${ARTIFACTORY_PASSWORD}" \
    "${HYPERKUBE_LATEST_LINK}" |
    python -c 'import sys,json; print json.load(sys.stdin)["downloadUri"]')
    HYPERKUBE_LATEST_ARTIFACTS=$(curl -s -u "${ARTIFACTORY_LOGIN}:${ARTIFACTORY_PASSWORD}" \
    "${HYPERKUBE_LATEST_YAML}")
    set -x

    # export hyperkube related parameters to uppercase environment vars
    set -a
    source <(python2 <<EOF
import sys, yaml
for k,v in yaml.load('''${HYPERKUBE_LATEST_ARTIFACTS}''').items():
    if 'hyperkube' not in k.lower():
        continue
    print '{0}={1}'.format(k.upper(), v)
EOF
    )
    set +a
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

# set version of downstream k8s artifacts
if [[ -z "${HYPERKUBE_IMAGE_TAG}" || "${HYPERKUBE_IMAGE_TAG}" == "latest" ]]; then
    set_latest_artifatcs
fi

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
