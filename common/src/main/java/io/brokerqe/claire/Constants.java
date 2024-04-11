/*
 * Copyright Strimzi and Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire;

import java.time.Duration;

public interface Constants {
    // Platform related strings
    String LINE_SEPARATOR = System.getProperty("line.separator");
    String FILE_SEPARATOR = System.getProperty("file.separator");
    String DATE_FORMAT = "yyyy-MM-dd_HH-mm-ss";

    // Test tags
    String TAG_JAAS = "jaas";
    String TAG_JDBC = "jdbc";
    String TAG_OPERATOR = "operator";
    String TAG_SMOKE = "smoke";
    String TAG_SMOKE_CLIENTS = "smoke-clients";
    String TAG_TLS = "tls";

    // Environment Variables
    String EV_KUBE_CONTEXT = "KUBE_CONTEXT";
    String EV_ARTEMIS_VERSION = "ARTEMIS_VERSION";
    String EV_ARTEMIS_TEST_VERSION = "ARTEMIS_TEST_VERSION";
    String EV_DISABLE_RANDOM_NAMESPACES = "DISABLE_RANDOM_NAMESPACES";
    String EV_TEST_LOG_LEVEL = "TEST_LOG_LEVEL";
    String EV_OLM_IIB = "OLM_IIB";
    String EV_OLM_CHANNEL = "OLM_CHANNEL";
    String EV_OLM_RELEASED = "OLM";
    String EV_OLM_LTS = "OLM_LTS";
    String EV_OPERATOR_IMAGE = "OPERATOR_IMAGE";
    String EV_BROKER_IMAGE = "BROKER_IMAGE";
    String EV_BROKER_INIT_IMAGE = "BROKER_INIT_IMAGE";
    String EV_BUNDLE_IMAGE = "BUNDLE_IMAGE";
    String EV_UPGRADE_PLAN = "UPGRADE_PLAN";
    String EV_LOGS_LOCATION = "LOGS_LOCATION";
    String EV_TMP_LOCATION = "TMP_LOCATION";
    String EV_DUMP_ENABLED = "DUMP_ENABLED";
    String EV_DUMP_LOCATION = "DUMP_LOCATION";
    String EV_DUMP_FORMAT = "DUMP_FORMAT";
    String EV_CLUSTER_OPERATOR_MANAGED = "CLUSTER_OPERATOR_MANAGED";
    String EV_COLLECT_TEST_DATA = "COLLECT_TEST_DATA";
    String EV_JDBC_DATA = "JDBC_DATA";
    String PROP_JDBC_DATA = "jdbc.data";
    String EV_CUSTOM_EXTRA_DELAY = "CUSTOM_EXTRA_DELAY";
    String EV_KEYCLOAK_OPERATOR_NAME = "KEYCLOAK_OPERATOR_NAME";
    String EV_KEYCLOAK_VERSION = "KEYCLOAK_VERSION";
    String EV_KEYCLOAK_CHANNEL = "KEYCLOAK_CHANNEL";
    String EV_USE_EXISTING_CONFIG = "USE_EXISTING_CONFIG";
    String PROP_USE_EXISTING_CONFIG = "use.existing.config";

    String BROKER_KEYSTORE_ID = "broker.ks";
    String BROKER_TRUSTSTORE_ID = "broker.ts";
    String CLIENT_KEYSTORE_ID = "client.ks";
    String CLIENT_TRUSTSTORE_ID = "client.ts";
    String KEY_TRUSTSTORE_PASSWORD = "trustStorePassword";
    String KEY_KEYSTORE_PASSWORD = "keyStorePassword";

    // Test related strings
    String DEFAULT_KEYCLOAK_OPERATOR_NAME = "keycloak-operator";
    String DEFAULT_RHSSO_OPERATOR_NAME = "rhbk-operator";
    String DEFAULT_KEYCLOAK_VERSION = "22.0.5";
    String DEFAULT_RHSSO_VERSION = "rhbk-operator.v22.0.9-opr.1";
    String DEFAULT_KEYCLOAK_CHANNEL = "fast";
    String DEFAULT_RHSSO_CHANNEL = "stable-v22";

    // Networking
    String AMQP = "amqp";
    String AMQP_URL_PREFIX = AMQP + "://";
    String HTTP = "http";
    String HTTPS = "https";
    String GET = "GET";
    String POST = "POST";

    // Timing variables
    long DURATION_100_MILLISECONDS = Duration.ofMillis(100).toMillis();
    long DURATION_500_MILLISECONDS = Duration.ofMillis(500).toMillis();
    long DURATION_1_SECOND = Duration.ofSeconds(1).toMillis();
    long DURATION_2_SECONDS = Duration.ofSeconds(2).toMillis();
    long DURATION_5_SECONDS = Duration.ofSeconds(5).toMillis();
    long DURATION_10_SECONDS = Duration.ofSeconds(10).toMillis();
    long DURATION_30_SECONDS = Duration.ofSeconds(30).toMillis();
    long DURATION_1_MINUTE = Duration.ofMinutes(1).toMillis();
    long DURATION_2_MINUTES = Duration.ofMinutes(2).toMillis();
    long DURATION_3_MINUTES = Duration.ofMinutes(3).toMillis();
    long DURATION_5_MINUTES = Duration.ofMinutes(5).toMillis();

    // Files
    String PROJECT_USER_DIR = System.getProperty("user.dir");
    String PROJECT_TEST_DIR = PROJECT_USER_DIR + "/src/test";
    String LOGS_DEFAULT_DIR = PROJECT_USER_DIR + "/test-logs";
    String TMP_DEFAULT_DIR = PROJECT_USER_DIR + "/test-tmp";
    String DUMP_DEFAULT_DIR = PROJECT_USER_DIR + "/serialization-dump";
    String DUMP_DEFAULT_TYPE = "yaml";
    String CERTS_GENERATION_DIR = "certificates";
    String PROJECT_SETTINGS_PATH = PROJECT_USER_DIR + "/artemis/project-settings.properties";
    String OPERATOR_CRDS_DIR_PATH = PROJECT_USER_DIR + "/artemis/crds/";
    String OPERATOR_INSTALL_DIR_PATH = PROJECT_USER_DIR + "/artemis/install/";
    String EXAMPLES_DIR_PATH = PROJECT_USER_DIR + "/artemis/examples/";
    String VERSION_MAPPER_PATH = PROJECT_USER_DIR + "/../version_map.yaml";
    String PERFORMANCE_DIR = PROJECT_USER_DIR + "/../performance/";

    String PROP_LOG_DIR = "log.dir";
    String PROP_LOG_LEVEL = "log.level";
    String JAVA_HOME = "JAVA_HOME";
    String CLAIRE_TEST_PKG_REGEX = ".+\\.claire\\.";
    String TAR_TMP_FILE_PREFIX = "/container_files_";
    String STANDALONE_MODULE_PROPERTIES_FILE = "standalone.properties";

    boolean DEFAULT_LOG_CONTAINERS = false;
    String EV_LOG_CONTAINERS = "LOG_CONTAINERS";
    String PROP_LOG_CONTAINERS = "log.containers";
    String DEFAULT_ARTEMIS_CONTAINER_IMAGE = "quay.io/rhmessagingqe/claire-standalone-artemis:fedora";
    String EV_ARTEMIS_CONTAINER_IMAGE = "ARTEMIS_CONTAINER_IMAGE";
    String PROP_ARTEMIS_CONTAINER_IMAGE = "artemis.container.image";
    String DEFAULT_ARTEMIS_CONTAINER_INSTANCE_JAVA_HOME = "/opt/openjdk-java-11";
    String EV_ARTEMIS_CONTAINER_JAVA_HOME = "ARTEMIS_CONTAINER_JAVA_HOME";
    String PROP_ARTEMIS_CONTAINER_JAVA_HOME = "artemis.container.java.home";

    String DEFAULT_NFS_SERVER_CONTAINER_IMAGE = "quay.io/rhmessagingqe/claire-nfs-server:fedora";
    String EV_NFS_SERVER_CONTAINER_IMAGE = "NFS_SERVER_CONTAINER_IMAGE";
    String PROP_NFS_SERVER_CONTAINER_IMAGE = "nfsserver.container.image";

    String DEFAULT_TOXI_PROXY_CONTAINER_IMAGE = "ghcr.io/shopify/toxiproxy:latest";
    String EV_TOXI_PROXY_CONTAINER_IMAGE = "TOXI_PROXY_CONTAINER_IMAGE";
    String PROP_TOXI_PROXY_CONTAINER_IMAGE = "toxiproxy.container.image";

    String DEFAULT_ZOOKEEPER_CONTAINER_IMAGE = "zookeeper:latest";
    String EV_ZOOKEEPER_CONTAINER_IMAGE = "ZOOKEEPER_CONTAINER_IMAGE";
    String PROP_ZOOKEEPER_CONTAINER_IMAGE = "zookeeper.container.image";

    String DEFAULT_YACFG_ARTEMIS_PROFILE = "claire-default-profile-%ARTEMIS_VERSION%.yaml.jinja2";
    String EV_YACFG_ARTEMIS_PROFILE = "YACFG_ARTEMIS_PROFILE";
    String PROP_YACFG_ARTEMIS_PROFILE = "yacfg.artemis.profile";
    String DEFAULT_YACFG_ARTEMIS_CONTAINER_IMAGE_BASE = "quay.io/rhmessagingqe/yacfg_artemis";
    String EV_YACFG_ARTEMIS_CONTAINER_IMAGE = "YACFG_ARTEMIS_CONTAINER_IMAGE";
    String PROP_YACFG_ARTEMIS_CONTAINER_IMAGE = "yacfg.artemis.container.image";
    String EV_YACFG_ARTEMIS_PROFILES_OVERRIDE_DIR = "YACFG_ARTEMIS_PROFILES_OVERRIDE_DIR";
    String PROP_YACFG_ARTEMIS_PROFILES_OVERRIDE_DIR = "yacfg.artemis.profiles_override_dir";
    String EV_YACFG_ARTEMIS_TEMPLATES_OVERRIDE_DIR = "YACFG_ARTEMIS_TEMPLATES_OVERRIDE_DIR";
    String PROP_YACFG_ARTEMIS_TEMPLATES_OVERRIDE_DIR = "yacfg.artemis.templates_override_dir";

    String ARTEMIS_DEFAULT_CFG_DIR = "artemis/artemis_default_cfg";
    String ARTEMIS_TEST_CFG_DIR = "test-cfg";
    String ARTEMIS_DEFAULT_CFG_BIN_DIR = ARTEMIS_DEFAULT_CFG_DIR + FILE_SEPARATOR + ArtemisConstants.BIN_DIR;
    String ARTEMIS_DEFAULT_CFG_LIB_DIR = ARTEMIS_DEFAULT_CFG_DIR + FILE_SEPARATOR + ArtemisConstants.LIB_DIR;

    String PREFIX_SYSTEMTESTS_CLIENTS = "systemtests-clients";
    String PREFIX_SYSTEMTESTS_CLI_PROTON_DOTNET = "systemtests-cli-proton-dotnet";
    String PREFIX_SYSTEMTESTS_CLI_CPP = "systemtests-cli-cpp";
    String PREFIX_SYSTEMTESTS_CLI_PROTON_PYTHON = "systemtests-cli-proton-python";
    String PREFIX_SYSTEMTESTS_CLI_RHEA = "systemtests-cli-rhea";
    String PREFIX_MQTT_CLIENT = "mqtt-client";

    // Images
    String IMAGE_SYSTEMTEST_CLIENTS = "quay.io/rhmessagingqe/cli-java:latest";
    String IMAGE_SYSTEMTEST_CLI_PROTON_DOTNET = "quay.io/messaging/cli-proton-dotnet:latest";
    String IMAGE_SYSTEMTEST_CLI_CPP = "quay.io/messaging/cli-cpp:latest";
    String IMAGE_SYSTEMTEST_CLI_PROTON_PYTHON = "quay.io/messaging/cli-proton-python:latest";
    String IMAGE_SYSTEMTEST_CLI_RHEA = "quay.io/messaging/cli-rhea:latest";
    String IMAGE_MQTT_CLIENT = "quay.io/rhmessagingqe/hivemq-mqtt-cli";
//    String IMAGE_OPENLDAP = "docker.io/bitnami/openldap:latest";
    String IMAGE_OPENLDAP = "docker.io/bitnami/openldap:2.6.3";
    String IMAGE_POSTGRES = "docker.io/bitnami/postgresql:latest";
    String IMAGE_MYSQL = "docker.io/bitnami/mysql:latest";
    String IMAGE_MARIADB = "docker.io/bitnami/mariadb:latest";
    // https://hub.docker.com/_/microsoft-mssql-server
    String IMAGE_MSSQL = "mcr.microsoft.com/mssql/server:2022-latest";
    String IMAGE_ORACLE = "container-registry.oracle.com/database/free:latest";

    // Openshift related
    String MONITORING_NAMESPACE = "openshift-monitoring";
    String MONITORING_NAMESPACE_USER = "openshift-user-workload-monitoring";
    String PROMETHEUS_USER_SS = "prometheus-user-workload";
    String THANOS_USER_SS = "thanos-ruler-user-workload";

    // Database
    String POSTGRESQL_DRIVER_URL = "https://jdbc.postgresql.org/download/postgresql-42.6.0.jar";
    String MYSQL_DRIVER_URL = "https://repo1.maven.org/maven2/mysql/mysql-connector-java/8.0.30/mysql-connector-java-8.0.30.jar";
    String MARIADB_DRIVER_URL = "https://dlm.mariadb.com/2912798/Connectors/java/connector-java-3.1.4/mariadb-java-client-3.1.4.jar";
    String MSSQL_DRIVER_URL = "https://download.microsoft.com/download/a/9/1/a91534b0-ed8c-4501-b491-e1dd0a20335a/sqljdbc_12.2.0.0_enu.zip";
    String ORACLE_DRIVER_URL = "https://download.oracle.com/otn-pub/otn_software/jdbc/2110/ojdbc11.jar";

}
