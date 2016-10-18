#!/bin/bash

set -eux

PACKAGES="
apt-transport-https
ca-certificates
curl
isc-dhcp-client
"

echo "==> Installing packages"
apt-get -y install ${PACKAGES}

apt-key adv --keyserver hkp://p80.pool.sks-keyservers.net:80 \
--recv-keys 58118E89F3A912897C070ADBF76221572C52609D
echo "deb https://apt.dockerproject.org/repo ubuntu-xenial main" \
> /etc/apt/sources.list.d/docker.list
apt-get update
apt-get -y install docker-engine
