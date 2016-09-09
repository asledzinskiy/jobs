#!/bin/bash -xe

export ENV_DIR="${WORKSPACE}/.tox/mcp-ci"

echo "This is job for update jenkins jobs after the merge event."

if [[ -e "${ENV_DIR}" ]]; then
  rm -rf "${ENV_DIR}"
fi

tox -e mcp-ci

source "${ENV_DIR}/bin/activate"

set +x
cat > jenkins_jobs.ini << EOF
[jenkins]
user=${JJB_USER}
password=${JJB_PASS}
url=${JENKINS_URL}
[job_builder]
ignore_cache=True
recursive=True
EOF
set -x

jenkins-jobs --flush-cache --conf jenkins_jobs.ini update --delete-old jenkins/jobs
