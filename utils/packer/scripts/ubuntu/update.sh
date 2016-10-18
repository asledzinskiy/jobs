#!/bin/bash

set -eux

if [[ "${UPDATE}"  =~ true || "${UPDATE}" =~ 1 || "${UPDATE}" =~ yes ]]; then
  echo "==> Updating list of repositories"
  # apt-get update does not actually perform updates, it just downloads and indexes the list of packages
  apt-get -y update

  echo "==> Performing dist-upgrade (all packages and kernel)"

  apt-get -o Dpkg::Options::=--force-confdef -o \
  Dpkg::Options::=--force-confold -q -y \
  dist-upgrade --allow-downgrades
fi
