#!/usr/bin/env bash

set -e
BASE_IMAGE="$1"

echo "###############################"
echo "running install-java-version.sh"
echo "###############################"
echo ""
echo "BASE_IMAGE = ${BASE_IMAGE}"

echo ""
echo "##########################"
echo "installing openjdk java 11"
echo "##########################"
yum --assumeyes install \
  java-11-openjdk-headless
ln -s /usr/lib/jvm/jre-11-openjdk /opt/openjdk-java-11
echo "openjdk java 11 installed"

echo ""
echo "#########################"
echo "installing oracle java 11"
echo "#########################"
mkdir -p /opt/oracle-java-11
tar -zxf /tmp/oracle-java-11.tar.gz -C /opt/oracle-java-11 --strip-components=1
echo "oracle java 11 installed"

echo ""
echo "##########################"
echo "installing openjdk java 17"
echo "##########################"
if [[ "${BASE_IMAGE}" =~ .*"ubi7":.* ]]; then
  mkdir -p /opt/openjdk-java-17
  tar -Jxf /tmp/openjdk-java-17.tar.xz -C /opt/openjdk-java-17 --strip-components=1
else
  yum --assumeyes install \
    java-17-openjdk-headless
  ln -s /usr/lib/jvm/jre-17-openjdk /opt/openjdk-java-17
fi
echo "openjdk java 17 installed"

echo ""
echo "#########################"
echo "installing oracle java 17"
echo "#########################"
mkdir -p /opt/oracle-java-17
tar -zxf /tmp/oracle-java-17.tar.gz -C /opt/oracle-java-17 --strip-components=1
echo "oracle java 17 installed"

echo ""
echo "##########################"
echo "installing openjdk java 21"
echo "##########################"
if [[ "${BASE_IMAGE}" =~ .*"ubi7":.* ]]; then
  mkdir -p /opt/openjdk-java-21
  tar -Jxf /tmp/openjdk-java-21.tar.xz -C /opt/openjdk-java-21 --strip-components=1
else
  yum --assumeyes install \
    java-21-openjdk-headless
  ln -s /usr/lib/jvm/jre-21-openjdk /opt/openjdk-java-21
fi
echo "openjdk java 21 installed"

echo ""
echo "#########################"
echo "installing oracle java 21"
echo "#########################"
mkdir -p /opt/oracle-java-21
tar -zxf /tmp/oracle-java-21.tar.gz -C /opt/oracle-java-21 --strip-components=1
echo "oracle java 21 installed"

yum --assumeyes clean all
echo ""
echo "install-java-version.sh finished"