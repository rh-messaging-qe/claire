ROOT_DIR 				= $(shell dirname $(realpath $(lastword $(MAKEFILE_LIST))))
ARTEMIS_PROPERTIES_FILE 		= ${ROOT_DIR}/artemis/project-settings.properties

ARTEMIS_VERSION 			?= 7.10.2
LATEST_ARTEMIS_VERSION			= 7.11
OPERATOR_INSTALL_ZIP			?= https://download.eng.bos.redhat.com/released/jboss/amq/broker/${ARTEMIS_VERSION}/amq-broker-operator-${ARTEMIS_VERSION}-ocp-install-examples-rhel8.zip
OPERATOR_VERSION_UPSTREAM 		?= main

all: test_smoke_downstream

build_downstream: clean_all prepare_dirs downstream_files copy_ocp_zip_files copy_latest_crd_files build_java

build_upstream: clean_all prepare_dirs upstream_files build_java

test_smoke_downstream: clean_all prepare_dirs downstream_files copy_ocp_zip_files copy_latest_crd_files test_smoke

test_smoke_upstream: clean_all prepare_dirs upstream_files test_smoke

clean: clean_all

build_java:
	mvn clean install -DskipTests --no-transfer-progress

test_smoke:
	mvn test -Dtest=SmokeTests,ClusteredOperatorSmokeTests --no-transfer-progress

clean_all:
	mvn clean
	rm -rf ${ROOT_DIR}/artemis

prepare_dirs:
	mkdir -p ${ROOT_DIR}/artemis/{crds,examples,install}
	mkdir -p ${ROOT_DIR}/artemis/examples/{artemis,address}

downstream_files:
	# TODO use new structure of examples/install for downstream once 7.11 is out. Current is ugly
	# Download ocp-install-examples candidate files
	wget ${OPERATOR_INSTALL_ZIP} -O ${ROOT_DIR}/artemis/ocp_install_examples.zip
	unzip -o ${ROOT_DIR}/artemis/ocp_install_examples.zip -d ${ROOT_DIR}/artemis/tmp/
	echo "artemis.zip=${OPERATOR_INSTALL_ZIP}" >> ${ARTEMIS_PROPERTIES_FILE}
	echo "artemis.name=amq-broker" >> ${ARTEMIS_PROPERTIES_FILE}

copy_ocp_zip_files:
	# Copy CRDs, examples and install files
	$(eval EXAMPLES_ZIP_DIR := $(shell find ${ROOT_DIR} -iname "*ocp-install-examples" -type d ))
	cp ${EXAMPLES_ZIP_DIR}/deploy/examples/artemis/*.yaml ${ROOT_DIR}/artemis/examples/artemis/ || cp ${EXAMPLES_ZIP_DIR}/deploy/examples/artemis-basic-deployment.yaml ${ROOT_DIR}/artemis/examples/artemis/
	cp ${EXAMPLES_ZIP_DIR}/deploy/examples/address/*.yaml ${ROOT_DIR}/artemis/examples/address/ || cp ${EXAMPLES_ZIP_DIR}/deploy/examples/address-queue-create.yaml ${ROOT_DIR}/artemis/examples/address/
	cp -r ${EXAMPLES_ZIP_DIR}/deploy/*yaml ${ROOT_DIR}/artemis/install/

copy_latest_crd_files:
	echo "artemis.type=downstream" >> ${ARTEMIS_PROPERTIES_FILE}
	$(eval EXAMPLES_ZIP_DIR := $(shell find ${ROOT_DIR} -iname "*ocp-install-examples" -type d ))
	@if [[ ${OPERATOR_INSTALL_ZIP} =~ ${LATEST_ARTEMIS_VERSION} ]]; then \
		echo "[CRD] Using zip provided crds from ${LATEST_ARTEMIS_VERSION}" ;\
		cp -r ${EXAMPLES_ZIP_DIR}/deploy/crds/* ${ROOT_DIR}/artemis/crds/ ;\
		echo "artemis.version=${LATEST_ARTEMIS_VERSION}" >> ${ARTEMIS_PROPERTIES_FILE} ;\
		echo "artemis.test.version=${LATEST_ARTEMIS_VERSION}" >> ${ARTEMIS_PROPERTIES_FILE} ;\
		echo "artemis.crds=provided_zip" >> ${ARTEMIS_PROPERTIES_FILE} ;\
	else \
		echo "[CRD] Using latest upstream crds" ;\
		wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/crds/broker_activemqartemis_crd.yaml -P ${ROOT_DIR}/artemis/crds/ ;\
		wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/crds/broker_activemqartemissecurity_crd.yaml -P ${ROOT_DIR}/artemis/crds/ ;\
		wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/crds/broker_activemqartemisaddress_crd.yaml -P ${ROOT_DIR}/artemis/crds/ ;\
		wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/crds/broker_activemqartemisscaledown_crd.yaml -P ${ROOT_DIR}/artemis/crds/ ;\
		echo "artemis.version=${ARTEMIS_VERSION}" >> ${ARTEMIS_PROPERTIES_FILE} ;\
		echo "artemis.test.version=${ARTEMIS_VERSION}" >> ${ARTEMIS_PROPERTIES_FILE} ;\
		echo "artemis.crds=upstream" >> ${ARTEMIS_PROPERTIES_FILE} ;\
	fi ;
	# Clean tmp folder
	rm -rf ${ROOT_DIR}/artemis/tmp ${ROOT_DIR}/artemis/ocp_install_examples.zip


upstream_files:
	# CRDs
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/crds/broker_activemqartemis_crd.yaml -P ${ROOT_DIR}/artemis/crds/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/crds/broker_activemqartemissecurity_crd.yaml -P ${ROOT_DIR}/artemis/crds/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/crds/broker_activemqartemisaddress_crd.yaml -P ${ROOT_DIR}/artemis/crds/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/crds/broker_activemqartemisscaledown_crd.yaml -P ${ROOT_DIR}/artemis/crds/
	# Install files (currently present only at main branch
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/service_account.yaml -P ${ROOT_DIR}/artemis/install/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/cluster_role.yaml -P ${ROOT_DIR}/artemis/install/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/role.yaml -P ${ROOT_DIR}/artemis/install/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/cluster_role_binding.yaml -P ${ROOT_DIR}/artemis/install/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/role_binding.yaml -P ${ROOT_DIR}/artemis/install/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/election_role.yaml -P ${ROOT_DIR}/artemis/install/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/election_role_binding.yaml -P ${ROOT_DIR}/artemis/install/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/operator_config.yaml -P ${ROOT_DIR}/artemis/install/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/deploy/operator.yaml -P ${ROOT_DIR}/artemis/install/
	# Examples
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/examples/artemis/artemis_single.yaml -P ${ROOT_DIR}/artemis/examples/artemis/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/examples/artemis/artemis_address_settings.yaml -P ${ROOT_DIR}/artemis/examples/artemis/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/examples/artemis/artemis_cluster_persistence.yaml -P ${ROOT_DIR}/artemis/examples/artemis/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/examples/artemis/artemis_resources.yaml -P ${ROOT_DIR}/artemis/examples/artemis/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/examples/address/address_queue.yaml -P ${ROOT_DIR}/artemis/examples/address/
	wget https://raw.githubusercontent.com/artemiscloud/activemq-artemis-operator/${OPERATOR_VERSION_UPSTREAM}/examples/address/address_topic.yaml -P ${ROOT_DIR}/artemis/examples/address/
	echo "artemis.type=upstream" >> ${ARTEMIS_PROPERTIES_FILE}
	echo "artemis.version=${OPERATOR_VERSION_UPSTREAM}" >> ${ARTEMIS_PROPERTIES_FILE}
	echo "artemis.test.version=${OPERATOR_VERSION_UPSTREAM}" >> ${ARTEMIS_PROPERTIES_FILE}
	echo "artemis.crds=upstream" >> ${ARTEMIS_PROPERTIES_FILE}
	echo "artemis.name=activemq-artemis" >> ${ARTEMIS_PROPERTIES_FILE}

.PHONY: build clean
