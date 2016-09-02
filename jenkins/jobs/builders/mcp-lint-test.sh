#!/bin/bash

set -ex

# exclude rules which are not required
export RULES_EXCLUDE=ANSIBLE0010,ANSIBLE0012,E511
# let's add more rules
export RULES_PROJECT=https://github.com/tsukinowasha/ansible-lint-rules

if git diff HEAD~1 --name-only --diff-filter=AM | grep "^ansible/.*yml$"; then
    tox -e ansible-lint
else
    echo "No playbooks were changed skipping ansible-lint"
fi