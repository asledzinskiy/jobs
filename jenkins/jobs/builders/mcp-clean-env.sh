#!/bin/bash

set -ex

ACTION="${ACTION:-0}"

if [ "${ACTION}" -eq 0 ]; then
  echo "No action has been specified"
  exit 1
fi

if [ -z "${ENV_NAME}" ]; then
  echo "No ENV_NAME has been set!"
  exit 1
fi

export VIRTUAL_ENV="/home/jenkins/venv-fuel-devops-3.0"
export DEVOPS_DB_NAME="/home/jenkins/venv-fuel-devops-3.0.sqlite3.db"
export DEVOPS_DB_ENGINE="django.db.backends.sqlite3"

if [ -d "${VIRTUAL_ENV}" ]; then
  source "${VIRTUAL_ENV}/bin/activate"
else
  echo "Unable to find ${VIRTUAL_ENV} dir!"
  exit 1
fi

dos.py "${ACTION}" "${ENV_NAME}"
