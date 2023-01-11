/*
 * Copyright Strimzi and Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe;

import java.time.Duration;

public interface Constants {

    // Platform related strings
    String LINE_SEPARATOR = System.getProperty("line.separator");
    String FILE_SEPARATOR = System.getProperty("file.separator");

    // Environment Variables
    String EV_ARTEMIS_VERSION = "ARTEMIS_VERSION";
    String EV_DISABLE_RANDOM_NAMESPACES = "DISABLE_RANDOM_NAMESPACES";
    String EV_TEST_LOG_LEVEL = "TEST_LOG_LEVEL";
    String EV_OPERATOR_IMAGE = "OPERATOR_IMAGE";
    String EV_BROKER_IMAGE = "BROKER_IMAGE";
    String EV_BROKER_INIT_IMAGE = "BROKER_INIT_IMAGE";
    String EV_BUNDLE_IMAGE = "BUNDLE_IMAGE";
    String EV_LOGS_LOCATION = "LOGS_LOCATION";
    String EV_CLUSTER_OPERATOR_MANAGED = "CLUSTER_OPERATOR_MANAGED";

    String BROKER_IMAGE_OPERATOR_PREFIX = "RELATED_IMAGE_ActiveMQ_Artemis_Broker_Kubernetes_";
    String BROKER_INIT_IMAGE_OPERATOR_PREFIX = "RELATED_IMAGE_ActiveMQ_Artemis_Broker_Init_";
    String OPERATOR_IMAGE_OPERATOR_PREFIX = "image";

    // Artemis Operator strings
    String CRD_ACTIVEMQ_ARTEMIS_GROUP = "broker.amq.io";
    // CRDs needed for typeless usage
    String CRD_ACTIVEMQ_ARTEMIS = "activemqartemises" + "." + CRD_ACTIVEMQ_ARTEMIS_GROUP;

    String WATCH_ALL_NAMESPACES = "*";

    // Files
    String PROJECT_USER_DIR = System.getProperty("user.dir");
    String LOGS_DEFAULT_DIR = System.getProperty("user.dir") + "/test-logs";
    String PROJECT_SETTINGS_PATH = PROJECT_USER_DIR + "/artemis/project-settings.properties";
    String OPERATOR_CRDS_DIR_PATH = PROJECT_USER_DIR + "/artemis/crds/";
    String OPERATOR_INSTALL_DIR_PATH = PROJECT_USER_DIR + "/artemis/install/";
    String EXAMPLES_DIR_PATH = PROJECT_USER_DIR + "/artemis/examples/";

    // New naming style
    String INSTALL_ARTEMIS_CO_010_CRD_ARTEMIS_PATH = Constants.OPERATOR_CRDS_DIR_PATH + "010_crd_artemis.yaml";
    String INSTALL_ARTEMIS_CO_020_CRD_SECURITY_PATH = Constants.OPERATOR_CRDS_DIR_PATH + "020_crd_artemis_security.yaml";
    String INSTALL_ARTEMIS_CO_030_CRD_ADDRESS_PATH = Constants.OPERATOR_CRDS_DIR_PATH + "030_crd_artemis_address.yaml";
    String INSTALL_ARTEMIS_CO_040_CRD_SCALEDOWN_PATH = Constants.OPERATOR_CRDS_DIR_PATH + "040_crd_artemis_scaledown.yaml";
    String INSTALL_ARTEMIS_CO_050_SERVICE_ACCOUNT_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "050_service_account.yaml";
    String INSTALL_ARTEMIS_CO_060_CLUSTER_ROLE_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "060_cluster_role.yaml";
    String INSTALL_ARTEMIS_CO_070_CLUSTER_ROLE_BINDING_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "070_cluster_role_binding.yaml";
    String INSTALL_ARTEMIS_CO_060_NAMESPACE_ROLE_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "060_namespace_role.yaml";
    String INSTALL_ARTEMIS_CO_070_NAMESPACE_ROLE_BINDING_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "070_namespace_role_binding.yaml";
    String INSTALL_ARTEMIS_CO_080_ELECTION_ROLE_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "080_election_role.yaml";
    String INSTALL_ARTEMIS_CO_090_ELECTION_ROLE_BINDING_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "090_election_role_binding.yaml";
    String INSTALL_ARTEMIS_CO_100_OPERATOR_CONFIG_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "100_operator_config.yaml";
    String INSTALL_ARTEMIS_CO_110_OPERATOR_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "110_operator.yaml";

    String EXAMPLE_ADDRESS_QUEUE_PATH = Constants.EXAMPLES_DIR_PATH + "address/address_queue.yaml";
    String EXAMPLE_ADDRESS_TOPIC_PATH = Constants.EXAMPLES_DIR_PATH + "address/address_topic.yaml";
    String EXAMPLE_ARTEMIS_SINGLE_PATH = Constants.EXAMPLES_DIR_PATH + "artemis/artemis_single.yaml";
    String EXAMPLE_ARTEMIS_CLUSTER_PERSISTENCE_PATH = Constants.EXAMPLES_DIR_PATH + "artemis/artemis_cluster_persistence.yaml";
    String EXAMPLE_ARTEMIS_ADDRESS_SETTINGS_PATH = Constants.EXAMPLES_DIR_PATH + "artemis/artemis_address_settings.yaml";
    String EXAMPLE_ARTEMIS_RESOURCES_PATH = Constants.EXAMPLES_DIR_PATH + "artemis/artemis_resources.yaml";

    // Old naming style
    String OLD_INSTALL_ARTEMIS_CO_010_CRD_ARTEMIS_PATH = Constants.OPERATOR_CRDS_DIR_PATH + "broker_activemqartemis_crd.yaml";             // 010_crd_artemis.yaml
    String OLD_INSTALL_ARTEMIS_CO_020_CRD_SECURITY_PATH = Constants.OPERATOR_CRDS_DIR_PATH + "broker_activemqartemissecurity_crd.yaml";    // 020_crd_artemis_security.yaml
    String OLD_INSTALL_ARTEMIS_CO_030_CRD_ADDRESS_PATH = Constants.OPERATOR_CRDS_DIR_PATH + "broker_activemqartemisaddress_crd.yaml";      // 030_crd_artemis_address.yaml
    String OLD_INSTALL_ARTEMIS_CO_040_CRD_SCALEDOWN_PATH = Constants.OPERATOR_CRDS_DIR_PATH + "broker_activemqartemisscaledown_crd.yaml";  // 040_crd_artemis_scaledown.yaml
    String OLD_INSTALL_ARTEMIS_CO_050_SERVICE_ACCOUNT_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "service_account.yaml";                 // 050_service_account.yaml
    String OLD_INSTALL_ARTEMIS_CO_060_CLUSTER_ROLE_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "cluster_role.yaml";                       // 060_cluster_role.yaml
    String OLD_INSTALL_ARTEMIS_CO_070_CLUSTER_ROLE_BINDING_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "cluster_role_binding.yaml";       // 070_cluster_role_binding.yaml
    String OLD_INSTALL_ARTEMIS_CO_060_NAMESPACE_ROLE_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "role.yaml";                             // 060_namespace_role.yaml
    String OLD_INSTALL_ARTEMIS_CO_070_NAMESPACE_ROLE_BINDING_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "role_binding.yaml";             // 070_namespace_role_binding.yaml
    String OLD_INSTALL_ARTEMIS_CO_080_ELECTION_ROLE_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "election_role.yaml";                     // 080_election_role.yaml
    String OLD_INSTALL_ARTEMIS_CO_090_ELECTION_ROLE_BINDING_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "election_role_binding.yaml";     // 090_election_role_binding.yaml
    String OLD_INSTALL_ARTEMIS_CO_100_OPERATOR_CONFIG_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "operator_config.yaml";                 // 100_operator_config.yaml
    String OLD_INSTALL_ARTEMIS_CO_110_OPERATOR_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "operator.yaml";                               // 110_operator.yaml

    String OLD_EXAMPLE_ADDRESS_QUEUE_PATH = Constants.EXAMPLES_DIR_PATH + "address/address-queue-create.yaml";
    // TODO alternative file does not exist?
//    String OLD_EXAMPLE_ADDRESS_TOPIC_PATH = Constants.EXAMPLES_DIR_PATH + "address/address_topic.yaml";
    String OLD_EXAMPLE_ARTEMIS_SINGLE_PATH = Constants.EXAMPLES_DIR_PATH + "artemis/artemis-basic-deployment.yaml";

    // Container folder paths
    String CONTAINER_BROKER_HOME_DIR = "/home/jboss/amq-broker/";
    String CONTAINER_BROKER_HOME_ETC_DIR = CONTAINER_BROKER_HOME_DIR + "etc/";

    // Timing variables
    long DURATION_2_SECONDS = Duration.ofSeconds(2).toMillis();
    long DURATION_5_SECONDS = Duration.ofSeconds(5).toMillis();
    long DURATION_10_SECONDS = Duration.ofSeconds(10).toMillis();
    long DURATION_30_SECONDS = Duration.ofSeconds(30).toMillis();
    long DURATION_1_MINUTE = Duration.ofMinutes(1).toMillis();
    long DURATION_3_MINUTES = Duration.ofMinutes(3).toMillis();
}
