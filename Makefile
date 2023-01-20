ROOT_DIR 				= $(shell dirname $(realpath $(lastword $(MAKEFILE_LIST))))
ARTEMIS_PROPERTIES_FILE 		= ${ROOT_DIR}/artemis/project-settings.properties

ARTEMIS_VERSION 			?= 7.10.2
OPERATOR_INSTALL_ZIP			?= https://download.eng.bos.redhat.com/released/jboss/amq/broker/${ARTEMIS_VERSION}/amq-broker-operator-${ARTEMIS_VERSION}-ocp-install-examples-rhel8.zip
OPERATOR_VERSION_UPSTREAM 		?= main

all: test_smoke_downstream

build_downstream: prepare_dirs downstream_files copy_ocp_zip_files build_java

build_upstream: prepare_dirs upstream_files build_java

test_smoke_downstream: prepare_dirs downstream_files copy_ocp_zip_files test_smoke

test_smoke_upstream: prepare_dirs upstream_files test_smoke

clean: clean_all

build_java:
	mvn clean install -DskipTests --no-transfer-progress

test_smoke:
	mvn test -Dtest=SmokeTests,ClusteredOperatorSmokeTests --no-transfer-progress

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
	wget ${OPERATOR_INSTALL_ZIP} -O ${ROOT_DIR}/artemis/ocp_install_examples.zip
	unzip -o ${ROOT_DIR}/artemis/ocp_install_examples.zip -d ${ROOT_DIR}/artemis/tmp/

copy_ocp_zip_files:
	# Copy CRDs, examples and install files; Execute as one shell command
	set -e ;\
	EXAMPLES_ZIP_DIR=$$(find ${ROOT_DIR} -iname "*ocp-install-examples" -type d ) ;\
	cp -r $${EXAMPLES_ZIP_DIR}/deploy/crds/* ${ROOT_DIR}/artemis/crds/ ;\
	cp $${EXAMPLES_ZIP_DIR}/deploy/examples/artemis-basic-deployment.yaml ${ROOT_DIR}/artemis/examples/artemis/ ;\
	cp $${EXAMPLES_ZIP_DIR}/deploy/examples/address-queue-create.yaml ${ROOT_DIR}/artemis/examples/address/ ;\
	cp -r $${EXAMPLES_ZIP_DIR}/deploy/*yaml ${ROOT_DIR}/artemis/install/
	rm -rf ${ROOT_DIR}/artemis/tmp ${ROOT_DIR}/artemis/ocp_install_examples.zip

upstream_files:
	# CRDs
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/resources/crd_artemis.yaml -P ${ROOT_DIR}/artemis/crds/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/resources/crd_artemis_security.yaml -P ${ROOT_DIR}/artemis/crds/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/resources/crd_artemis_address.yaml -P ${ROOT_DIR}/artemis/crds/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/resources/crd_artemis_scaledown.yaml -P ${ROOT_DIR}/artemis/crds/
	# Install files (currently present only at main branch
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/resources/service_account.yaml -P ${ROOT_DIR}/artemis/install/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/resources/cluster_role.yaml -P ${ROOT_DIR}/artemis/install/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/resources/namespace_role.yaml -P ${ROOT_DIR}/artemis/install/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/resources/cluster_role_binding.yaml -P ${ROOT_DIR}/artemis/install/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/resources/namespace_role_binding.yaml -P ${ROOT_DIR}/artemis/install/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/resources/election_role.yaml -P ${ROOT_DIR}/artemis/install/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/resources/election_role_binding.yaml -P ${ROOT_DIR}/artemis/install/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/resources/operator_config.yaml -P ${ROOT_DIR}/artemis/install/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/resources/operator.yaml -P ${ROOT_DIR}/artemis/install/
	# Examples
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/examples/artemis/artemis_single.yaml -P ${ROOT_DIR}/artemis/examples/artemis/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/examples/artemis/artemis_address_settings.yaml -P ${ROOT_DIR}/artemis/examples/artemis/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/examples/artemis/artemis_cluster_persistence.yaml -P ${ROOT_DIR}/artemis/examples/artemis/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/examples/artemis/artemis_resources.yaml -P ${ROOT_DIR}/artemis/examples/artemis/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/examples/address/address_queue.yaml -P ${ROOT_DIR}/artemis/examples/address/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/examples/address/address_topic.yaml -P ${ROOT_DIR}/artemis/examples/address/


.PHONY: build clean
