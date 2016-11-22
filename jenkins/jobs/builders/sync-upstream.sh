#!/bin/bash

set -ex

PROJECTS_TO_SYNC="${WORKSPACE}/project_to_sync.yaml"
PROJECTS_TO_SYNC_TCP="${WORKSPACE}/project_to_sync_tcp.yaml"
PROJECTS_TO_SYNC_FUEL_CCP="${WORKSPACE}/project_to_sync_fuel_ccp.yaml"

cat << EOF > "${PROJECTS_TO_SYNC}"
- project: kubernetes
  src-repo: https://github.com/kubernetes/kubernetes
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/kubernetes/kubernetes
  branches:
    - "*"

- project: felix
  src-repo: https://github.com/projectcalico/felix
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/projectcalico/felix
  branches:
    - "*"

- project: calico-bird
  src-repo: https://github.com/projectcalico/calico-bird
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/projectcalico/calico-bird
  branches:
    - "*"

- project: calico-cni
  src-repo: https://github.com/projectcalico/calico-cni
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/projectcalico/calico-cni
  branches:
    - "*"

- project: calico-containers
  src-repo: https://github.com/projectcalico/calico-containers
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/projectcalico/calico-containers
  branches:
    - "*"

- project: confd
  src-repo: https://github.com/projectcalico/confd
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/projectcalico/confd
  branches:
    - "*"

- project: libcalico
  src-repo: https://github.com/projectcalico/libcalico
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/projectcalico/libcalico
  branches:
    - "*"

- project: libcalico-go
  src-repo: https://github.com/projectcalico/libcalico-go
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/projectcalico/libcalico-go
  branches:
    - "*"
EOF

cat << EOF > "${PROJECTS_TO_SYNC_TCP}"
- project: ccp-contrail-pipeline
  src-repo: git@github.com:Mirantis/ccp-contrail-pipeline.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/tcp/ccp-contrail-pipeline
  branches:
    - "master"
- project: ccp-devops-portal
  src-repo: git@github.com:Mirantis/ccp-devops-portal.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/tcp/ccp-devops-portal
  branches:
    - "master"
- project: ccp-docker-artifactory
  src-repo: git@github.com:Mirantis/ccp-docker-artifactory.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/tcp/ccp-docker-artifactory
  branches:
    - "master"
- project: ccp-docker-calico
  src-repo: git@github.com:Mirantis/ccp-docker-calico.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/tcp/ccp-docker-calico
  branches:
    - "master"
- project: ccp-docker-cassandra
  src-repo: git@github.com:Mirantis/ccp-docker-cassandra.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/tcp/ccp-docker-cassandra
  branches:
    - "master"
- project: ccp-docker-ceilometer
  src-repo: git@github.com:Mirantis/ccp-docker-ceilometer.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/tcp/ccp-docker-ceilometer
  branches:
    - "master"
- project: ccp-docker-cinder
  src-repo: git@github.com:Mirantis/ccp-docker-cinder.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/tcp/ccp-docker-cinder
  branches:
    - "master"
- project: ccp-docker-galera
  src-repo: git@github.com:Mirantis/ccp-docker-galera.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/tcp/ccp-docker-galera
  branches:
    - "master"
- project: ccp-docker-gerrit
  src-repo: git@github.com:Mirantis/ccp-docker-gerrit.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/tcp/ccp-docker-gerrit
  branches:
    - "master"
- project: ccp-docker-glance
  src-repo: git@github.com:Mirantis/ccp-docker-glance.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/tcp/ccp-docker-glance
  branches:
    - "master"
- project: ccp-docker-heat
  src-repo: git@github.com:Mirantis/ccp-docker-heat.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/tcp/ccp-docker-heat
  branches:
    - "master"
- project: ccp-docker-horizon
  src-repo: git@github.com:Mirantis/ccp-docker-horizon.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/tcp/ccp-docker-horizon
  branches:
    - "master"
- project: ccp-docker-hyperkube
  src-repo: git@github.com:Mirantis/ccp-docker-hyperkube.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/tcp/ccp-docker-hyperkube
  branches:
    - "master"
- project: ccp-docker-jenkins
  src-repo: git@github.com:Mirantis/ccp-docker-jenkins.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/tcp/ccp-docker-jenkins
  branches:
    - "master"
- project: ccp-docker-kafka
  src-repo: git@github.com:Mirantis/ccp-docker-kafka.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/tcp/ccp-docker-kafka
  branches:
    - "master"
- project: ccp-docker-keystone
  src-repo: git@github.com:Mirantis/ccp-docker-keystone.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/tcp/ccp-docker-keystone
  branches:
    - "master"
- project: ccp-docker-libvirt
  src-repo: git@github.com:Mirantis/ccp-docker-libvirt.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/tcp/ccp-docker-libvirt
  branches:
    - "master"
- project: ccp-docker-memcached
  src-repo: git@github.com:Mirantis/ccp-docker-memcached.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/tcp/ccp-docker-memcached
  branches:
    - "master"
- project: ccp-docker-mongodb
  src-repo: git@github.com:Mirantis/ccp-docker-mongodb.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/tcp/ccp-docker-mongodb
  branches:
    - "master"
- project: ccp-docker-neutron
  src-repo: git@github.com:Mirantis/ccp-docker-neutron.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/tcp/ccp-docker-neutron
  branches:
    - "master"
- project: ccp-docker-nova
  src-repo: git@github.com:Mirantis/ccp-docker-nova.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/tcp/ccp-docker-nova
  branches:
    - "master"
- project: ccp-docker-opencontrail
  src-repo: git@github.com:Mirantis/ccp-docker-opencontrail.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/tcp/ccp-docker-opencontrail
  branches:
    - "master"
- project: ccp-docker-rabbitmq
  src-repo: git@github.com:Mirantis/ccp-docker-rabbitmq.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/tcp/ccp-docker-rabbitmq
  branches:
    - "master"
- project: ccp-docker-swift
  src-repo: git@github.com:Mirantis/ccp-docker-swift.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/tcp/ccp-docker-swift
  branches:
    - "master"
- project: ccp-docker-redis
  src-repo: git@github.com:Mirantis/ccp-docker-redis.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/tcp/ccp-docker-redis
  branches:
    - "master"
- project: ccp-docker-zookeeper
  src-repo: git@github.com:Mirantis/ccp-docker-zookeeper.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/tcp/ccp-docker-zookeeper
  branches:
    - "master"
- project: ccp-pipeline-libs
  src-repo: git@github.com:Mirantis/ccp-pipeline-libs.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/tcp/ccp-pipeline-libs
  branches:
    - "master"
- project: ccp-poc-salt-model
  src-repo: git@github.com:Mirantis/ccp-poc-salt-model.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/tcp/ccp-poc-salt-model
  branches:
    - "master"
EOF

cat << EOF > "${PROJECTS_TO_SYNC_FUEL_CCP}"
- project: fuel-ccp
  src-repo: git://git.openstack.org/openstack/fuel-ccp
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/ccp/fuel-ccp
  branches:
    - "*"

- project: fuel-ccp-ceph
  src-repo: git://git.openstack.org/openstack/fuel-ccp-ceph
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/ccp/fuel-ccp-ceph
  branches:
    - "*"

- project: fuel-ccp-ci-config
  src-repo: git://git.openstack.org/openstack/fuel-ccp-ci-config
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/ccp/fuel-ccp-ci-config
  branches:
    - "*"

- project: fuel-ccp-cinder
  src-repo: git://git.openstack.org/openstack/fuel-ccp-cinder
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/ccp/fuel-ccp-cinder
  branches:
    - "*"

- project: fuel-ccp-debian-base
  src-repo: git://git.openstack.org/openstack/fuel-ccp-debian-base
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/ccp/fuel-ccp-debian-base
  branches:
    - "*"

- project: fuel-ccp-entrypoint
  src-repo: git://git.openstack.org/openstack/fuel-ccp-entrypoint
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/ccp/fuel-ccp-entrypoint
  branches:
    - "*"

- project: fuel-ccp-etcd
  src-repo: git://git.openstack.org/openstack/fuel-ccp-etcd
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/ccp/fuel-ccp-etcd
  branches:
    - "*"

- project: fuel-ccp-galera
  src-repo: git://git.openstack.org/openstack/fuel-ccp-galera
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/ccp/fuel-ccp-galera
  branches:
    - "*"

- project: fuel-ccp-glance
  src-repo: git://git.openstack.org/openstack/fuel-ccp-glance
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/ccp/fuel-ccp-glance
  branches:
    - "*"

- project: fuel-ccp-heat
  src-repo: git://git.openstack.org/openstack/fuel-ccp-heat
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/ccp/fuel-ccp-heat
  branches:
    - "*"

- project: fuel-ccp-horizon
  src-repo: git://git.openstack.org/openstack/fuel-ccp-horizon
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/ccp/fuel-ccp-horizon
  branches:
    - "*"

- project: fuel-ccp-installer
  src-repo: git://git.openstack.org/openstack/fuel-ccp-installer
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/ccp/fuel-ccp-installer
  branches:
    - "*"

- project: fuel-ccp-ironic
  src-repo: git://git.openstack.org/openstack/fuel-ccp-ironic
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/ccp/fuel-ccp-ironic
  branches:
    - "*"

- project: fuel-ccp-keystone
  src-repo: git://git.openstack.org/openstack/fuel-ccp-keystone
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/ccp/fuel-ccp-keystone
  branches:
    - "*"

- project: fuel-ccp-mariadb
  src-repo: git://git.openstack.org/openstack/fuel-ccp-mariadb
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/ccp/fuel-ccp-mariadb
  branches:
    - "*"

- project: fuel-ccp-memcached
  src-repo: git://git.openstack.org/openstack/fuel-ccp-memcached
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/ccp/fuel-ccp-memcached
  branches:
    - "*"

- project: fuel-ccp-murano
  src-repo: git://git.openstack.org/openstack/fuel-ccp-murano
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/ccp/fuel-ccp-murano
  branches:
    - "*"

- project: fuel-ccp-neutron
  src-repo: git://git.openstack.org/openstack/fuel-ccp-neutron
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/ccp/fuel-ccp-neutron
  branches:
    - "*"

- project: fuel-ccp-nova
  src-repo: git://git.openstack.org/openstack/fuel-ccp-nova
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/ccp/fuel-ccp-nova
  branches:
    - "*"

- project: fuel-ccp-openstack-base
  src-repo: git://git.openstack.org/openstack/fuel-ccp-openstack-base
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/ccp/fuel-ccp-openstack-base
  branches:
    - "*"

- project: fuel-ccp-rabbitmq
  src-repo: git://git.openstack.org/openstack/fuel-ccp-rabbitmq
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/ccp/fuel-ccp-rabbitmq
  branches:
    - "*"

- project: fuel-ccp-sahara
  src-repo: git://git.openstack.org/openstack/fuel-ccp-sahara
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/ccp/fuel-ccp-sahara
  branches:
    - "*"

- project: fuel-ccp-searchlight
  src-repo: git://git.openstack.org/openstack/fuel-ccp-searchlight
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/ccp/fuel-ccp-searchlight
  branches:
    - "*"

- project: fuel-ccp-specs
  src-repo: git://git.openstack.org/openstack/fuel-ccp-specs
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/ccp/fuel-ccp-specs
  branches:
    - "*"

- project: fuel-ccp-stacklight
  src-repo: git://git.openstack.org/openstack/fuel-ccp-stacklight
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/ccp/fuel-ccp-stacklight
  branches:
    - "*"

- project: fuel-ccp-tests
  src-repo: git://git.openstack.org/openstack/fuel-ccp-tests
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/ccp/fuel-ccp-tests
  branches:
    - "*"

- project: fuel-ccp-zmq
  src-repo: git://git.openstack.org/openstack/fuel-ccp-zmq
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/ccp/fuel-ccp-zmq
  branches:
    - "*"
EOF

VENV="${WORKSPACE}_VENV"

virtualenv "${VENV}"
source "${VENV}/bin/activate" || exit 1

pip install .

if [ "${PUSH_FORCE}" == "true" ]; then
  FORCE_FLAG="--force"
else
  FORCE_FLAG=""
fi

gitrepo sync ${FORCE_FLAG} "${PROJECTS_TO_SYNC}"
# (skulanov) FIXME: remove this after complete switching to mcp gerrit
# but for new we shouldn't run sync for review.fuel-infra.org
if [ "${GERRIT_HOST}" != "review.fuel-infra.org" ]; then
  gitrepo sync ${FORCE_FLAG} "${PROJECTS_TO_SYNC_TCP}"
  gitrepo sync ${FORCE_FLAG} "${PROJECTS_TO_SYNC_FUEL_CCP}"
fi
