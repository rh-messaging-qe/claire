/*
 * Copyright Strimzi and Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe;

import java.time.Duration;

public interface Constants {

    // Platform related strings
    String NEW_LINE_SEPARATOR = System.getProperty("line.separator");

    // Project related strings
    String PROJECT_TYPE_ARTEMIS = "activemq-artemis";
    String PROJECT_TYPE_AMQ = "amq-broker";
    String PROJECT_TYPE_KEY = "project.type";
    String PROJECT_CO_MANAGE_KEY = "project.cluster_operator.manage";

    // Artemis Operator strings
    String CRD_ACTIVEMQ_ARTEMIS_GROUP = "broker.amq.io";
    // CRDs needed for typeless usage
    String CRD_ACTIVEMQ_ARTEMIS = "activemqartemises" + "." + CRD_ACTIVEMQ_ARTEMIS_GROUP;
    String CRD_ACTIVEMQ_ARTEMIS_ADDRESS = "activemqartemisaddresses" + "." + CRD_ACTIVEMQ_ARTEMIS_GROUP;
    String CRD_ACTIVEMQ_ARTEMIS_SECURITY = "activemqartemissecurities" + "." + CRD_ACTIVEMQ_ARTEMIS_GROUP;
    String CRD_ACTIVEMQ_ARTEMIS_SCALEDOWN = "activemqartemisscaledowns" + "." + CRD_ACTIVEMQ_ARTEMIS_GROUP;

    String ARTEMIS_OPERATOR_NAME = "activemq-artemis-controller-manager";
    String AMQ_OPERATOR_NAME = "amq-broker-controller-manager";

    String WATCH_ALL_NAMESPACES = "*";

    // Files
    String PROJECT_USER_DIR = System.getProperty("user.dir");
    String PROJECT_SETTINGS_PATH = PROJECT_USER_DIR + "/artemis/project-settings.properties";
    String OPERATOR_CRDS_DIR_PATH = PROJECT_USER_DIR + "/artemis/crds/";
    String OPERATOR_INSTALL_DIR_PATH = PROJECT_USER_DIR + "/artemis/install/";
    String EXAMPLES_DIR_PATH = PROJECT_USER_DIR + "/artemis/examples/";

    String EXAMPLE_ADDRESS_QUEUE_PATH = EXAMPLES_DIR_PATH + "address/address_queue.yaml";
    String EXAMPLE_ADDRESS_TOPIC_PATH = EXAMPLES_DIR_PATH + "address/address_topic.yaml";
    String EXAMPLE_ARTEMIS_SINGLE_PATH = EXAMPLES_DIR_PATH + "artemis/artemis_single.yaml";

    String INSTALL_ARTEMIS_CO_010_CRD_ARTEMIS_PATH = OPERATOR_CRDS_DIR_PATH + "broker_activemqartemis_crd.yaml";             // 010_crd_artemis.yaml
    String INSTALL_ARTEMIS_CO_020_CRD_SECURITY_PATH = OPERATOR_CRDS_DIR_PATH + "broker_activemqartemissecurity_crd.yaml";    // 020_crd_artemis_security.yaml
    String INSTALL_ARTEMIS_CO_030_CRD_ADDRESS_PATH = OPERATOR_CRDS_DIR_PATH + "broker_activemqartemisaddress_crd.yaml";      // 030_crd_artemis_address.yaml
    String INSTALL_ARTEMIS_CO_040_CRD_SCALEDOWN_PATH = OPERATOR_CRDS_DIR_PATH + "broker_activemqartemisscaledown_crd.yaml";  // 040_crd_artemis_scaledown.yaml
    String INSTALL_ARTEMIS_CO_050_SERVICE_ACCOUNT_PATH = OPERATOR_INSTALL_DIR_PATH + "service_account.yaml";                 // 050_service_account.yaml
    String INSTALL_ARTEMIS_CO_060_CLUSTER_ROLE_PATH = OPERATOR_INSTALL_DIR_PATH + "cluster_role.yaml";                       // 060_cluster_role.yaml
    String INSTALL_ARTEMIS_CO_070_CLUSTER_ROLE_BINDING_PATH = OPERATOR_INSTALL_DIR_PATH + "cluster_role_binding.yaml";       // 070_cluster_role_binding.yaml
    String INSTALL_ARTEMIS_CO_060_NAMESPACE_ROLE_PATH = OPERATOR_INSTALL_DIR_PATH + "role.yaml";                             // 060_namespace_role.yaml
    String INSTALL_ARTEMIS_CO_070_NAMESPACE_ROLE_BINDING_PATH = OPERATOR_INSTALL_DIR_PATH + "role_binding.yaml";             // 070_namespace_role_binding.yaml
    String INSTALL_ARTEMIS_CO_080_ELECTION_ROLE_PATH = OPERATOR_INSTALL_DIR_PATH + "election_role.yaml";                     // 080_election_role.yaml
    String INSTALL_ARTEMIS_CO_090_ELECTION_ROLE_BINDING_PATH = OPERATOR_INSTALL_DIR_PATH + "election_role_binding.yaml";     // 090_election_role_binding.yaml
    String INSTALL_ARTEMIS_CO_100_OPERATOR_CONFIG_PATH = OPERATOR_INSTALL_DIR_PATH + "operator_config.yaml";                 // 100_operator_config.yaml
    String INSTALL_ARTEMIS_CO_110_OPERATOR_PATH = OPERATOR_INSTALL_DIR_PATH + "operator.yaml";                               // 110_operator.yaml



    // Timing variables
    long DURATION_5_SECONDS = Duration.ofSeconds(5).toMillis();
    long DURATION_2_SECONDS = Duration.ofSeconds(2).toMillis();
    long DURATION_3_MINUTES = Duration.ofMinutes(3).toMillis();
}
