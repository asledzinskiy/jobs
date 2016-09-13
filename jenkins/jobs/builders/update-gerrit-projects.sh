#!/bin/bash -xe

export PROJECT_CONFIG_PATH="${WORKSPACE}"

echo "This job updates Gerrit projects after the merge event."

jeepyb-manage-projects.sh
