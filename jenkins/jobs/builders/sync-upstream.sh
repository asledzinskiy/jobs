#!/bin/bash

set -ex

PROJECTS_TO_SYNC="${WORKSPACE}/project_to_sync.yaml"
PROJECTS_TO_SYNC_CCP="${WORKSPACE}/project_to_sync_ccp.yaml"

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

cat << EOF > "${PROJECTS_TO_SYNC_CCP}"
- project: ccp-contrail-pipeline
  src-repo: git@github.com:Mirantis/ccp-contrail-pipeline.git
  dst-repo: ssh://${GERRIT_HOST}:29418/tcp/ccp-contrail-pipeline
  branches:
    - "master"
- project: ccp-devops-portal
  src-repo: git@github.com:Mirantis/ccp-devops-portal.git
  dst-repo: ssh://${GERRIT_HOST}:29418/tcp/ccp-devops-portal
  branches:
    - "master"
- project: ccp-docker-artifactory
  src-repo: git@github.com:Mirantis/ccp-docker-artifactory.git
  dst-repo: ssh://${GERRIT_HOST}:29418/tcp/ccp-docker-artifactory
  branches:
    - "master"
- project: ccp-docker-calico
  src-repo: git@github.com:Mirantis/ccp-docker-calico.git
  dst-repo: ssh://${GERRIT_HOST}:29418/tcp/ccp-docker-calico
  branches:
    - "master"
- project: ccp-docker-cassandra
  src-repo: git@github.com:Mirantis/ccp-docker-cassandra.git
  dst-repo: ssh://${GERRIT_HOST}:29418/tcp/ccp-docker-cassandra
  branches:
    - "master"
- project: ccp-docker-ceilometer
  src-repo: git@github.com:Mirantis/ccp-docker-ceilometer.git
  dst-repo: ssh://${GERRIT_HOST}:29418/tcp/ccp-docker-ceilometer
  branches:
    - "master"
- project: ccp-docker-cinder
  src-repo: git@github.com:Mirantis/ccp-docker-cinder.git
  dst-repo: ssh://${GERRIT_HOST}:29418/tcp/ccp-docker-cinder
  branches:
    - "master"
- project: ccp-docker-galera
  src-repo: git@github.com:Mirantis/ccp-docker-galera.git
  dst-repo: ssh://${GERRIT_HOST}:29418/tcp/ccp-docker-galera
  branches:
    - "master"
- project: ccp-docker-gerrit
  src-repo: git@github.com:Mirantis/ccp-docker-gerrit.git
  dst-repo: ssh://${GERRIT_HOST}:29418/tcp/ccp-docker-gerrit
  branches:
    - "master"
- project: ccp-docker-glance
  src-repo: git@github.com:Mirantis/ccp-docker-glance.git
  dst-repo: ssh://${GERRIT_HOST}:29418/tcp/ccp-docker-glance
  branches:
    - "master"
- project: ccp-docker-heat
  src-repo: git@github.com:Mirantis/ccp-docker-heat.git
  dst-repo: ssh://${GERRIT_HOST}:29418/tcp/ccp-docker-heat
  branches:
    - "master"
- project: ccp-docker-horizon
  src-repo: git@github.com:Mirantis/ccp-docker-horizon.git
  dst-repo: ssh://${GERRIT_HOST}:29418/tcp/ccp-docker-horizon
  branches:
    - "master"
- project: ccp-docker-hyperkube
  src-repo: git@github.com:Mirantis/ccp-docker-hyperkube.git
  dst-repo: ssh://${GERRIT_HOST}:29418/tcp/ccp-docker-hyperkube
  branches:
    - "master"
- project: ccp-docker-jenkins
  src-repo: git@github.com:Mirantis/ccp-docker-jenkins.git
  dst-repo: ssh://${GERRIT_HOST}:29418/tcp/ccp-docker-jenkins
  branches:
    - "master"
- project: ccp-docker-kafka
  src-repo: git@github.com:Mirantis/ccp-docker-kafka.git
  dst-repo: ssh://${GERRIT_HOST}:29418/tcp/ccp-docker-kafka
  branches:
    - "master"
- project: ccp-docker-keystone
  src-repo: git@github.com:Mirantis/ccp-docker-keystone.git
  dst-repo: ssh://${GERRIT_HOST}:29418/tcp/ccp-docker-keystone
  branches:
    - "master"
- project: ccp-docker-libvirt
  src-repo: git@github.com:Mirantis/ccp-docker-libvirt.git
  dst-repo: ssh://${GERRIT_HOST}:29418/tcp/ccp-docker-libvirt
  branches:
    - "master"
- project: ccp-docker-memcached
  src-repo: git@github.com:Mirantis/ccp-docker-memcached.git
  dst-repo: ssh://${GERRIT_HOST}:29418/tcp/ccp-docker-memcached
  branches:
    - "master"
- project: ccp-docker-mongodb
  src-repo: git@github.com:Mirantis/ccp-docker-mongodb.git
  dst-repo: ssh://${GERRIT_HOST}:29418/tcp/ccp-docker-mongodb
  branches:
    - "master"
- project: ccp-docker-neutron
  src-repo: git@github.com:Mirantis/ccp-docker-neutron.git
  dst-repo: ssh://${GERRIT_HOST}:29418/tcp/ccp-docker-neutron
  branches:
    - "master"
- project: ccp-docker-nova
  src-repo: git@github.com:Mirantis/ccp-docker-nova.git
  dst-repo: ssh://${GERRIT_HOST}:29418/tcp/ccp-docker-nova
  branches:
    - "master"
- project: ccp-docker-opencontrail
  src-repo: git@github.com:Mirantis/ccp-docker-opencontrail.git
  dst-repo: ssh://${GERRIT_HOST}:29418/tcp/ccp-docker-opencontrail
  branches:
    - "master"
- project: ccp-docker-rabbitmq
  src-repo: git@github.com:Mirantis/ccp-docker-rabbitmq.git
  dst-repo: ssh://${GERRIT_HOST}:29418/tcp/ccp-docker-rabbitmq
  branches:
    - "master"
- project: ccp-docker-swift
  src-repo: git@github.com:Mirantis/ccp-docker-swift.git
  dst-repo: ssh://${GERRIT_HOST}:29418/tcp/ccp-docker-swift
  branches:
    - "master"
- project: ccp-docker-redis
  src-repo: git@github.com:Mirantis/ccp-docker-redis.git
  dst-repo: ssh://${GERRIT_HOST}:29418/tcp/ccp-docker-redis
  branches:
    - "master"
- project: ccp-docker-zookeeper
  src-repo: git@github.com:Mirantis/ccp-docker-zookeeper.git
  dst-repo: ssh://${GERRIT_HOST}:29418/tcp/ccp-docker-zookeeper
  branches:
    - "master"
- project: ccp-pipeline-libs
  src-repo: git@github.com:Mirantis/ccp-pipeline-libs.git
  dst-repo: ssh://${GERRIT_HOST}:29418/tcp/ccp-pipeline-libs
  branches:
    - "master"
- project: ccp-poc-salt-model
  src-repo: git@github.com:Mirantis/ccp-poc-salt-model.git
  dst-repo: ssh://${GERRIT_HOST}:29418/tcp/ccp-poc-salt-model
  branches:
    - "master"
EOF

VENV="${WORKSPACE}_VENV"

virtualenv "${VENV}"
source "${VENV}/bin/activate" || exit 1

pip install .

gitrepo sync "${PROJECTS_TO_SYNC}"
# (skulanov) FIXME: remove this after complete switching to mcp gerrit
# but for new we shouldn't run sync for review.fuel-infra.org
if [ "${GERRIT_HOST}" != "review.fuel-infra.org" ]; then
  gitrepo sync "${PROJECTS_TO_SYNC_CCP}"
fi
