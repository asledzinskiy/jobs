#!/bin/bash

set -axe

source <(echo "${ADDITIONAL_PARAMETERS}")

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

export_params_from_yaml () {
    # Export parameters from YAML ($1) which match filter ($2)
    # to uppercase environment variables. The variables are used by tests.
    local MYAML="$1"
    local FILTER="$2"
    set -a
    source <(python2 <<EOF
import sys, yaml
for k,v in yaml.load('''${MYAML}''').items():
    if '${FILTER}' not in k.lower():
        continue
    print '{0}={1}'.format(k.upper(), v)
EOF
    )
    set +a
}

latest_image_tag_lookup () {
    local DOCKER_REGISTRY="${1}"
    local DOCKER_IMAGE="${2}"

    python2 <<EOF
import requests, sys

requests.packages.urllib3.disable_warnings()

tags_link ='https://${DOCKER_REGISTRY}/v2/${DOCKER_IMAGE}/tags/list'
digest_link = 'https://${DOCKER_REGISTRY}/v2/${DOCKER_IMAGE}/manifests/{tag}'

def get_digest(tag):
    return requests.get(digest_link.format(tag=tag),
                        headers={
                            'Accept': 'application/vnd.docker.distribution.manifest.v2+json'
                            }
                        ).headers['Docker-Content-Digest']

latest_digest = get_digest('latest')
same_digest_tags = set([])

for tag in requests.get(tags_link).json()['tags']:
    if tag == 'latest':
        continue
    if get_digest(tag) == latest_digest:
        same_digest_tags.add(tag)

sys.stderr.write("The following image tags point to the 'latest' "
       "now: [{0}]\n".format(" ".join(same_digest_tags)))
print same_digest_tags.pop() if same_digest_tags else 'latest'
EOF
}

set_latest_artifacts () {
    local DOCKER_IMAGE_REPO_VAR="$1"
    local DOCKER_IMAGE_TAG_VAR="$2"
    if printenv &>/dev/null "${DOCKER_IMAGE_TAG_VAR}" && \
       [[ -z "${!DOCKER_IMAGE_TAG_VAR}" || "${!DOCKER_IMAGE_TAG_VAR}" == "latest" ]]; then
        if [ -n "${!DOCKER_IMAGE_REPO_VAR}" ]; then
            local DOCKER_REGISTRY="${!DOCKER_IMAGE_REPO_VAR%%/*}"
            local DOCKER_IMAGE="${!DOCKER_IMAGE_REPO_VAR#*/}"
            local DOCKER_IMAGE_LATEST_TAG=$(latest_image_tag_lookup "${DOCKER_REGISTRY}" "${DOCKER_IMAGE}")
            eval ${DOCKER_IMAGE_TAG_VAR}="\${DOCKER_IMAGE_LATEST_TAG:-${!DOCKER_IMAGE_TAG_VAR}}"

        else
            echo "${DOCKER_IMAGE_TAG_VAR} variable isn't set, can't inspect 'latest' image!"
        fi
    fi
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
set_latest_artifacts "HYPERKUBE_IMAGE_REPO" "HYPERKUBE_IMAGE_TAG"

# set version of downstream calico artifacts
if printenv &>/dev/null CALICO_VERSION; then
    export CALICOCTL_IMAGE_TAG="${CALICOCTL_IMAGE_TAG:-${CALICO_VERSION}}"
    export CALICO_NODE_IMAGE_TAG="${CALICO_NODE_IMAGE_TAG:-${CALICO_VERSION}}"
fi
set_latest_artifacts "CALICOCTL_IMAGE_REPO" "CALICOCTL_IMAGE_TAG"
set_latest_artifacts "CALICO_NODE_IMAGE_REPO" "CALICO_NODE_IMAGE_TAG"
set_latest_artifacts "CALICO_CNI_IMAGE_REPO" "CALICO_CNI_IMAGE_TAG"

# run tests
declare -a TEST_ARGS
TEST_ARGS=("-s")
TEST_ARGS+=("-ra")

if [[ -n "${TEST_PATH}" && "${TEST_PATH}" != "none" ]]; then
  TEST_ARGS+=("${TEST_PATH}")
elif [[ -n "${TEST_EXPRESSION}" && "${TEST_EXPRESSION}" != "none" ]]; then
  TEST_ARGS+=("-k" "${TEST_EXPRESSION}")
elif [[ -n "${TEST_MARK}" && "${TEST_MARK}" != "none" ]]; then
  TEST_ARGS+=("-m" "${TEST_MARK}")
fi

if [[ "$VERBOSE" == 'true' ]]; then
  TEST_ARGS+=("-vvv")
fi

exit_code=0

export IMAGE_PATH=$(readlink -f "${IMAGE_PATH}")

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
