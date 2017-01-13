#!/bin/bash

set -eux

echo "==> Setting up sudo"
sed -i "s/^.*requiretty/#Defaults requiretty/" /etc/sudoers
echo "vagrant ALL=(ALL) NOPASSWD: ALL" > /etc/sudoers.d/10_vagrant

echo "==> Configuring logging"
touch /var/log/daemon.log
chmod 666 /var/log/daemon.log
echo "daemon.* /var/log/daemon.log" >> /etc/rsyslog.d/50-default.conf

echo "==> Setting default locale to en_US.UTF-8"
echo "LC_ALL=en_US.UTF-8" >> /etc/environment

echo "==> Adding vagrant user to the docker group"
gpasswd -a vagrant docker
