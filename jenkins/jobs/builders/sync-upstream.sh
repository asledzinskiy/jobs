#!/bin/bash

set -ex

PROJECTS_TO_SYNC="${WORKSPACE}/project_to_sync.yaml"
PROJECTS_TO_SYNC_TCP="${WORKSPACE}/project_to_sync_tcp.yaml"
PROJECTS_TO_SYNC_FUEL_CCP="${WORKSPACE}/project_to_sync_fuel_ccp.yaml"
PROJECTS_TO_SYNC_MK="${WORKSPACE}/project_to_sync_mk.yaml"

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

- project: calicoctl
  src-repo: https://github.com/projectcalico/calicoctl
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/projectcalico/calicoctl
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

cat << EOF > "${PROJECTS_TO_SYNC_MK}"
- project: mcp-lab-heat-templates
  src-repo: git://github.com/Mirantis/mcp-lab-heat-templates.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/mcp-lab-heat-templates
  branches:
    - "*"

- project: mk-lab-salt-model
  src-repo: git://github.com/Mirantis/mk-lab-salt-model.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/mk-lab-salt-model
  branches:
    - "*"

- project: openstack-salt
  src-repo: git://github.com/openstack/openstack-salt.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/openstack-salt
  branches:
    - "*"

- project: openstack-salt-specs
  src-repo: git://github.com/openstack/openstack-salt-specs.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/openstack-salt-specs
  branches:
    - "*"

- project: salt-formula-apache
  src-repo: https://github.com/tcpcloud/salt-formula-apache.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-apache
  branches:
    - "*"

- project: salt-formula-aptly
  src-repo: https://github.com/tcpcloud/salt-formula-aptly.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-aptly
  branches:
    - "*"

- project: salt-formula-backupninja
  src-repo: https://github.com/tcpcloud/salt-formula-backupninja.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-backupninja
  branches:
    - "*"

- project: salt-formula-billometer
  src-repo: https://github.com/tcpcloud/salt-formula-billometer.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-billometer
  branches:
    - "*"

- project: salt-formula-bind
  src-repo: https://github.com/tcpcloud/salt-formula-bind.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-bind
  branches:
    - "*"

- project: salt-formula-ceilometer
  src-repo: git://git.openstack.org/openstack/salt-formula-ceilometer.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-ceilometer
  branches:
    - "*"

- project: salt-formula-ceph
  src-repo: https://github.com/tcpcloud/salt-formula-ceph.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-ceph
  branches:
    - "*"

- project: salt-formula-cinder
  src-repo: git://git.openstack.org/openstack/salt-formula-cinder.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-cinder
  branches:
    - "*"

- project: salt-formula-collectd
  src-repo: https://github.com/tcpcloud/salt-formula-collectd.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-collectd
  branches:
    - "*"

- project: salt-formula-docker
  src-repo: https://github.com/tcpcloud/salt-formula-docker.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-docker
  branches:
    - "*"

- project: salt-formula-dovecot
  src-repo: https://github.com/tcpcloud/salt-formula-dovecot.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-dovecot
  branches:
    - "*"

- project: salt-formula-elasticsearch
  src-repo: https://github.com/tcpcloud/salt-formula-elasticsearch.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-elasticsearch
  branches:
    - "*"

- project: salt-formula-foreman
  src-repo: https://github.com/tcpcloud/salt-formula-foreman.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-foreman
  branches:
    - "*"

- project: salt-formula-freeipa
  src-repo: https://github.com/tcpcloud/salt-formula-freeipa.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-freeipa
  branches:
    - "*"

- project: salt-formula-galera
  src-repo: https://github.com/tcpcloud/salt-formula-galera.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-galera
  branches:
    - "*"

- project: salt-formula-git
  src-repo: https://github.com/tcpcloud/salt-formula-git.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-git
  branches:
    - "*"

- project: salt-formula-gitlab
  src-repo: https://github.com/tcpcloud/salt-formula-gitlab.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-gitlab
  branches:
    - "*"

- project: salt-formula-glance
  src-repo: git://git.openstack.org/openstack/salt-formula-glance.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-glance
  branches:
    - "*"

- project: salt-formula-glusterfs
  src-repo: https://github.com/tcpcloud/salt-formula-glusterfs.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-glusterfs
  branches:
    - "*"

- project: salt-formula-grafana
  src-repo: https://github.com/tcpcloud/salt-formula-grafana.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-grafana
  branches:
    - "*"

- project: salt-formula-graphite
  src-repo: https://github.com/tcpcloud/salt-formula-graphite.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-graphite
  branches:
    - "*"

- project: salt-formula-haproxy
  src-repo: https://github.com/tcpcloud/salt-formula-haproxy.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-haproxy
  branches:
    - "*"

- project: salt-formula-heat
  src-repo: git://git.openstack.org/openstack/salt-formula-heat.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-heat
  branches:
    - "*"

- project: salt-formula-heka
  src-repo: https://github.com/tcpcloud/salt-formula-heka.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-heka
  branches:
    - "*"

- project: salt-formula-horizon
  src-repo: git://git.openstack.org/openstack/salt-formula-horizon.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-horizon
  branches:
    - "*"

- project: salt-formula-iptables
  src-repo: https://github.com/tcpcloud/salt-formula-iptables.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-iptables
  branches:
    - "*"

- project: salt-formula-isc-dhcp
  src-repo: https://github.com/tcpcloud/salt-formula-isc-dhcp.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-isc-dhcp
  branches:
    - "*"

- project: salt-formula-java
  src-repo: https://github.com/tcpcloud/salt-formula-java.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-java
  branches:
    - "*"

- project: salt-formula-jenkins
  src-repo: https://github.com/tcpcloud/salt-formula-jenkins.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-jenkins
  branches:
    - "*"

- project: salt-formula-kedb
  src-repo: https://github.com/tcpcloud/salt-formula-kedb.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-kedb
  branches:
    - "*"

- project: salt-formula-keepalived
  src-repo: https://github.com/tcpcloud/salt-formula-keepalived.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-keepalived
  branches:
    - "*"

- project: salt-formula-keystone
  src-repo: git://git.openstack.org/openstack/salt-formula-keystone.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-keystone
  branches:
    - "*"

- project: salt-formula-kibana
  src-repo: https://github.com/tcpcloud/salt-formula-kibana.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-kibana
  branches:
    - "*"

- project: salt-formula-kubernetes
  src-repo: git://git.openstack.org/openstack/salt-formula-kubernetes.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-kubernetes
  branches:
    - "*"

- project: salt-formula-letsencrypt
  src-repo: https://github.com/tcpcloud/salt-formula-letsencrypt.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-letsencrypt
  branches:
    - "*"

- project: salt-formula-libvirt
  src-repo: https://github.com/tcpcloud/salt-formula-libvirt.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-libvirt
  branches:
    - "*"

- project: salt-formula-linux
  src-repo: https://github.com/tcpcloud/salt-formula-linux.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-linux
  branches:
    - "*"

- project: salt-formula-logrotate
  src-repo: https://github.com/tcpcloud/salt-formula-logrotate.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-logrotate
  branches:
    - "*"

- project: salt-formula-magnum
  src-repo: https://github.com/tcpcloud/salt-formula-magnum.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-magnum
  branches:
    - "*"

- project: salt-formula-memcached
  src-repo: https://github.com/tcpcloud/salt-formula-memcached.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-memcached
  branches:
    - "*"

- project: salt-formula-midonet
  src-repo: git://git.openstack.org/openstack/salt-formula-midonet.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-midonet
  branches:
    - "*"

- project: salt-formula-mongodb
  src-repo: https://github.com/tcpcloud/salt-formula-mongodb.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-mongodb
  branches:
    - "*"

- project: salt-formula-murano
  src-repo: https://github.com/tcpcloud/salt-formula-murano.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-murano
  branches:
    - "*"

- project: salt-formula-mysql
  src-repo: https://github.com/tcpcloud/salt-formula-mysql.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-mysql
  branches:
    - "*"

- project: salt-formula-neutron
  src-repo: git://git.openstack.org/openstack/salt-formula-neutron.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-neutron
  branches:
    - "*"

- project: salt-formula-nfs
  src-repo: https://github.com/tcpcloud/salt-formula-nfs.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-nfs
  branches:
    - "*"

- project: salt-formula-nginx
  src-repo: https://github.com/tcpcloud/salt-formula-nginx.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-nginx
  branches:
    - "*"

- project: salt-formula-nodejs
  src-repo: https://github.com/tcpcloud/salt-formula-nodejs.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-nodejs
  branches:
    - "*"

- project: salt-formula-nova
  src-repo: git://git.openstack.org/openstack/salt-formula-nova.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-nova
  branches:
    - "*"

- project: salt-formula-ntp
  src-repo: https://github.com/tcpcloud/salt-formula-ntp.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-ntp
  branches:
    - "*"

- project: salt-formula-opencontrail
  src-repo: git://git.openstack.org/openstack/salt-formula-opencontrail.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-opencontrail
  branches:
    - "*"

- project: salt-formula-openssh
  src-repo: https://github.com/tcpcloud/salt-formula-openssh.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-openssh
  branches:
    - "*"

- project: salt-formula-openvstorage
  src-repo: https://github.com/tcpcloud/salt-formula-openvstorage.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-openvstorage
  branches:
    - "*"

- project: salt-formula-owncloud
  src-repo: https://github.com/tcpcloud/salt-formula-owncloud.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-owncloud
  branches:
    - "*"

- project: salt-formula-postfix
  src-repo: https://github.com/tcpcloud/salt-formula-postfix.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-postfix
  branches:
    - "*"

- project: salt-formula-postgresql
  src-repo: https://github.com/tcpcloud/salt-formula-postgresql.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-postgresql
  branches:
    - "*"

- project: salt-formula-pritunl
  src-repo: https://github.com/tcpcloud/salt-formula-pritunl.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-pritunl
  branches:
    - "*"

- project: salt-formula-python
  src-repo: https://github.com/tcpcloud/salt-formula-python.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-python
  branches:
    - "*"

- project: salt-formula-rabbitmq
  src-repo: https://github.com/tcpcloud/salt-formula-rabbitmq.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-rabbitmq
  branches:
    - "*"

- project: salt-formula-reclass
  src-repo: https://github.com/tcpcloud/salt-formula-reclass.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-reclass
  branches:
    - "*"

- project: salt-formula-redis
  src-repo: https://github.com/tcpcloud/salt-formula-redis.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-redis
  branches:
    - "*"

- project: salt-formula-roundcube
  src-repo: https://github.com/tcpcloud/salt-formula-roundcube.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-roundcube
  branches:
    - "*"

- project: salt-formula-rsync
  src-repo: https://github.com/tcpcloud/salt-formula-rsync.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-rsync
  branches:
    - "*"

- project: salt-formula-rsyslog
  src-repo: https://github.com/tcpcloud/salt-formula-rsyslog.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-rsyslog
  branches:
    - "*"

- project: salt-formula-salt
  src-repo: https://github.com/tcpcloud/salt-formula-salt.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-salt
  branches:
    - "*"

- project: salt-formula-sensu
  src-repo: https://github.com/tcpcloud/salt-formula-sensu.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-sensu
  branches:
    - "*"

- project: salt-formula-sphinx
  src-repo: https://github.com/tcpcloud/salt-formula-sphinx.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-sphinx
  branches:
    - "*"

- project: salt-formula-statsd
  src-repo: https://github.com/tcpcloud/salt-formula-statsd.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-statsd
  branches:
    - "*"

- project: salt-formula-supervisor
  src-repo: https://github.com/tcpcloud/salt-formula-supervisor.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-supervisor
  branches:
    - "*"

- project: salt-formula-swift
  src-repo: git://git.openstack.org/openstack/salt-formula-swift.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-swift
  branches:
    - "*"

- project: salt-formula-taiga
  src-repo: https://github.com/tcpcloud/salt-formula-taiga.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-taiga
  branches:
    - "*"

- project: salt-formula-varnish
  src-repo: https://github.com/tcpcloud/salt-formula-varnish.git
  dst-repo: ssh://${GIT_PUSH_USERNAME}@${GERRIT_HOST}:29418/mk/salt-formula-varnish
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
gitrepo sync ${FORCE_FLAG} "${PROJECTS_TO_SYNC_TCP}"
gitrepo sync ${FORCE_FLAG} "${PROJECTS_TO_SYNC_FUEL_CCP}"
gitrepo sync ${FORCE_FLAG} "${PROJECTS_TO_SYNC_MK}"
