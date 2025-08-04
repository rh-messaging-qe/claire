#!/usr/bin/env bash

set -e
BASE_IMAGE="$1"

echo "############################"
echo -e "[${BASE_IMAGE}]\nrunning install-pulp-repo.sh"
echo "############################"
echo ""
echo "BASE_IMAGE = ${BASE_IMAGE}"

echo "Installing Pulp repo for ${BASE_IMAGE}"
case ${BASE_IMAGE} in
    *ubi7:*)
        cp /tmp/pulp-repository-rhel7.repo /etc/yum.repos.d
        ;;
    *ubi8:*)
        cp /tmp/pulp-repository-rhel8.repo /etc/yum.repos.d
        ;;
    *ubi9:*)
        cp /tmp/pulp-repository-rhel9.repo /etc/yum.repos.d
        ;;
    *ubi10:*)
        cp /tmp/pulp-repository-rhel10.repo /etc/yum.repos.d
        ;;
    *)
        echo "pulp-repository not installed as base image name does not match one of expected images"
        ;;
esac;

echo ""
echo "install-pulp-repo.sh finished"
