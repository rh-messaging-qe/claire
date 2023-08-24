#!/usr/bin/env bash

set -e

SKIP_PUSH="${1:-false}"

BASE_IMAGES=(
  fedora
)

CLAIRE_STANDALONE_IMAGE_REGISTRY="quay.io/rhmessagingqe"
CLAIRE_STANDALONE_IMAGE_BASE_NAME="claire-nfs-server"

echo ""
for base_image_name in "${BASE_IMAGES[@]}"; do
  base_image="${base_image_name}:latest"
  claire_standalone_image_name="${CLAIRE_STANDALONE_IMAGE_BASE_NAME}:${base_image_name}"
  claire_standalone_registry_image="${CLAIRE_STANDALONE_IMAGE_REGISTRY}/${claire_standalone_image_name}"

  echo ""
  echo "#######################################################################"
  echo "Building image with:"
  echo "    base image = ${base_image}"
  echo "    image name = ${claire_standalone_image_name}"
  docker build --progress=plain --build-arg BASE_IMAGE="${base_image}" --tag "${claire_standalone_image_name}" \
    --file Dockerfile .

  echo ""
  echo "Tagging local image ${claire_standalone_image_name} to ${claire_standalone_registry_image}"
  docker tag "${claire_standalone_image_name}" "${CLAIRE_STANDALONE_IMAGE_REGISTRY}/${claire_standalone_image_name}"

  if [[ "${SKIP_PUSH}" == "false" ]]; then
    echo ""
    echo "Pushing local image ${claire_standalone_image_name} to ${claire_standalone_registry_image}"
    docker push "${claire_standalone_registry_image}"
  fi

  echo ""
done