#!/usr/bin/env python

"""
Those tests checks the following requirements for the `projects.yaml` file:
    - Its syntax is valid
    - Each project definition should consist of the following mandatory parts:
        * project
        * description
      and could contain the following optional parts:
        * acl-config
        * upstream
      No other parts are possible.
    - All the projects listed in the `projects.yaml` file
      must be sorted alphabetically.
"""

import logging
import os
import sys

import jsonschema
import yaml


# Only lower case letters (a-z), digits (0-9), plus (+) and minus (-)
# and periods (.).
# They must be at least two characters long and must start with an
# alphanumeric character.
# https://www.debian.org/doc/debian-policy/ch-controlfields.html#s-f-Source
_PREFIX_PATTERN = '\A([a-z]([a-z]|\d|-)+/)*'
_DEBIAN_NAMES_PATTERN = '([a-z]|\d)([a-z]|\d|[+-.])+\Z'
PROJECT_NAME_PATTERN = _PREFIX_PATTERN + _DEBIAN_NAMES_PATTERN

PROJECT_SCHEMA = {
    "$schema": "http://json-schema.org/draft-04/schema#",
    "type": "array",
    "items": {
        "type": "object",
        "additionalProperties": False,
        "properties": {
            "project": {
                "type": "string",
                "pattern": PROJECT_NAME_PATTERN
            },
            "description": {
                "type": "string"
            },
            "upstream": {
                "type": "string"
            },

            "acl-config": {
                "type": "string"
            }
        },
        "required": ["project", "description"]
    }
}


def parse_yaml_file(file_path):
    try:
        data = yaml.safe_load(open(file_path))
        if data is None:
            logging.error("File {0} is empty".format(file_path))
            sys.exit(1)
        return data
    except yaml.YAMLError as exc:
        msg = "File {0} could not be parsed: {1}".format(file_path, exc)
        logging.error(msg)
        sys.exit(1)


def validate_data_by_schema(data, file_path):
    try:
        jsonschema.validate(data, PROJECT_SCHEMA)
    except jsonschema.exceptions.ValidationError as exc:
        raise ValueError(_make_error_message(exc, file_path))


def _make_error_message(exc, file_path):
    value_path = []

    if exc.absolute_path:
        value_path.extend(exc.absolute_path)

    error_msg = "File '{0}', {1}".format(file_path, exc.message)

    if value_path:
        value_path = ' -> '.join(map(str, value_path))
        error_msg = '{0}, value path {1}'.format(error_msg, value_path)

    return error_msg


def check_duplicate_projects(data):
    projects_items = []
    for item in data:
        if item['project'] not in projects_items:
            projects_items.append(item['project'])
        else:
            msg = "Project '{0}' is duplicated".format(item['project'])
            raise ValueError(msg)


def check_alphabetical_order(data):
    for i in range(len(data) - 1):
        if not data[i]['project'] < data[i + 1]['project']:
            msg = ("Alphabetical order violation: project '{0}' must be "
                   "placed after '{1}'".format(data[i]['project'],
                                               data[i + 1]['project']))
            raise ValueError(msg)


def check_acls_config_path(data):
    valid = True

    for item in data:
        acl_config_path = item.get('acl-config')
        if not acl_config_path:
            continue
        config_path = os.path.join(os.path.abspath(os.curdir),
                                   'gerrit',
                                   acl_config_path)
        if not os.path.isfile(config_path):
            logging.error("Config file for project '{0}' is not found "
                          "at {1}.".format(item.get('project'), config_path))
            valid = False
    if not valid:
        sys.exit(1)


def run_checks(file_to_check):
    data = parse_yaml_file(file_to_check)
    validate_data_by_schema(data, file_to_check)
    check_duplicate_projects(data)
    check_alphabetical_order(data)
    check_acls_config_path(data)


if __name__ == '__main__':
    if len(sys.argv) < 2:
        sys.stderr.write("Usage: {0} path/to/projects.yaml"
                         "\n".format(sys.argv[0]))
        sys.exit(1)
    run_checks(sys.argv[1])
