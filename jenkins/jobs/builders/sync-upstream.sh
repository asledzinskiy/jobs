#!/bin/bash

set -ex

PROJECTS_TO_SYNC="${WORKSPACE}/project_to_sync.yaml"

cat << EOF > "${PROJECTS_TO_SYNC}"
- project: kubernetes
  src-repo: https://github.com/kubernetes/kubernetes
  dst-repo: ssh://${GERRIT_HOST}:29418/kubernetes/kubernetes
  branches:
    - "*"

- project: felix
  src-repo: https://github.com/projectcalico/felix
  dst-repo: ssh://${GERRIT_HOST}:29418/projectcalico/felix
  branches:
    - "*"

- project: calico-bird
  src-repo: https://github.com/projectcalico/calico-bird
  dst-repo: ssh://${GERRIT_HOST}:29418/projectcalico/calico-bird
  branches:
    - "*"

- project: calico-cni
  src-repo: https://github.com/projectcalico/calico-cni
  dst-repo: ssh://${GERRIT_HOST}:29418/projectcalico/calico-cni
  branches:
    - "*"

- project: calico-containers
  src-repo: https://github.com/projectcalico/calico-containers
  dst-repo: ssh://${GERRIT_HOST}:29418/projectcalico/calico-containers
  branches:
    - "*"

- project: confd
  src-repo: https://github.com/projectcalico/confd
  dst-repo: ssh://${GERRIT_HOST}:29418/projectcalico/confd
  branches:
    - "*"

- project: libcalico
  src-repo: https://github.com/projectcalico/libcalico
  dst-repo: ssh://${GERRIT_HOST}:29418/projectcalico/libcalico
  branches:
    - "*"

- project: libcalico-go
  src-repo: https://github.com/projectcalico/libcalico-go
  dst-repo: ssh://${GERRIT_HOST}:29418/projectcalico/libcalico-go
  branches:
    - "*"
EOF

VENV="${WORKSPACE}_VENV"

virtualenv "${VENV}"
source "${VENV}/bin/activate" || exit 1

pip install .

gitrepo sync "${PROJECTS_TO_SYNC}"
