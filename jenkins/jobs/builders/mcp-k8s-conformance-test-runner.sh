#!/bin/bash

set -ex

if [[ -z "${KUBE_DOCKER_OWNER:-}" ]]; then
  echo "KUBE_DOCKER_OWNER must be set"
  exit -1
fi

if [[ -z "${KUBE_DOCKER_REGISTRY:-}" ]]; then
  echo "KUBE_DOCKER_REGISTRY must be set"
  exit -1
fi

GIT_K8S_CACHE_DIR="${GIT_K8S_CACHE_DIR:-/home/jenkins/kubernetes}"

# clone repo from local copy
git clone file://"${GIT_K8S_CACHE_DIR}" .

git reset --hard
if ! git clean -x -f -d -q ; then
  sleep 1
  git clean -x -f -d -q
fi

git fetch ssh://mcp-ci-gerrit@"${GERRIT_HOST}":29418/"${GERRIT_PROJECT}" "${GERRIT_REFSPEC}" && git checkout FETCH_HEAD

export GIT_COMMIT_TAG_ID=$(git describe --tags --abbrev=4)

export KUBE_DOCKER_VERSION="${GIT_COMMIT_TAG_ID}_${BUILD_NUMBER}"
export KUBE_DOCKER_CONFORMANCE_TAG="${KUBE_DOCKER_REGISTRY}/${KUBE_DOCKER_CONFORMANCE_REPOSITORY}:${KUBE_DOCKER_VERSION}"

make -C "${WORKSPACE}" release-skip-tests

mkdir "${WORKSPACE}/_build_test_runner"
mv "${WORKSPACE}/_output/release-tars/kubernetes-test.tar.gz" \
   "${WORKSPACE}/_output/release-tars/kubernetes.tar.gz" \
   "${WORKSPACE}/_build_test_runner"

cat >"${WORKSPACE}/_build_test_runner/Dockerfile" <<'EOF'
FROM golang:1.6.3

RUN mkdir -p /go/src/k8s.io
ADD kubernetes-test.tar.gz /go/src/k8s.io/
ADD kubernetes.tar.gz /go/src/k8s.io/
COPY entrypoint.sh /
RUN chmod +x /entrypoint.sh
WORKDIR /go/src/k8s.io/kubernetes
CMD /entrypoint.sh
LABEL com.mirantis.image-specs.gerrit_change_url="${GERRIT_CHANGE_URL}" \
      com.mirantis.image-specs.build_url="${BUILD_URL}" \
      com.mirantis.image-specs.patchset="${GERRIT_PATCHSET_REVISION}"
EOF

cat >"${WORKSPACE}/_build_test_runner/entrypoint.sh" <<'EOF'
#!/bin/bash
set -u -e

function escape_test_name() {
    sed 's/[]\$*.^|()[]/\\&/g; s/\s\+/\\s+/g' <<< "$1" | tr -d '\n'
}

TESTS_TO_SKIP=(
    '[k8s.io] Port forwarding [k8s.io] With a server that expects no client request should support a client that connects, sends no data, and disconnects [Conformance]'
    '[k8s.io] Port forwarding [k8s.io] With a server that expects a client request should support a client that connects, sends no data, and disconnects [Conformance]'
    '[k8s.io] Port forwarding [k8s.io] With a server that expects a client request should support a client that connects, sends data, and disconnects [Conformance]'
    '[k8s.io] Downward API volume should update annotations on modification [Conformance]'
    '[k8s.io] DNS should provide DNS for services [Conformance]'
    '[k8s.io] Kubectl client [k8s.io] Kubectl patch should add annotations for pods in rc [Conformance]'
)

function skipped_test_names () {
    local first=y
    for name in "${TESTS_TO_SKIP[@]}"; do
        if [ -z "$first" ]; then
            echo -n "|"
        else
            first=
        fi
        echo -n "$(escape_test_name "$name")\$"
    done
}

FOCUS="${FOCUS:-}"
API_SERVER="${API_SERVER:-}"
if [ -z "$API_SERVER" ]; then
    echo "Must provide API_SERVER env var" 1>&2
    exit 1
fi

export KUBERNETES_PROVIDER=skeleton
export KUBERNETES_CONFORMANCE_TEST=y

# Configure kube config
cluster/kubectl.sh config set-cluster local --server="$API_SERVER" --insecure-skip-tls-verify=true
cluster/kubectl.sh config set-context local --cluster=local --user=local
cluster/kubectl.sh config use-context local

if [ -z "$FOCUS" ]; then
    # non-serial tests can be run in parallel mode
    GINKGO_PARALLEL=y go run hack/e2e.go --v --test -check_version_skew=false \
      --check_node_count=false \
      --test_args="--ginkgo.focus=\[Conformance\] --ginkgo.skip=\[Serial\]|\[Flaky\]|\[Feature:.+\]|$(skipped_test_names)"

    # serial tests must be run without GINKGO_PARALLEL
    go run hack/e2e.go --v --test -check_version_skew=false --check_node_count=false \
      --test_args="--ginkgo.focus=\[Serial\].*\[Conformance\] --ginkgo.skip=$(skipped_test_names)"
else
    go run hack/e2e.go --v --test -check_version_skew=false --check_node_count=false \
      --test_args="--ginkgo.focus=$(escape_test_name "$FOCUS")"
fi
EOF

docker build -t "${KUBE_DOCKER_CONFORMANCE_TAG}" "${WORKSPACE}/_build_test_runner"

set +x
docker login -u "${ARTIFACTORY_HYPERKUBE_LOGIN}" -p "${ARTIFACTORY_HYPERKUBE_PASSWORD}" -e "${ARTIFACTORY_USER_EMAIL}" "${KUBE_DOCKER_REGISTRY}"
set -x
docker push "${KUBE_DOCKER_CONFORMANCE_TAG}"

# clean images locally
docker rmi -f "${KUBE_DOCKER_CONFORMANCE_TAG}" || true

# generate image description artifact
cat <<EOF > "${WORKSPACE}/conformance_image_${KUBE_DOCKER_VERSION}.yaml"
e2e_conformance_image_repo: "${KUBE_DOCKER_REGISTRY}/${KUBE_DOCKER_CONFORMANCE_REPOSITORY}"
e2e_conformance_image_tag: "${KUBE_DOCKER_VERSION}"
gerrit_change_url: "${GERRIT_CHANGE_URL}"
EOF
