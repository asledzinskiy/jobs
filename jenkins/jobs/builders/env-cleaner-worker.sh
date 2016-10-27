#!/bin/bash

set -ex

export DEVOPS_DB_ENGINE="django.db.backends.sqlite3"
export DEVOPS_DB_NAME="/home/jenkins/venv-fuel-devops-3.0.sqlite3.db"
export VENV_PATH="/home/jenkins/venv-fuel-devops-3.0"

if [ ! -r "${VENV_PATH}/bin/activate" ]; then
  error_exit "Python virtual environment not found! Set correct VENV_PATH!"
fi

source "${VENV_PATH}/bin/activate"

# Remove devops environments which are older than 2 days

while read i; do
  env=($i);
  env_name="${env[0]}";
  creation_time="${env[1]//_/ }";
  if [ $(($(date "+%s")-$(date -d "${creation_time}" "+%s"))) -gt 172800 ]; then
     echo "Removing environment: ${env_name}, which was created: ${creation_time}";
     dos.py erase "${env_name}";
  fi;
done < <(dos.py list --timestamps | tail -n +3)
