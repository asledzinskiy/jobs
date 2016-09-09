#!/bin/bash
set -ex

VENV=${VENV:-$WORKSPACE/venv-update-kubernetes-images}

if [[ ! -d "${VENV}" ]]; then
  virtualenv --no-site-packages $VENV
fi
source ${VENV}/bin/activate
pip install git-review

HYPERKUBE_BUILD_NUMBER="${HYPERKUBE_BUILD_NUMBER:-lastSuccessfulBuild}"
K8S_CONFORMANCE_BUILD_NUMBER="${K8S_CONFORMANCE_BUILD_NUMBER:-lastSuccessfulBuild}"
CLUSTERNAME="${CLUSTERNAME:-test-cluster1}"
JENKINS_URL="${JENKINS_URL:-https://ci.mcp.mirantis.net/jenkins}"
GERRIT_TOPIC="${GERRIT_TOPIC:-update-inventory-for-$CLUSTERNAME}"
GERRIT_USERNAME="${GERRIT_USERNAME:-mcp-ci-gerrit}"
GERRIT_EMAIL="${GERRIT_EMAIL:-mcp-ci-gerrit@example.com}"

rm -rf inventory
git clone "${INVENTORY_REPO}" inventory
cd inventory
set -o pipefail
if [ -f custom.yaml ]; then
    sed -i '/^\(hyperkube_image_repo\|hyperkube_image_tag\|e2e_conformance_image_repo\|e2e_conformance_image_tag\):/d' custom.yaml
fi
curl -sf "${JENKINS_URL}"/job/mcp-k8s-hyperkube/${HYPERKUBE_BUILD_NUMBER}/artifact/hyperkube_image.yaml >>custom.yaml
curl -sf "${JENKINS_URL}"/job/mcp-k8s-conformance-test-runner/${K8S_CONFORMANCE_BUILD_NUMBER}/artifact/conformance_image.yaml >>custom.yaml
git add custom.yaml
if ! git commit -a -m "Update inventory for $CLUSTERNAME"; then
    echo "Inventory is up to date" 1>&2
    exit 0
fi

git -c user.email="${GERRIT_EMAIL}" -c gitreview.username="${GERRIT_USERNAME}" review -t "$GERRIT_TOPIC"
