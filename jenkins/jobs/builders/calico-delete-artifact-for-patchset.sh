#!/bin/bash

set -ex

# delete merged artifact
curl -u "${ARTIFACTORY_LOGIN}:${ARTIFACTORY_PASSWORD}" -X DELETE "${ARTIFACTORY_URL}/${GERRIT_CHANGE_NUMBER}" || true
