#!/usr/bin/env bash

set -e
BASE_IMAGE="$1"

COMMON_PACKAGES=(libaio nodejs nfs-utils sudo xz libicu harfbuzz-icu libjpeg-turbo libwebp enchant2 libsecret)
FEDORA_PACKAGES=("${COMMON_PACKAGES[@]}" procps-ng)
UBI7_PACKAGES=("${COMMON_PACKAGES[@]}" xorg-x11-server-Xvfb sysvinit-tools)
UBI8_PACKAGES=("${FEDORA_PACKAGES[@]}" xorg-x11-server-Xvfb)
UBI9_PACKAGES=("${FEDORA_PACKAGES[@]}" xorg-x11-server-Xvfb)
UBI10_PACKAGES=("${COMMON_PACKAGES[@]}" procps-ng)

echo "############################"
echo -e "[${BASE_IMAGE}]\nrunning install-packages.sh"
echo "############################"
echo ""
echo "BASE_IMAGE = ${BASE_IMAGE}"

echo "Installing packages for ${BASE_IMAGE}"
case ${BASE_IMAGE} in
    *fedora:*)
        dnf install --assumeyes "${FEDORA_PACKAGES[@]}"
        ;;
    *ubi7:*)
        curl -sL rpm.nodesource.com/setup_4.x | bash -
        yum install --assumeyes "${UBI7_PACKAGES[@]}"
        ;;
    *ubi8:*)
        dnf install --assumeyes "${UBI8_PACKAGES[@]}"
        ;;
    *ubi9:*)
        dnf install --assumeyes "${UBI9_PACKAGES[@]}"
        ;;
    *ubi10:*)
        dnf install --assumeyes "${UBI10_PACKAGES[@]}"
        ;;
    *)
        echo "packages were not installed as base image name does not match one of expected images"
        ;;
esac;

echo ""
echo "install-packages.sh finished"
