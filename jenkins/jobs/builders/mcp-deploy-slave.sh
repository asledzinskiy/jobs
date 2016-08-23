#!/bin/bash

set -ex

export ENV_DIR="${WORKSPACE}/.tox/mcp-ci"
export HOSTS=("${HOSTS_LIST}")
export HOSTNAME=$(hostname)
export ANSIBLE_HOST_KEY_CHECKING=False

if [[ -e "${ENV_DIR}" ]]; then
  rm -rf "${ENV_DIR}"
fi

tox -e mcp-ci
source "${ENV_DIR}/bin/activate"

sed -i 's/- hosts: slave-node/- hosts: all/g' "${WORKSPACE}/ansible/prepare-slave-node.yml"

# construct inventory for every host from HOSTS_LIST variable
for host in ${HOSTS[@]}; do
  if [[ "${host}" == "localhost" ]] || [[ "${host}" =~ ^127\. ]] || [[ "${host}" =~ ^${HOSTNAME}.* ]]; then
    echo "localhost detected - skipping!"
  else
    echo "${host} ansible_user=root ansible_connection=ssh" >> "${WORKSPACE}/inventory"
  fi
done

ansible-playbook "${WORKSPACE}/ansible/prepare-slave-node.yml" -i "${WORKSPACE}/inventory"

