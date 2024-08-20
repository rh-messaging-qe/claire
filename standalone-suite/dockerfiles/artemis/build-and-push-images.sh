#!/usr/bin/env bash

set -e

SKIP_PUSH="${1:-false}"
DEV_TAG="${2:-false}"

BASE_IMAGE_REGISTRY="registry.access.redhat.com"
BASE_IMAGES=(
  fedora
  ubi7
  ubi8
  ubi9
)

CLAIRE_STANDALONE_IMAGE_REGISTRY="quay.io/rhmessagingqe"
CLAIRE_STANDALONE_IMAGE_BASE_NAME="claire-standalone-artemis"

echo ""
echo "#######################################################################"
echo "Downloading additional Java JDK: openjdk-java-17"
wget -nv -c -O openjdk-java-17.tar.xz \
    https://download.eng.bos.redhat.com/released/OpenJDK/17.0.12/java-17-openjdk-17.0.12.0.7-1.portable.jdk.el.x86_64.tar.xz
tar -Jtf openjdk-java-17.tar.xz > /dev/null

echo "Downloading additional Java JDK: openjdk-java-21"
wget -nv -c -O openjdk-java-21.tar.xz \
    https://download.eng.bos.redhat.com/released/OpenJDK/21.0.4/java-21-openjdk-21.0.4.0.7-1.portable.jdk.x86_64.tar.xz
tar -Jtf openjdk-java-21.tar.xz > /dev/null

echo "Downloading additional Java JDK: oracle-java-11"
wget -nv -c -O oracle-java-11.tar.gz \
  https://repository.engineering.redhat.com/nexus/repository/rhm-qe-amq-clients-raw/jdk/jdk-11.0.15.1_linux-x64_bin.tar.gz
tar -ztf oracle-java-11.tar.gz > /dev/null

echo "Downloading additional Java JDK: oracle-java-17"
wget -nv -c -O oracle-java-17.tar.gz https://download.oracle.com/java/17/latest/jdk-17_linux-x64_bin.tar.gz
tar -ztf oracle-java-17.tar.gz > /dev/null

echo "Downloading additional Java JDK: oracle-java-21"
wget -nv -c -O oracle-java-21.tar.gz https://download.oracle.com/java/21/latest/jdk-21_linux-x64_bin.tar.gz
tar -ztf oracle-java-21.tar.gz > /dev/null

echo ""
for base_image_name in "${BASE_IMAGES[@]}"; do
  if [[ "${base_image_name}" =~ ubi[0-9] ]]; then
    base_image="${BASE_IMAGE_REGISTRY}/${base_image_name}:latest"
  else
    base_image="${base_image_name}:latest"
  fi
  claire_standalone_image_name="${CLAIRE_STANDALONE_IMAGE_BASE_NAME}:${base_image_name}"
  if [[ "${DEV_TAG}" == "true" ]]; then
    claire_standalone_image_name+="-dev"
  fi
  claire_standalone_registry_image="${CLAIRE_STANDALONE_IMAGE_REGISTRY}/${claire_standalone_image_name}"

  echo ""
  echo "#######################################################################"
  echo "Building image with:"
  echo "    base image = ${base_image}"
  echo "    image name = ${claire_standalone_image_name}"
  docker build --progress=plain --build-arg BASE_IMAGE="${base_image}" --tag "${claire_standalone_image_name}" \
    --file DockerfileArtemisRedHatBased .

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