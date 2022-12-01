ROOT_DIR = $(shell dirname $(realpath $(lastword $(MAKEFILE_LIST))))
ARTEMIS_VERSION 			= 7.10.1
OPERATOR_VERSION_UPSTREAM 	= v0.20.1

all: prepare_dirs downstream_files build_java

build_downstream: prepare_dirs downstream_files build_java

build_upstream: prepare_dirs upstream_files build_java

clean: clean_all

build_java:
	mvn clean install -DskipTests --no-transfer-progress

clean_all:
	mvn clean
	rm -rf ${ROOT_DIR}/artemis

prepare_dirs:
	rm -rf ${ROOT_DIR}/artemis
	mkdir -p ${ROOT_DIR}/artemis/{crds,examples,install}
	mkdir -p ${ROOT_DIR}/artemis/examples/{artemis,address}

downstream_files:
	# TODO use new structure of examples/install for downstream once 7.11 is out. Current is ugly
	# Download ocp-install-examples candidate files
	wget http://download.lab.bos.redhat.com/devel/candidates/amq/AMQ-BROKER-${ARTEMIS_VERSION}/amq-broker-operator-${ARTEMIS_VERSION}-ocp-install-examples-rhel8.zip -O ${ROOT_DIR}/artemis/ocp_install_examples.zip
	unzip -o ${ROOT_DIR}/artemis/ocp_install_examples.zip -d ${ROOT_DIR}/artemis/tmp/
	cp -r ${ROOT_DIR}/artemis/tmp/amq-broker-operator-${ARTEMIS_VERSION}-ocp-install-examples/deploy/crds ${ROOT_DIR}/artemis/crds
	cp ${ROOT_DIR}/artemis/tmp/amq-broker-operator-${ARTEMIS_VERSION}-ocp-install-examples/deploy/examples/artemis-basic-deployment.yaml ${ROOT_DIR}/artemis/examples/artemis/
	cp ${ROOT_DIR}/artemis/tmp/amq-broker-operator-${ARTEMIS_VERSION}-ocp-install-examples/deploy/examples/address-queue-create.yaml ${ROOT_DIR}/artemis/examples/address/

	# Install files
	#rm -rf ${ROOT_DIR}/artemis/tmp

upstream_files:
	#CRDs
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/crds/broker_activemqartemis_crd.yaml -P ${ROOT_DIR}/artemis/crds/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/crds/broker_activemqartemisaddress_crd.yaml -P ${ROOT_DIR}/artemis/crds/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/crds/broker_activemqartemisscaledown_crd.yaml -P ${ROOT_DIR}/artemis/crds/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/crds/broker_activemqartemissecurity_crd.yaml -P ${ROOT_DIR}/artemis/crds/
	# Examples
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/examples/artemis-basic-deployment.yaml -P ${ROOT_DIR}/artemis/examples/artemis/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/examples/address-queue-create.yaml -P ${ROOT_DIR}/artemis/examples/address/
	# CO Install files
	# TODO

.PHONY: build clean
