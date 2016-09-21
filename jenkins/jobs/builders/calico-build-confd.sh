#!/bin/sh
set -e
set -x

rm -rf ./artifacts
mkdir -p ./artifacts

container_src_dir=/usr/src/confd
src_suffix=src/github.com/kelseyhightower/confd
container_workdir=${container_src_dir}/${src_suffix}
container_gopath=${container_src_dir}/vendor:${container_src_dir}

docker run --rm \
    -v `pwd`:${container_src_dir} \
    -w ${container_workdir} \
    -e GOPATH=${container_gopath} \
    golang:1.7 \
    bash -c \
    "go build -a -installsuffix cgo -ldflags '-extld ld -extldflags -static' -a -x ."

BUILD=$(git rev-parse --short HEAD)
cp "./${src_suffix}/confd" "./artifacts/confd-${BUILD}"
echo "${BUILD}" > ./artifacts/lastbuild
echo "GIT_SHA=${BUILD}" > build.property
