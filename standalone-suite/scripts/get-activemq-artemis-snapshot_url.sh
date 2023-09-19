#!/usr/bin/env bash

set -e

CLAIRE_PROPERTIES_FILE="${1}"
STANDALONE_SNAPSHOT_BIN_ZIP_BASE_URL=$(yq '.standalone.snapshot_bin_zip_base_url.activemq-artemis' "${CLAIRE_PROPERTIES_FILE}")

STANDALONE_SNAPSHOT_BIN_ZIP_URL=$(wget -q -O - "${STANDALONE_SNAPSHOT_BIN_ZIP_BASE_URL}" | \
    grep -E -o "https://.*SNAPSHOT/\">" | \
    sed -E 's#(.*)/">#\1#' | \
    wget -q -i - -O - | \
    grep -E -o "https://.*-bin.zip\">" | \
    sed -E 's#(.*)">#\1#' | \
    sort | tail -1)

echo "${STANDALONE_SNAPSHOT_BIN_ZIP_URL}"

