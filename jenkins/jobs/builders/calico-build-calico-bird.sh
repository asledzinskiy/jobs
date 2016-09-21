#!/bin/sh

set -e
set -x

BUILD=$(git rev-parse --short HEAD)

rm -rf ./artifacts/

/bin/sh -x build.sh

mkdir -p ./artifacts/

# Prepare artifacts
cp ./dist/bird ./artifacts/bird-"${BUILD}"
cp ./dist/bird6 ./artifacts/bird6-"${BUILD}"
cp ./dist/birdcl ./artifacts/birdcl-"${BUILD}"
rm -rf ./dist
echo "${BUILD}" > ./artifacts/lastbuild
echo "GIT_SHA=${BUILD}" > build.property
