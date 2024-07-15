#
# Copyright Broker QE authors.
# License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
#
### Main project directories and files variables
SHELL                                         := /usr/bin/env bash
.SHELLFLAGS                                   := -o pipefail -e -c
ROOT_DIR                                       = $(shell dirname $(realpath $(lastword $(MAKEFILE_LIST))))
TMP_DIR                                        = ${ROOT_DIR}/tmp
OPERATOR_CRD_DIR                               = ${ROOT_DIR}/operator-crd
OPERATOR_SUITE_DIR                             = ${ROOT_DIR}/operator-suite
STANDALONE_SUITE_DIR                           = ${ROOT_DIR}/standalone-suite
VERSION_PROPERTIES_FILE                       ?= ${ROOT_DIR}/claire_properties.yaml
BUILD_PROPERTIES                               = ${ROOT_DIR}/build_properties.yaml

### Commands variables
MVN_DEFAULT_CMD                                = mvn --no-transfer-progress --update-snapshots -Dmaven.repo.local=./.m2/repository
MVN_TEST_ADDITIONAL_ARGS                      ?= 
MVN_TEST_CMD                                   = ${MVN_DEFAULT_CMD} ${MVN_TEST_ADDITIONAL_ARGS} failsafe:integration-test
WGET_CMD                                       = wget -nv -c

### Standalone variables
STANDALONE_TYPE                               ?= activemq-artemis
STANDALONE_VERSION                            ?= $(shell yq '.standalone.versions.latest."${STANDALONE_TYPE}"' ${VERSION_PROPERTIES_FILE})
STANDALONE_TYPE_FINAL                         ?= $(shell yq '.standalone.versions."${STANDALONE_VERSION}".type' ${VERSION_PROPERTIES_FILE})
STANDALONE_BIN_ZIP_URL                        ?= $(shell VERSION_PLACEHOLDER=${STANDALONE_VERSION} yq '.standalone.bin_zip_url."${STANDALONE_TYPE_FINAL}" | envsubst' ${VERSION_PROPERTIES_FILE})
STANDALONE_ARTEMIS_DIR                         = ${STANDALONE_SUITE_DIR}/artemis
STANDALONE_ARTEMIS_INSTALL_DIR                 = ${STANDALONE_ARTEMIS_DIR}/artemis_install
STANDALONE_ARTEMIS_DEFAULT_CFG_DIR             = ${STANDALONE_ARTEMIS_DIR}/artemis_default_cfg
STANDALONE_INSTANCE_NAME                       = artemis-instance
STANDALONE_USER                                = admin
STANDALONE_PASSWORD                            = admin
STANDALONE_CONTAINER_ARTEMIS_INSTANCE_DIR      = /var/lib/artemis-instance
STANDALONE_CONTAINER_ARTEMIS_INSTANCE_ETC_DIR  = ${STANDALONE_CONTAINER_ARTEMIS_INSTANCE_DIR}/etc

### Operator variables
OPERATOR_TYPE                                 ?= activemq-artemis
OPERATOR_VERSION                              ?= $(shell yq '.operator.versions.latest."${OPERATOR_TYPE}"' ${VERSION_PROPERTIES_FILE})
OPERATOR_VERSION_UPSTREAM                     ?= $(shell yq '.operator.versions."${OPERATOR_VERSION}".upstream_version' ${VERSION_PROPERTIES_FILE})
OPERATOR_MMM_VERSION                           = $(shell echo "${OPERATOR_VERSION}" | sed -E 's/([0-9]+\.[0-9]+\.[0-9]+).*/\1/')
OPERATOR_MM_VERSION                            = $(shell echo "${OPERATOR_VERSION}" | sed -E 's/([0-9]+\.[0-9]+).*/\1/')
OPERATOR_TYPE_FINAL                            = $(shell yq '.operator.versions."${OPERATOR_VERSION}".type' ${VERSION_PROPERTIES_FILE})
OPERATOR_IMAGE                                ?= $(shell yq '.operator.versions."${OPERATOR_VERSION}".operator_image' ${VERSION_PROPERTIES_FILE})
OPERATOR_BROKER_IMAGE                         ?= $(shell yq '.operator.versions."${OPERATOR_VERSION}".broker_image' ${VERSION_PROPERTIES_FILE})
OPERATOR_BROKER_INIT_IMAGE                    ?= $(shell yq '.operator.versions."${OPERATOR_VERSION}".broker_init_image' ${VERSION_PROPERTIES_FILE})
OPERATOR_ARTEMIS_CONTAINER_VERSION            ?= $(shell yq '.operator.versions."${OPERATOR_VERSION}".artemis_container_version' ${VERSION_PROPERTIES_FILE})
OPERATOR_ARTEMIS_CONTAINER_VERSION_UPSTREAM   ?= $(shell yq '.operator.versions."${OPERATOR_VERSION}".artemis_container_version_upstream' ${VERSION_PROPERTIES_FILE})
OPERATOR_ARTEMIS_DIR                           = ${OPERATOR_SUITE_DIR}/artemis

### Operator CRD variables
OPERATOR_CRD_ARTEMIS_DIR                       = ${OPERATOR_CRD_DIR}/artemis
OPERATOR_CRD_LATEST_OPERATOR_VERSION          ?= $(shell yq '.operator.versions.latest."${OPERATOR_TYPE_FINAL}"' ${VERSION_PROPERTIES_FILE})
OPERATOR_CRD_SRC_DIR                           = ${TMP_DIR}/operator-src
ifeq ($(OPERATOR_TYPE_FINAL),amq-broker)
OPERATOR_SRC_URL_VERSION_PLACEHOLDER1           = $(shell yq '.operator.versions."${OPERATOR_VERSION}".src_url_version_placeholder' ${VERSION_PROPERTIES_FILE})
OPERATOR_SRC_URL_VERSION_PLACEHOLDER2           = ${OPERATOR_MMM_VERSION}
else
OPERATOR_SRC_URL_VERSION_PLACEHOLDER1           = ${OPERATOR_MMM_VERSION}
OPERATOR_SRC_URL_VERSION_PLACEHOLDER1           = ${OPERATOR_MMM_VERSION}
endif
ifeq ($(OPERATOR_VERSION),snapshot)
OPERATOR_CRD_OPERATOR_SRC_URL                 ?= $(shell VERSION_PLACEHOLDER1=${OPERATOR_SRC_URL_VERSION_PLACEHOLDER1} VERSION_PLACEHOLDER2=${OPERATOR_SRC_URL_VERSION_PLACEHOLDER2} yq '.operator.src_zip_url."${OPERATOR_TYPE_FINAL}-dev" | envsubst' ${VERSION_PROPERTIES_FILE})
else
OPERATOR_CRD_OPERATOR_SRC_URL                 ?= $(shell VERSION_PLACEHOLDER1=${OPERATOR_SRC_URL_VERSION_PLACEHOLDER1} VERSION_PLACEHOLDER2=${OPERATOR_SRC_URL_VERSION_PLACEHOLDER2} yq '.operator.src_zip_url."${OPERATOR_TYPE_FINAL}" | envsubst' ${VERSION_PROPERTIES_FILE})
endif

### Operator container variables
OPERATOR_CONTAINER_SKIP_BUILD                 ?= false
OPERATOR_CONTAINER_DEPLOY                     ?= false
OPERATOR_CONTAINER_GENERATE_LPT_TAG           ?= false
OPERATOR_CONTAINER_ARCH                       ?= amd64 arm64 ppc64le s390x
OPERATOR_CONTAINER_FULL_VERSION                = ${OPERATOR_TYPE_FINAL}-${OPERATOR_VERSION}
OPERATOR_CONTAINER_MMM_VERSION                 = ${OPERATOR_TYPE_FINAL}-${OPERATOR_MMM_VERSION}
OPERATOR_CONTAINER_MM_VERSION                  = ${OPERATOR_TYPE_FINAL}-${OPERATOR_MM_VERSION}
OPERATOR_CONTAINER_LPT_VERSION                 = ${OPERATOR_TYPE_FINAL}-lpt
OPERATOR_CONTAINER_REGISTRY                    = quay.io
OPERATOR_CONTAINER_NAMESPACE                  ?= rhmessagingqe
OPERATOR_CONTAINER_IMAGE_NAME                  = ${OPERATOR_CONTAINER_REGISTRY}/${OPERATOR_CONTAINER_NAMESPACE}/claire
OPERATOR_CONTAINER_IMAGE_NAME_FULL_VERSION     = ${OPERATOR_CONTAINER_IMAGE_NAME}:${OPERATOR_CONTAINER_FULL_VERSION}
OPERATOR_CONTAINER_IMAGE_NAME_MMM_VERSION      = ${OPERATOR_CONTAINER_IMAGE_NAME}:${OPERATOR_CONTAINER_MMM_VERSION}
OPERATOR_CONTAINER_IMAGE_NAME_MM_VERSION       = ${OPERATOR_CONTAINER_IMAGE_NAME}:${OPERATOR_CONTAINER_MM_VERSION}
OPERATOR_CONTAINER_IMAGE_NAME_LPT_VERSION      = ${OPERATOR_CONTAINER_IMAGE_NAME}:${OPERATOR_CONTAINER_LPT_VERSION}

### Tests related variables
ifdef TESTS
    $(eval MVN_TESTS_PARAM := -Dit.test="${TESTS}")
endif
ifdef TEST_GROUPS
    $(eval MVN_GROUPS_PARAM := -Dgroups="${TEST_GROUPS}")
endif

### project level targets
clean: clean_maven operator_crd_clean standalone_clean operator_clean
	@echo "####################################"
	@echo "###### Executing $@ target ######"
	@echo "####################################"
	rm -rf ${BUILD_PROPERTIES}
	@echo ""
	@echo ""

clean_maven:
	@echo "##########################################"
	@echo "###### Executing $@ target ######"
	@echo "##########################################"
	${MVN_DEFAULT_CMD} versions:revert clean
	@echo ""
	@echo ""

clean_all: clean
	@echo "########################################"
	@echo "###### Executing $@ target ######"
	@echo "########################################"
	rm -rf ${TMP_DIR} ${ROOT_DIR}/performance ${ROOT_DIR}/test-results ${ROOT_DIR}/container_build_*.log
	rm -rf ${STANDALONE_SUITE_DIR}/test-logs rm -rf ${STANDALONE_SUITE_DIR}/test_cfg ${STANDALONE_SUITE_DIR}/https_console_tune.yaml ${STANDALONE_SUITE_DIR}/test-tmp
	rm -rf ${OPERATOR_SUITE_DIR}/test-logs ${OPERATOR_SUITE_DIR}/certificates ${OPERATOR_SUITE_DIR}/test-tmp
	@echo ""
	@echo ""

build: standalone_prepare operator_prepare operator_crd_prepare build_maven generate_current_build_info print_build_info

build_maven:
	@echo "##########################################"
	@echo "###### Executing $@ target ######"
	@echo "##########################################"
	${MVN_DEFAULT_CMD} -DskipTests install
	@echo ""
	@echo ""

prepare_root_dirs:
	@echo "################################################"
	@echo "###### Executing $@ target ######"
	@echo "################################################"
	mkdir -p ${TMP_DIR}
	@echo ""
	@echo ""

generate_current_build_info:
	@echo "##########################################################"
	@echo "###### Executing $@ target ######"
	@echo "##########################################################"
	echo "" > ${BUILD_PROPERTIES}
	yq -i '(.version_properties_file = "${VERSION_PROPERTIES_FILE}")' ${BUILD_PROPERTIES}
	yq -i '(.standalone.type = "${STANDALONE_TYPE_FINAL}")' ${BUILD_PROPERTIES}
	yq -i '(.standalone.version = "${STANDALONE_VERSION}")' ${BUILD_PROPERTIES}
	yq -i '(.standalone.bin_zip_url = "${STANDALONE_BIN_ZIP_URL}")' ${BUILD_PROPERTIES}
	yq -i '(.operator.type = "${OPERATOR_TYPE_FINAL}")' ${BUILD_PROPERTIES}
	yq -i '(.operator.version = "${OPERATOR_VERSION}")' ${BUILD_PROPERTIES}
	yq -i '(.operator.latest_version = "${OPERATOR_CRD_LATEST_OPERATOR_VERSION}")' ${BUILD_PROPERTIES}
	yq -i '(.operator.artemis_container_version = "${OPERATOR_ARTEMIS_CONTAINER_VERSION}")' ${BUILD_PROPERTIES}
	yq -i '(.operator.operator_image = "${OPERATOR_IMAGE}")' ${BUILD_PROPERTIES}
	yq -i '(.operator.broker_image = "${OPERATOR_BROKER_IMAGE}")' ${BUILD_PROPERTIES}
	yq -i '(.operator.broker_init_image = "${OPERATOR_BROKER_INIT_IMAGE}")' ${BUILD_PROPERTIES}
	yq -i '(.operator.src_url = "${OPERATOR_CRD_OPERATOR_SRC_URL}")' ${BUILD_PROPERTIES}
	if [[ "${OPERATOR_TYPE_FINAL}" == "amq-broker" ]]; then \
		yq -i '(.operator.upstream_version = "${OPERATOR_VERSION_UPSTREAM}")' ${BUILD_PROPERTIES} ;\
		yq -i '(.operator.artemis_container_version_upstream = "${OPERATOR_ARTEMIS_CONTAINER_VERSION_UPSTREAM}")' ${BUILD_PROPERTIES} ;\
		yq -i '(.operator.container.mmm_version = "${OPERATOR_CONTAINER_IMAGE_NAME_MMM_VERSION}")' ${BUILD_PROPERTIES} ;\
		yq -i '(.operator.container.mm_version = "${OPERATOR_CONTAINER_IMAGE_NAME_MM_VERSION}")' ${BUILD_PROPERTIES} ;\
	fi
	yq -i '(.operator.container.deploy = ${OPERATOR_CONTAINER_DEPLOY})' ${BUILD_PROPERTIES}
	yq -i '(.operator.container.generate_lpt_tag = ${OPERATOR_CONTAINER_GENERATE_LPT_TAG})' ${BUILD_PROPERTIES}
	yq -i '(.operator.container.skip_build = ${OPERATOR_CONTAINER_SKIP_BUILD})' ${BUILD_PROPERTIES}
	yq -i '(.operator.container.arch = "${OPERATOR_CONTAINER_ARCH}")' ${BUILD_PROPERTIES}
	yq -i '(.operator.container.full_version = "${OPERATOR_CONTAINER_IMAGE_NAME_FULL_VERSION}")' ${BUILD_PROPERTIES}
	@echo ""
	@echo ""

print_build_info:
	@echo "###############################################"
	@echo "###### Executing $@ target ######"
	@echo "###############################################"
	@echo ""
	@echo "Build info:"
	@cat ${BUILD_PROPERTIES}
	@echo ""
	@echo ""

### Development targets
checkstyle:
	@echo "#########################################"
	@echo "###### Executing $@ target ######"
	@echo "#########################################"
	${MVN_DEFAULT_CMD} checkstyle:check
	@echo ""
	@echo ""

display_mvn_property_updates:
	@echo "###########################################################"
	@echo "###### Executing $@ target ######"
	@echo "###########################################################"
	${MVN_DEFAULT_CMD} versions:display-property-updates
	@echo ""
	@echo ""

.PHONY: print_vars
print_vars:
	@echo "##########################################"
	@echo "###### Executing $@ target ######"
	@echo "#########################################"
	$(foreach V,$(sort $(.VARIABLES)),$(if $(filter-out environment% default automatic,$(origin $V)),$(info $V=$($V))))
	@echo ""
	@echo ""

### Standalone targets
standalone_clean:
	@echo "###############################################"
	@echo "###### Executing $@ target ######"
	@echo "###############################################"
	rm -rf ${STANDALONE_ARTEMIS_DIR} ${STANDALONE_SUITE_DIR}/certificates
	@echo ""
	@echo ""

standalone_prepare: standalone_prepare_dirs standalone_download_zip standalone_generate_artemis_default_cfg

standalone_build_java:
	@echo "####################################################"
	@echo "###### Executing $@ target ######"
	@echo "####################################################"
	${MVN_DEFAULT_CMD} --projects :standalone-suite --also-make -DskipTests install
	@echo ""
	@echo ""

standalone_build: standalone_prepare standalone_build_java generate_current_build_info print_build_info

standalone_test_smoke:
	@echo "####################################################"
	@echo "###### Executing $@ target ######"
	@echo "####################################################"
	${MVN_TEST_CMD} --projects :standalone-suite -Dgroups="smoke"
	@echo ""
	@echo ""

standalone_test:
	@echo "##############################################"
	@echo "###### Executing $@ target ######"
	@echo "##############################################"
	${MVN_TEST_CMD} --projects :standalone-suite ${MVN_TESTS_PARAM} ${MVN_GROUPS_PARAM}
	@echo ""
	@echo ""

standalone_prepare_dirs:
	@echo "######################################################"
	@echo "###### Executing $@ target ######"
	@echo "######################################################"
	mkdir -p ${STANDALONE_ARTEMIS_INSTALL_DIR}
	@echo ""
	@echo ""

standalone_download_zip: prepare_root_dirs
	@echo "######################################################"
	@echo "###### Executing $@ target ######"
	@echo "######################################################"
	if [[ "${STANDALONE_TYPE_FINAL}" == "activemq-artemis" ]] && [[ "${STANDALONE_VERSION}" == "snapshot" ]]; then \
		echo "Setting STANDALONE_BIN_ZIP_URL_FINAL as SNAPSHOT url" ;\
		$(eval STANDALONE_BIN_ZIP_URL_FINAL := $(shell ${STANDALONE_SUITE_DIR}/scripts/get-activemq-artemis-snapshot_url.sh ${VERSION_PROPERTIES_FILE})) \
	else \
		echo "Setting STANDALONE_BIN_ZIP_URL_FINAL as DEFAULT url" ;\
		$(eval STANDALONE_BIN_ZIP_URL_FINAL := ${STANDALONE_BIN_ZIP_URL_FINAL}) \
	fi
	echo "Downloading standalone bin zip: ${STANDALONE_BIN_ZIP_URL}"
	${WGET_CMD} ${STANDALONE_BIN_ZIP_URL} -P ${TMP_DIR}
	$(eval ARTEMIS_FILE := ${TMP_DIR}/$(shell basename ${STANDALONE_BIN_ZIP_URL}))
	cp -f ${ARTEMIS_FILE} ${STANDALONE_ARTEMIS_DIR}/artemis.zip
	rm -rf ${STANDALONE_ARTEMIS_INSTALL_DIR}/*
	unzip -q -o ${STANDALONE_ARTEMIS_DIR}/artemis.zip -d ${STANDALONE_ARTEMIS_INSTALL_DIR}
	mv ${STANDALONE_ARTEMIS_INSTALL_DIR}/*/* ${STANDALONE_ARTEMIS_INSTALL_DIR}
	rm -rf ${STANDALONE_ARTEMIS_INSTALL_DIR}/examples
	find ${STANDALONE_ARTEMIS_INSTALL_DIR} -maxdepth 1 -type d -name apache-artemis-* -exec rmdir \{\} \;
	@echo ""
	@echo ""

standalone_generate_artemis_default_cfg:
	@echo "######################################################################"
	@echo "###### Executing $@ target ######"
	@echo "######################################################################"
	# create default instance
	${STANDALONE_ARTEMIS_INSTALL_DIR}/bin/artemis create --force --name ${STANDALONE_INSTANCE_NAME} \
		--user ${STANDALONE_USER} --password ${STANDALONE_PASSWORD} \
		--require-login ${STANDALONE_ARTEMIS_DEFAULT_CFG_DIR} --no-autotune
	# update artemis command with correct container instance path
	sed -i.bak -re "s#^(ARTEMIS_INSTANCE_ETC=)(.*)#\1'${STANDALONE_CONTAINER_ARTEMIS_INSTANCE_ETC_DIR}'#" \
		${STANDALONE_ARTEMIS_DEFAULT_CFG_DIR}/bin/artemis
	@echo ""
	@echo ""

### Operator CRD targets
operator_crd_clean:
	@echo "#################################################"
	@echo "###### Executing $@ target ######"
	@echo "#################################################"
	rm -rf ${OPERATOR_CRD_ARTEMIS_DIR}
	@echo ""
	@echo ""

operator_crd_prepare: operator_crd_get_operator_source_zip operator_crd_set_project_version operator_crd_prepare_dirs operator_crd_copy_files

operator_crd_build: operator_crd_prepare operator_crd_build_java generate_current_build_info print_build_info

operator_crd_get_operator_source_zip: prepare_root_dirs
	@echo "###################################################################"
	@echo "###### Executing $@ target ######"
	@echo "###################################################################"
	${WGET_CMD} ${OPERATOR_CRD_OPERATOR_SRC_URL} -O ${TMP_DIR}/operator_src.zip
	unzip -q -o ${TMP_DIR}/operator_src.zip -d ${TMP_DIR}
	find ${TMP_DIR} -name '${OPERATOR_TYPE_FINAL}-operator-*' -type d | tail -1 | xargs -I{} mv {} ${OPERATOR_CRD_SRC_DIR}
	@echo ""
	@echo ""

operator_crd_set_project_version:
	@echo "###############################################################"
	@echo "###### Executing $@ target ######"
	@echo "###############################################################"
	${MVN_DEFAULT_CMD} --projects :operator-crd -DnewVersion=${OPERATOR_VERSION} versions:set
	@echo ""
	@echo ""

operator_crd_prepare_dirs:
	@echo "########################################################"
	@echo "###### Executing $@ target ######"
	@echo "########################################################"
	mkdir -p ${OPERATOR_CRD_ARTEMIS_DIR}/{crds,install,examples}
	@echo ""
	@echo ""

operator_crd_copy_files: 
	@echo "######################################################"
	@echo "###### Executing $@ target ######"
	@echo "######################################################"
	@if [[ ${OPERATOR_VERSION} =~ ${OPERATOR_CRD_LATEST_OPERATOR_VERSION} ]]; then \
		echo "[CRD] Using CRDs from operator source version ${OPERATOR_VERSION}" ;\
		cp -ax ${OPERATOR_CRD_SRC_DIR}/deploy/crds/* ${OPERATOR_CRD_ARTEMIS_DIR}/crds/ ;\
	else \
		echo "[CRD] Using CRDs from upstream repo on main branch" ;\
		${WGET_CMD} -q -O - https://api.github.com/repos/artemiscloud/activemq-artemis-operator/contents/deploy/crds | jq -r '.[].download_url' | xargs -n 1 ${WGET_CMD} -P ${OPERATOR_CRD_ARTEMIS_DIR}/crds ;\
	fi ;
	cp ${OPERATOR_CRD_SRC_DIR}/deploy/*.yaml ${OPERATOR_CRD_ARTEMIS_DIR}/install/
	cp -ax ${OPERATOR_CRD_SRC_DIR}/examples/* ${OPERATOR_CRD_ARTEMIS_DIR}/examples/
	rm -rf ${OPERATOR_CRD_SRC_DIR}
	@echo ""
	@echo ""

operator_crd_build_java:
	@echo "######################################################"
	@echo "###### Executing $@ target ######"
	@echo "######################################################"
	${MVN_DEFAULT_CMD} --projects :operator-crd --also-make install
	@echo ""
	@echo ""

### Operator targets
operator_clean:
	@echo "#############################################"
	@echo "###### Executing $@ target ######"
	@echo "#############################################"
	rm -rf ${OPERATOR_ARTEMIS_DIR}
	@echo ""
	@echo ""

operator_prepare_dirs:
	@echo "####################################################"
	@echo "###### Executing $@ target ######"
	@echo "####################################################"
	mkdir -p ${OPERATOR_ARTEMIS_DIR}
	@echo ""
	@echo ""

operator_prepare_dependency_version:
	@echo "##################################################################"
	@echo "###### Executing $@ target ######"
	@echo "##################################################################"
	${MVN_DEFAULT_CMD} -Dproperty=operator-crd.version -DnewVersion=${OPERATOR_VERSION} -DallowSnapshots=true versions:set-property
	@echo ""
	@echo ""

operator_prepare: operator_prepare_dirs operator_prepare_dependency_version

operator_build_java:
	@echo "##################################################"
	@echo "###### Executing $@ target ######"
	@echo "##################################################"
	${MVN_DEFAULT_CMD} --projects :operator-suite --also-make -DskipTests install
	@echo ""
	@echo ""

operator_build: operator_crd_prepare operator_prepare operator_build_java generate_current_build_info print_build_info

operator_test_smoke:
	@echo "##################################################"
	@echo "###### Executing $@ target ######"
	@echo "##################################################"
	${MVN_TEST_CMD} --projects :operator-suite -Dgroups="smoke"
	@echo ""
	@echo ""

operator_test:
	@echo "############################################"
	@echo "###### Executing $@ target ######"
	@echo "############################################"
	${MVN_TEST_CMD} --projects :operator-suite ${MVN_TESTS_PARAM} ${MVN_GROUPS_PARAM}
	@echo ""
	@echo ""

### Container targets
build_container:
	@echo "##############################################"
	@echo "###### Executing $@ target ######"
	@echo "##############################################"
	@echo "Preparing host for multi arch build"
	@sudo podman run --privileged --rm docker.io/tonistiigi/binfmt --install all
	@if [[ "${OPERATOR_CONTAINER_DEPLOY}" == "true" ]]; then \
		echo "" ;\
		echo "" ;\
		echo "Creating a new manifest: ${OPERATOR_CONTAINER_IMAGE_NAME_FULL_VERSION}" ;\
		podman manifest rm "${OPERATOR_CONTAINER_IMAGE_NAME_FULL_VERSION}" 2> /dev/null || true ;\
		podman rmi "${OPERATOR_CONTAINER_IMAGE_NAME_FULL_VERSION}" 2> /dev/null || true ;\
		podman manifest create "${OPERATOR_CONTAINER_IMAGE_NAME_FULL_VERSION}" ;\
		if [[ "${OPERATOR_TYPE_FINAL}" == "amq-broker" ]]; then \
			echo "Create manifest for version ${OPERATOR_MMM_VERSION}" ;\
			podman manifest rm "${OPERATOR_CONTAINER_IMAGE_NAME_MMM_VERSION}" 2> /dev/null || true ;\
			podman rmi "${OPERATOR_CONTAINER_IMAGE_NAME_MMM_VERSION}" 2> /dev/null || true ;\
			podman manifest create "${OPERATOR_CONTAINER_IMAGE_NAME_MMM_VERSION}" ;\
			echo "Create manifest for version ${OPERATOR_MM_VERSION}" ;\
			podman manifest rm "${OPERATOR_CONTAINER_IMAGE_NAME_MM_VERSION}" 2> /dev/null || true ;\
			podman rmi "${OPERATOR_CONTAINER_IMAGE_NAME_MM_VERSION}" 2> /dev/null || true ;\
			podman manifest create "${OPERATOR_CONTAINER_IMAGE_NAME_MM_VERSION}" ;\
			if [[ "${OPERATOR_CONTAINER_GENERATE_LPT_TAG}" == "true" ]]; then \
				echo "Create manifest for version ${OPERATOR_CONTAINER_LPT_VERSION}" ;\
				podman manifest rm "${OPERATOR_CONTAINER_IMAGE_NAME_LPT_VERSION}" || true ;\
				podman rmi "${OPERATOR_CONTAINER_IMAGE_NAME_LPT_VERSION}" || true ;\
				podman manifest create "${OPERATOR_CONTAINER_IMAGE_NAME_LPT_VERSION}" ;\
			fi ;\
		fi ;\
	fi

	@for arch in ${OPERATOR_CONTAINER_ARCH} ; do \
		FULL_VERSION_WITH_ARCH="${OPERATOR_CONTAINER_IMAGE_NAME_FULL_VERSION}.$${arch}" ;\
		if [[ "${OPERATOR_TYPE_FINAL}" == "amq-broker" ]]; then \
			MMM_VERSION_WITH_ARCH="${OPERATOR_CONTAINER_IMAGE_NAME_MMM_VERSION}.$${arch}" ;\
			MM_VERSION_WITH_ARCH="${OPERATOR_CONTAINER_IMAGE_NAME_MM_VERSION}.$${arch}" ;\
			LPT_VERSION_WITH_ARCH="${OPERATOR_CONTAINER_IMAGE_NAME_LPT_VERSION}.$${arch}" ;\
		fi ;\
		echo "" ;\
		echo "" ;\
		echo "Building a new container image: ${OPERATOR_CONTAINER_IMAGE_NAME_FULL_VERSION}, arch: $${arch}" ;\
	  	if [[ "${OPERATOR_CONTAINER_SKIP_BUILD}" == "false" ]]; then \
			podman build --arch=$${arch} --tag $${FULL_VERSION_WITH_ARCH} --build-arg buildArch=$${arch} \
						--build-arg operatorType=${OPERATOR_TYPE_FINAL} --build-arg operatorVersion=${OPERATOR_VERSION} \
						--file operator-suite/container/Containerfile . ;\
			if [[ "${OPERATOR_TYPE_FINAL}" == "amq-broker" ]]; then \
				echo "Creating tag from $${FULL_VERSION_WITH_ARCH} to $${MMM_VERSION_WITH_ARCH}" ;\
				podman tag "$${FULL_VERSION_WITH_ARCH}" "$${MMM_VERSION_WITH_ARCH}" ;\
				echo "Creating tag from $${FULL_VERSION_WITH_ARCH} to $${MM_VERSION_WITH_ARCH}" ;\
				podman tag "$${FULL_VERSION_WITH_ARCH}" "$${MM_VERSION_WITH_ARCH}" ;\
				if [[ "${OPERATOR_CONTAINER_GENERATE_LPT_TAG}" == "true" ]]; then \
					echo "Creating tag from $${FULL_VERSION_WITH_ARCH} to $${LPT_VERSION_WITH_ARCH}" ;\
					podman tag "$${FULL_VERSION_WITH_ARCH}" "$${LPT_VERSION_WITH_ARCH}" ;\
				fi ;\
			fi ;\
		fi ;\
	  	if [[ "${OPERATOR_CONTAINER_DEPLOY}" == "true" ]]; then \
			echo "Pushing $${FULL_VERSION_WITH_ARCH}" ;\
			podman push "$${FULL_VERSION_WITH_ARCH}" ;\
			echo "Adding ${OPERATOR_CONTAINER_IMAGE_NAME_FULL_VERSION} to $${FULL_VERSION_WITH_ARCH} manifest" ;\
	  		podman manifest add "${OPERATOR_CONTAINER_IMAGE_NAME_FULL_VERSION}" "$${FULL_VERSION_WITH_ARCH}" ;\
			if [[ "${OPERATOR_TYPE_FINAL}" == "amq-broker" ]]; then \
				echo "Pushing $${MMM_VERSION_WITH_ARCH}" ;\
				podman push "$${MMM_VERSION_WITH_ARCH}" ;\
	  			echo "Adding ${OPERATOR_CONTAINER_IMAGE_NAME_MMM_VERSION} to $${MMM_VERSION_WITH_ARCH} manifest" ;\
				podman manifest add "${OPERATOR_CONTAINER_IMAGE_NAME_MMM_VERSION}" "$${MMM_VERSION_WITH_ARCH}" ;\
				echo "Pushing $${MM_VERSION_WITH_ARCH}" ;\
				podman push "$${MM_VERSION_WITH_ARCH}" ;\
				echo "Adding ${OPERATOR_CONTAINER_IMAGE_NAME_MM_VERSION} to $${MM_VERSION_WITH_ARCH} manifest" ;\
	  			podman manifest add "${OPERATOR_CONTAINER_IMAGE_NAME_MM_VERSION}" "$${MM_VERSION_WITH_ARCH}" ;\
				if [[ "${OPERATOR_CONTAINER_GENERATE_LPT_TAG}" == "true" ]]; then \
					podman push "$${LPT_VERSION_WITH_ARCH}" ;\
	  				podman manifest add "${OPERATOR_CONTAINER_IMAGE_NAME_LPT_VERSION}" "$${LPT_VERSION_WITH_ARCH}" ;\
				fi ;\
			fi ;\
		fi ;\
	done

	@if [[ "${OPERATOR_CONTAINER_DEPLOY}" == "true" ]]; then \
		echo "" ;\
		echo "" ;\
		echo "Pushing a manifest: ${OPERATOR_CONTAINER_IMAGE_NAME_FULL_VERSION}" ;\
		podman manifest push ${OPERATOR_CONTAINER_IMAGE_NAME_FULL_VERSION} docker://${OPERATOR_CONTAINER_IMAGE_NAME_FULL_VERSION} ;\
		if [[ "${OPERATOR_TYPE_FINAL}" == "amq-broker" ]]; then \
			echo "Pushing a manifest: ${OPERATOR_CONTAINER_IMAGE_NAME_MMM_VERSION}" ;\
			podman manifest push ${OPERATOR_CONTAINER_IMAGE_NAME_MMM_VERSION} docker://${OPERATOR_CONTAINER_IMAGE_NAME_MMM_VERSION} ;\
			echo "Pushing a manifest: ${OPERATOR_CONTAINER_IMAGE_NAME_MM_VERSION}" ;\
			podman manifest push ${OPERATOR_CONTAINER_IMAGE_NAME_MM_VERSION} docker://${OPERATOR_CONTAINER_IMAGE_NAME_MM_VERSION} ;\
			if [[ "${OPERATOR_CONTAINER_GENERATE_LPT_TAG}" == "true" ]]; then \
				echo "Pushing a new manifest: ${OPERATOR_CONTAINER_IMAGE_NAME_LPT_VERSION}" ;\
				podman manifest push ${OPERATOR_CONTAINER_IMAGE_NAME_LPT_VERSION} docker://${OPERATOR_CONTAINER_IMAGE_NAME_LPT_VERSION} ;\
			fi ;\
		fi ;\
	fi
	@echo ""
	@echo ""

