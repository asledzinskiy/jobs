#!/bin/bash

set -xeu

if [[ "${USE_MIRROR}"  =~ true || "${USE_MIRROR}" =~ 1 || "${USE_MIRROR}" =~ yes ]]; then
  echo "==> Creating source.list with mirrors"
  cat > /etc/apt/sources.list <<EOF
deb https://artifactory.mcp.mirantis.net/ubuntu-virtual xenial main restricted universe multiverse
deb https://artifactory.mcp.mirantis.net/ubuntu-virtual xenial-updates main restricted universe multiverse
deb https://artifactory.mcp.mirantis.net/ubuntu-virtual xenial-backports main restricted universe multiverse
deb https://artifactory.mcp.mirantis.net/ubuntu-virtual xenial-security main restricted universe multiverse
EOF
fi
