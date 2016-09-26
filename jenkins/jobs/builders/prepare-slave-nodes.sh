#!/bin/bash

set -ex

export VENV_PATH="${WORKSPACE}/.tox/mcp-ci"
export HOSTS=("${HOSTS_LIST}")
export HOSTNAME=$(hostname)
export ANSIBLE_HOST_KEY_CHECKING=False
export INVENTORY="${WORKSPACE}/conf/slave_nodes_inventory"

if [[ -e "${VENV_PATH}" ]]; then
  rm -rf "${VENV_PATH}"
fi

if [[ -z "${USERNAME}" ]]; then
  echo "No username is set!"
  exit 1
fi

tox -e mcp-ci
source "${VENV_PATH}/bin/activate"

echo "[slave-nodes]" > "${INVENTORY}"
cp tests/test_conf.yml conf/conf.yml

# construct inventory for every host from HOSTS_LIST variable
for host in ${HOSTS[@]}; do
  if [[ "${host}" == "localhost" ]] || [[ "${host}" =~ ^127\. ]] || [[ "${host}" =~ ^${HOSTNAME}.* ]]; then
    echo "localhost detected - skipping!"
  else
    echo "${host} ansible_user=${USERNAME} ansible_connection=ssh" >> "${INVENTORY}"
  fi
done

./mcp-ci.sh prepare-slave-nodes
