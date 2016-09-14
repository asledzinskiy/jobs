#!/bin/bash

set -ex

export PATH=/bin:/usr/bin:/sbin:/usr/sbin:${PATH}

ACT=0

function update_devops () {
  ACT=1
  VIRTUAL_ENV=/home/jenkins/venv-fuel-devops-3.0

  export DEVOPS_DB_NAME=/home/jenkins/venv-fuel-devops-3.0.sqlite3.db
  export DEVOPS_DB_ENGINE="django.db.backends.sqlite3"

  if [[ -d "${VIRTUAL_ENV}" ]] && [[ "${FORCE_DELETE_DEVOPS}" == "true" ]]; then
    echo "Delete venv from ${VIRTUAL_ENV}"
    rm -rf ${VIRTUAL_ENV}
    rm ${DEVOPS_DB_NAME}
  fi

  if [ -f ${VIRTUAL_ENV}/bin/activate ]; then
    source ${VIRTUAL_ENV}/bin/activate
    echo "Python virtual env exist"
  else
    rm -rf ${VIRTUAL_ENV}
    virtualenv --no-site-packages  ${VIRTUAL_ENV}
    source ${VIRTUAL_ENV}/bin/activate
  fi

  #
  # fuel-devops use ~/.devops directory to store log configuration
  # we need to delete log.yaml befeore update to get it in current
  # version
  #
  rm -f ~/.devops/log.yaml

  # Prepare requirements file
  if [[ -n "${VENV_REQUIREMENTS}" ]]; then
    echo "Install with custom requirements"
    echo "${VENV_REQUIREMENTS}" >"${WORKSPACE}/venv-requirements.txt"
  else
    cp ${WORKSPACE}/fuel_ccp_tests/requirements.txt "${WORKSPACE}/venv-requirements.txt"
  fi

  # Upgrade pip inside virtualenv
  pip install pip --upgrade

  pip install -r "${WORKSPACE}/venv-requirements.txt" --upgrade
  echo "=============================="
  pip freeze
  echo "=============================="
  django-admin.py syncdb --settings=devops.settings --noinput
  django-admin.py migrate devops --settings=devops.settings --noinput
  deactivate

}

# DevOps
if [[ ${update_devops} == "true" ]]; then
  update_devops
fi

if [ ${ACT} -eq 0 ]; then
  echo "No action selected!"
  exit 1
fi

