#!/bin/bash

set -ex

# do not perform any action if GERRIT_CHANGE_NUMBER is not defined
[ -z "${GERRIT_CHANGE_NUMBER}" ] && exit 0

# delete merged artifact
curl -u "${ARTIFACTORY_LOGIN}:${ARTIFACTORY_PASSWORD}" -X DELETE "${ARTIFACTORY_URL}/${GERRIT_CHANGE_NUMBER}" || true
