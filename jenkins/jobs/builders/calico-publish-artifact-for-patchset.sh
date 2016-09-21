#!/bin/bash

set -ex

# do not perform any action if GERRIT_CHANGE_NUMBER is not defined
[ -z "${GERRIT_CHANGE_NUMBER}" ] && exit 0

# save artifacts
set +x
for artifact in ${WORKSPACE}/artifacts/*; do
  curl -u "${ARTIFACTORY_LOGIN}:${ARTIFACTORY_PASSWORD}" \
  -X PUT "${ARTIFACTORY_URL}/${GERRIT_CHANGE_NUMBER}/${GERRIT_PROJECT##*/}/${artifact##*/}" \
  -T "${artifact}"
done
set -x
