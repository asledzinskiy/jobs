#!/bin/bash

set -ex

if [ -n "${IMAGE_LINK}" ]; then
    IMAGE_FILE="${IMAGE_LINK/*\//}"
    IMAGES_DIR="$(dirname "${IMAGE_PATH}")"
    mkdir -p "${IMAGES_DIR}"
    pushd "${IMAGES_DIR}"

    wget -N "${IMAGE_LINK}"

    if file "${IMAGE_FILE}" | grep -q 'ASCII text'; then
        declare -a IMAGES_LIST
        IMAGES_LIST=($(sed -r 's/\S*=//; s/,/ /g' "${IMAGE_FILE}"))
        echo "Trying to download one of latest images: ${IMAGES_LIST[@]}"
        LATEST_IMAGE_FILE=${IMAGES_LIST[0]}

        if [ -z "${LATEST_IMAGE_FILE}" ]; then
            echo "ERROR! Latest image not found!"
        fi

        IMAGE_FILE="${LATEST_IMAGE_FILE}"
        LATEST_IMAGE_LINK="${IMAGE_LINK%/*}/${LATEST_IMAGE_FILE}"

        wget -N "${LATEST_IMAGE_LINK}"
    fi

    ln -sf "${IMAGES_DIR}/${IMAGE_FILE}" "${IMAGE_PATH}"

    popd
fi

if [ ! -r "${IMAGE_PATH}" ]; then
    echo "Image not found: ${IMAGE_PATH}"
    exit 1
fi