ROOT_DIR 				= $(shell dirname $(realpath $(lastword $(MAKEFILE_LIST))))
ARTEMIS_VERSION 			?= 7.10.1
OPERATOR_VERSION_UPSTREAM 		?= main
ARTEMIS_PROPERTIES_FILE 		= ${ROOT_DIR}/artemis/project-settings.properties
CLUSTER_OPERATOR_MANAGED		?= true

all: test_smoke_downstream

build_downstream: prepare_dirs fill_project_properties downstream_files build_java

build_upstream: prepare_dirs fill_project_properties upstream_files build_java

test_smoke_downstream: prepare_dirs fill_project_properties downstream_files test_smoke

test_smoke_upstream: prepare_dirs fill_project_properties upstream_files test_smoke


clean: clean_all

build_java:
	mvn clean install -DskipTests --no-transfer-progress

test_smoke:
	mvn test -Dtest=SmokeTests,ClusteredOperatorSmokeTests

clean_all:
	mvn clean
	rm -rf ${ROOT_DIR}/artemis

prepare_dirs:
	rm -rf ${ROOT_DIR}/artemis
	mkdir -p ${ROOT_DIR}/artemis/{crds,examples,install}
	mkdir -p ${ROOT_DIR}/artemis/examples/{artemis,address}

fill_project_properties:
	echo "project.cluster_operator.manage=${CLUSTER_OPERATOR_MANAGED}" > ${ARTEMIS_PROPERTIES_FILE}

downstream_files:
	echo "project.type=amq-broker" >> ${ARTEMIS_PROPERTIES_FILE}
	# TODO use new structure of examples/install for downstream once 7.11 is out. Current is ugly
	# Download ocp-install-examples candidate files
	wget http://download.lab.bos.redhat.com/devel/candidates/amq/AMQ-BROKER-${ARTEMIS_VERSION}/amq-broker-operator-${ARTEMIS_VERSION}-ocp-install-examples-rhel8.zip -O ${ROOT_DIR}/artemis/ocp_install_examples.zip
	unzip -o ${ROOT_DIR}/artemis/ocp_install_examples.zip -d ${ROOT_DIR}/artemis/tmp/
	cp -r ${ROOT_DIR}/artemis/tmp/amq-broker-operator-${ARTEMIS_VERSION}-ocp-install-examples/deploy/crds/* ${ROOT_DIR}/artemis/crds/
	cp ${ROOT_DIR}/artemis/tmp/amq-broker-operator-${ARTEMIS_VERSION}-ocp-install-examples/deploy/examples/artemis-basic-deployment.yaml ${ROOT_DIR}/artemis/examples/artemis/
	cp ${ROOT_DIR}/artemis/tmp/amq-broker-operator-${ARTEMIS_VERSION}-ocp-install-examples/deploy/examples/address-queue-create.yaml ${ROOT_DIR}/artemis/examples/address/

	# Install files
	cp -r ${ROOT_DIR}/artemis/tmp/amq-broker-operator-${ARTEMIS_VERSION}-ocp-install-examples/deploy/*yaml ${ROOT_DIR}/artemis/install/
	rm -rf ${ROOT_DIR}/artemis/tmp ${ROOT_DIR}/artemis/ocp_install_examples.zip

upstream_files:
	echo "project.type=activemq-artemis" >> ${ARTEMIS_PROPERTIES_FILE}
	# CRDs
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/install/010_crd_artemis.yaml -P ${ROOT_DIR}/artemis/crds/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/install/020_crd_artemis_security.yaml -P ${ROOT_DIR}/artemis/crds/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/install/030_crd_artemis_address.yaml -P ${ROOT_DIR}/artemis/crds/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/install/040_crd_artemis_scaledown.yaml -P ${ROOT_DIR}/artemis/crds/
	# Install files (currently present only at main branch
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/install/050_service_account.yaml -P ${ROOT_DIR}/artemis/install/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/install/060_cluster_role.yaml -P ${ROOT_DIR}/artemis/install/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/install/060_namespace_role.yaml -P ${ROOT_DIR}/artemis/install/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/install/070_cluster_role_binding.yaml -P ${ROOT_DIR}/artemis/install/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/install/070_namespace_role_binding.yaml -P ${ROOT_DIR}/artemis/install/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/install/080_election_role.yaml -P ${ROOT_DIR}/artemis/install/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/install/090_election_role_binding.yaml -P ${ROOT_DIR}/artemis/install/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/install/100_operator_config.yaml -P ${ROOT_DIR}/artemis/install/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/install/110_operator.yaml -P ${ROOT_DIR}/artemis/install/
	# Examples
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/examples/artemis/artemis_single.yaml -P ${ROOT_DIR}/artemis/examples/artemis/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/examples/artemis/artemis_address_settings.yaml -P ${ROOT_DIR}/artemis/examples/artemis/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/examples/artemis/artemis_cluster_persistence.yaml -P ${ROOT_DIR}/artemis/examples/artemis/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/examples/artemis/artemis_resources.yaml -P ${ROOT_DIR}/artemis/examples/artemis/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/examples/address/address_queue.yaml -P ${ROOT_DIR}/artemis/examples/address/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/examples/address/address_topic.yaml -P ${ROOT_DIR}/artemis/examples/address/


.PHONY: build clean
