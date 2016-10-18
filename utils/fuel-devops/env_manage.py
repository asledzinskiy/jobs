# -*- coding: utf-8 -*-
from copy import deepcopy
import os
import sys

from devops.helpers.templates import yaml_template_load
from devops.models import Environment


def create_config():
    env = os.environ

    conf_path = env['CONF_PATH']
    conf = yaml_template_load(conf_path)

    group = conf['template']['devops_settings']['groups'][0]
    defined = filter(lambda x: x['role'] == 'generic-node',
                     group['nodes'])
    node_params = filter(lambda x: x['name'].endswith('generic-node'),
                         group['nodes'])[0]['params']

    return conf


def _get_free_eth_interface(node):
    taken = [i['label'] for i in node['params']['interfaces']]
    iface = 'eth'
    index = 0
    while True:
        new_iface = '{}{}'.format(iface, index)
        if new_iface not in taken:
            return new_iface
        index += 1


def get_env():
    env = os.environ
    env_name = env['ENV_NAME']
    return Environment.get(name=env_name)


def get_node_ip(env):
    admin = env.get_node(name='generic-node')
    return admin.get_ip_address_by_network_name('private')


def get_bridged_iface_mac(env, ip):
    for node in env.get_nodes():
        ips = [iface.addresses[0].ip_address for iface in node.interfaces
               if iface.addresses]
        if ip in ips:
            iface = node.get_interface_by_network_name('public')
            return iface.mac_address


def define_from_config(conf):
    env = Environment.create_environment(conf)
    env.define()
    env.start()


if __name__ == '__main__':
    if len(sys.argv) < 2:
        sys.exit(2)
    cmd = sys.argv[1]
    if cmd == 'create_env':
        config = create_config()
        define_from_config(config)
    elif cmd == 'get_node_ip':
        sys.stdout.write(get_node_ip(get_env()))
    elif cmd == 'get_bridged_iface_mac':
        if len(sys.argv) < 3:
            sys.stdout.write('IP address required')
            sys.exit(1)
        ip = sys.argv[2]
        sys.stdout.write(str(get_bridged_iface_mac(get_env(), ip)))
