#
# Copyright Broker QE authors.
# License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
#
ROOT_DIR                                          = $(shell dirname $(realpath $(lastword $(MAKEFILE_LIST))))
MVN_DEFAULT_CMD                                   = mvn -T 1.5C --no-transfer-progress
MVN_TEST_CMD                                      = ${MVN_DEFAULT_CMD} -Dfailsafe.rerunFailingTestsCount=3 failsafe:integration-test
WGET_CMD                                          = wget -nv -c

ARTEMIS_VERSION                                  ?= 2.33.0
OPERATOR_VERSION                                 ?= main

OPERATOR_ROOT_DIR                                 = ${ROOT_DIR}/operator-suite

STANDALONE_ROOT_DIR                               = ${ROOT_DIR}/standalone-suite
STANDALONE_ARTEMIS_DIR                            = ${STANDALONE_ROOT_DIR}/artemis
STANDALONE_ARTEMIS_INSTALL_DIR                    = ${STANDALONE_ARTEMIS_DIR}/artemis_install
STANDALONE_ARTEMIS_DEFAULT_CFG_DIR                = ${STANDALONE_ARTEMIS_DIR}/artemis_default_cfg
STANDALONE_INSTANCE_NAME                          = artemis-instance
STANDALONE_USER                                   = admin
STANDALONE_PASSWORD                               = admin
STANDALONE_CONTAINER_ARTEMIS_INSTANCE_DIR         = /var/lib/artemis-instance
STANDALONE_CONTAINER_ARTEMIS_INSTANCE_ETC_DIR     = ${STANDALONE_CONTAINER_ARTEMIS_INSTANCE_DIR}/etc

# Container variables
ARCH                                              = amd64 ppc64le s390x
VERSION                                          ?= latest
REGISTRY                                         ?= quay.io

# messaging
NAMESPACE                                        ?= rhmessagingqe
IMAGE_NAME                                        ="${REGISTRY}/${NAMESPACE}/claire:${VERSION}"

ifndef ARTEMIS_INSTALL_ZIP
    $(eval ARTEMIS_INSTALL_ZIP := https://dlcdn.apache.org/activemq/activemq-artemis/${ARTEMIS_VERSION}/apache-artemis-${ARTEMIS_VERSION}-bin.zip)
endif
ifndef ARTEMIS_INSTALL_ZIP_SECONDARY
    $(eval ARTEMIS_INSTALL_ZIP_SECONDARY := https://archive.apache.org/dist/activemq/activemq-artemis/${ARTEMIS_VERSION}/apache-artemis-${ARTEMIS_VERSION}-bin.zip)
endif
ifdef TESTS
    $(eval MVN_TESTS_PARAM := -Dit.test="${TESTS}")
endif
ifdef TEST_GROUPS
    $(eval MVN_GROUPS_PARAM := -Dgroups="${TEST_GROUPS}")
endif

clean: clean_maven standalone_clean operator_clean

clean_maven:
	${MVN_DEFAULT_CMD} clean

build: standalone_prepare operator_prepare build_maven

build_maven:
	${MVN_DEFAULT_CMD} -DskipTests install

checkstyle:
	${MVN_DEFAULT_CMD} checkstyle:check

### Standalone targets
standalone_clean:
	mv standalone-suite/standalone.properties.bak standalone-suite/standalone.properties || true
	rm -rf ${STANDALONE_ARTEMIS_DIR}

standalone_prepare: standalone_prepare_dirs standalone_download_zip standalone_generate_artemis_default_cfg

standalone_build_java:
	${MVN_DEFAULT_CMD} --projects :standalone-suite install -DskipTests

standalone_build: standalone_prepare standalone_build_java

standalone_test_smoke:
	${MVN_TEST_CMD} --projects :standalone-suite -Dgroups="smoke"

standalone_test:
	${MVN_TEST_CMD} --projects :standalone-suite ${MVN_TESTS_PARAM} ${MVN_GROUPS_PARAM}

standalone_prepare_dirs:
	mkdir -p ${STANDALONE_ARTEMIS_INSTALL_DIR}

standalone_download_zip:
	${WGET_CMD} ${ARTEMIS_INSTALL_ZIP} -P /tmp/ || ${WGET_CMD} ${ARTEMIS_INSTALL_ZIP_SECONDARY} -P /tmp/
	$(eval ARTEMIS_FILE := /tmp/$(shell basename ${ARTEMIS_INSTALL_ZIP}))
	cp -f ${ARTEMIS_FILE} ${STANDALONE_ARTEMIS_DIR}/artemis.zip
	rm -rf ${STANDALONE_ARTEMIS_INSTALL_DIR}/*
	unzip -q -o ${STANDALONE_ARTEMIS_DIR}/artemis.zip -d ${STANDALONE_ARTEMIS_INSTALL_DIR}
	mv ${STANDALONE_ARTEMIS_INSTALL_DIR}/*/* ${STANDALONE_ARTEMIS_INSTALL_DIR}
	rm -rf ${STANDALONE_ARTEMIS_INSTALL_DIR}/examples
	find ${STANDALONE_ARTEMIS_INSTALL_DIR} -maxdepth 1 -type d -name apache-artemis-* -exec rmdir \{\} \;

standalone_generate_artemis_default_cfg:
	sed -i.bak -e 's/artemis.build.version=/artemis.build.version=${ARTEMIS_VERSION}/' standalone-suite/standalone.properties
	# create default instance
	${STANDALONE_ARTEMIS_INSTALL_DIR}/bin/artemis create --force --name ${STANDALONE_INSTANCE_NAME} \
		--user ${STANDALONE_USER} --password ${STANDALONE_PASSWORD} \
		--require-login ${STANDALONE_ARTEMIS_DEFAULT_CFG_DIR}
	# update artemis command with correct container instance path
	sed -i.bak -re "s#^(ARTEMIS_INSTANCE_ETC=)(.*)#\1'${STANDALONE_CONTAINER_ARTEMIS_INSTANCE_ETC_DIR}'#" \
		${STANDALONE_ARTEMIS_DEFAULT_CFG_DIR}/bin/artemis

### Operator targets
operator_clean:
	rm -rf ${OPERATOR_ROOT_DIR}/artemis

operator_prepare_dirs:
	mkdir -p ${OPERATOR_ROOT_DIR}/artemis

operator_prepare: operator_prepare_dirs

operator_build_java:
	${MVN_DEFAULT_CMD} versions:update-property -Dproperty=artemiscloud-crd.version -DnewVersion=${OPERATOR_VERSION}
	${MVN_DEFAULT_CMD} --projects :operator-suite install -DskipTests

operator_build: operator_prepare operator_build_java

operator_test_smoke:
	${MVN_DEFAULT_CMD} test --projects :operator-suite -Dgroups="smoke"

operator_test:
	${MVN_TEST_CMD} --projects :operator-suite ${MVN_TESTS_PARAM} ${MVN_GROUPS_PARAM}

build_container:
	sudo podman run --privileged --rm docker.io/tonistiigi/binfmt --install all
	echo "Creating a new manifest: ${IMAGE_NAME}"
	podman manifest rm ${IMAGE_NAME} || true
	podman manifest create ${IMAGE_NAME}

	@for arch in ${ARCH} ; do \
		echo "Building a new docker image: ${IMAGE_NAME}, arch: $${arch}" ;\
	  	podman build --arch=$${arch} -t ${IMAGE_NAME}.$${arch} --build-arg ARCH=$${arch} --build-arg VERSION=${VERSION} . ;\
	  	podman push ${IMAGE_NAME}.$${arch} ;\
	  	podman manifest add ${IMAGE_NAME} ${IMAGE_NAME}.$${arch} ;\
	done
	echo "Pushing a new manifest: ${IMAGE_NAME}"
	podman manifest push ${IMAGE_NAME} docker://${IMAGE_NAME}

.PHONY: clean
