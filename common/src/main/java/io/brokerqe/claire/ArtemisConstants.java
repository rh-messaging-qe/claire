/*
 * Copyright Strimzi and Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire;

import java.net.URLEncoder;

public interface ArtemisConstants {
    // Files
    String BIN_DIR = Constants.FILE_SEPARATOR + "bin";
    String ETC_DIR = Constants.FILE_SEPARATOR + "etc";
    String DATA_DIR = Constants.FILE_SEPARATOR + "data";
    String OPT_DIR = Constants.FILE_SEPARATOR + "opt";
    String VAR_DIR = Constants.FILE_SEPARATOR + "var";
    String LIB_DIR = Constants.FILE_SEPARATOR + "lib";
    String LOG_DIR = Constants.FILE_SEPARATOR + "log";
    String TMP_DIR = Constants.FILE_SEPARATOR + "tmp";
    String AUDIT_LOG_FILE = "audit.log";
    String ARTEMIS_LOG_FILE = "artemis.log";


    String CONSOLE_STRING = "console";
    String AUTH_STRING = "auth";
    String LOGIN_STRING = "login";
    String WEBCONSOLE_URI_PREFIX = "wconsj";
    String JOLOKIA_CALL_ENDPOINT = "/console/jolokia/exec/org.apache.activemq.artemis";
    String JOLOKIA_BROKER_PARAM = ":broker=" + URLEncoder.encode("\"amq-broker\"");
    String JOLOKIA_ADDRESSETTINGS_ENDPOINT = "/getAddressSettingsAsJSON/";
    String JOLOKIA_STATUS_ENDPOINT = "/Status";
    String JOLOKIA_ORIGIN_HEADER = "http://localhost:8161";

    int CONSOLE_PORT = 8161;
    int DEFAULT_ALL_PROTOCOLS_PORT = 61616;
    int DEFAULT_AMQP_PORT = 5672;
    int DEFAULT_HORNETQ_PORT = 5445;
    int DEFAULT_JMX_PORT = 1099;
    int DEFAULT_MQTT_PORT = 1883;
    int DEFAULT_STOMP_PORT = 61613;
    int DEFAULT_WEB_CONSOLE_PORT = 8161;

    // Security
    String SASL_ANON = "ANONYMOUS";
    String SASL_PLAIN = "PLAIN";
    String SASL_EXTERNAL = "EXTERNAL";

    // Logging
    String DEFAULT_LOG_LEVEL = "INFO";

    // Roles
    String ROLE_ADMIN = "amq";
    String ROLE_SENDERS = "senders";
    String ROLE_RECEIVERS = "receivers";

    // Users
    String ADMIN_NAME = "admin";
    String ADMIN_PASS = "admin";
    String ALICE_NAME = "alice";
    String ALICE_PASS = "alice";
    String BOB_NAME = "bob";
    String BOB_PASS = "bob";
    String CHARLIE_NAME = "charlie";
    String CHARLIE_PASS = "charlie";
    String LALA_NAME = "lala";
    String LALA_PASS = "lala";

    // Address/Queue settings
    String ROUTING_TYPE_ANYCAST = "anycast";
    String ROUTING_TYPE_MULTICAST = "multicast";
    String ADDRESSSETTINGS_POLICY_DROP = "DROP";
    String ADDRESSSETTINGS_POLICY_FAIL = "FAIL";
    String ADDRESSSETTINGS_POLICY_PAGE = "PAGE";
    String ADDRESSSETTINGS_POLICY_BLOCK = "BLOCK";
    String ADDRESSETTINGS_POLICY_KILL = "KILL";
    String ADDRESSETTINGS_POLICY_NOTIFY = "NOTIFY";
    String ADDRESSSETTING_FORCE = "FORCE";
    String ADDRESSSETTING_OFF = "OFF";
    String ADDRESSSETTINGS_ROUTING_ANYCAST = "ANYCAST";
    String ADDRESSSETTINGS_ROUTING_MULTICAST = "MULTICAST";
    String ADDRESSSETING_UNIT_MPS = "MESSAGES_PER_SECOND";

    // Artemis test container
    String ARTEMIS_STRING = "artemis";
    String INSTANCE_STRING = "artemis-instance";
    String INSTANCE_DIR = "/var/lib/" + INSTANCE_STRING;
    String INSTANCE_BIN_DIR = INSTANCE_DIR + "/bin";
    String INSTALL_DIR = "artemis/artemis_install";


    // ==== Artemis operator image ====
    // Broker Container folder paths
    String CONTAINER_BROKER_HOME_DIR = "/home/jboss/amq-broker/";
    String CONTAINER_BROKER_HOME_ETC_DIR = CONTAINER_BROKER_HOME_DIR + "etc/";
    String CONTAINER_BROKER_HOME_LOG_DIR = CONTAINER_BROKER_HOME_DIR + "log/";

    // Image
    String BROKER_IMAGE_OPERATOR_PREFIX = "RELATED_IMAGE_ActiveMQ_Artemis_Broker_Kubernetes_";
    String BROKER_INIT_IMAGE_OPERATOR_PREFIX = "RELATED_IMAGE_ActiveMQ_Artemis_Broker_Init_";
    String OPERATOR_IMAGE_OPERATOR_PREFIX = "image";


    // ArtemisCloud Operator strings
    String WATCH_ALL_NAMESPACES = "*";
    String LOGGING_PROPERTIES_CONFIG_KEY = "logging.properties";
    String LOGIN_CONFIG_CONFIG_KEY = "login.config";

    // Container labels & names
    String LABEL_ACTIVEMQARTEMIS = "ActiveMQArtemis";

    // Condition & Status
    String CONDITION_TYPE_BROKER_PROPERTIES_APPLIED = "BrokerPropertiesApplied";
    String CONDITION_TYPE_DEPLOYED = "Deployed";
    String CONDITION_TYPE_READY = "Ready";
    String CONDITION_TYPE_VALID = "Valid";
    String CONDITION_TYPE_BROKER_VERSION_ALIGNED = "BrokerVersionAligned";
    String CONDITION_STATUS_READY = "AllPodsReady";
    String CONDITION_REASON_VALIDATION = "ValidationSucceded";
    String CONDITION_REASON_ALL_PODS_READY = "AllPodsReady";
    String CONDITION_REASON_APPLIED = "Applied";
    String CONDITION_REASON_APPLIED_WITH_ERROR = "AppliedWithError";
    String CONDITION_TRUE = "True";
    String CONDITION_FALSE = "False";
    String CONDITION_REASON_OUT_OF_SYNC = "OutOfSync";
    String CONDITION_REASON_INVALID_EXTRA_MOUNT = "InvalidExtraMount";
    String CONDITION_REASON_RESOURCE_ERROR = "ResourceError";
    String CONDITION_REASON_WAITING_FOR_ALL_CONDITIONS = "WaitingForAllConditions";
    String CONDITION_REASON_ACCEPTOR_DUPLICATE = "DuplicateAcceptorPort";

    // New naming style
    String INSTALL_ARTEMIS_CO_010_CRD_ARTEMIS_PATH = Constants.OPERATOR_CRDS_DIR_PATH + "crd_artemis.yaml";
    String INSTALL_ARTEMIS_CO_020_CRD_SECURITY_PATH = Constants.OPERATOR_CRDS_DIR_PATH + "crd_artemis_security.yaml";
    String INSTALL_ARTEMIS_CO_030_CRD_ADDRESS_PATH = Constants.OPERATOR_CRDS_DIR_PATH + "crd_artemis_address.yaml";
    String INSTALL_ARTEMIS_CO_040_CRD_SCALEDOWN_PATH = Constants.OPERATOR_CRDS_DIR_PATH + "crd_artemis_scaledown.yaml";
    String INSTALL_ARTEMIS_CO_050_SERVICE_ACCOUNT_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "service_account.yaml";
    String INSTALL_ARTEMIS_CO_060_CLUSTER_ROLE_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "cluster_role.yaml";
    String INSTALL_ARTEMIS_CO_070_CLUSTER_ROLE_BINDING_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "cluster_role_binding.yaml";
    String INSTALL_ARTEMIS_CO_060_NAMESPACE_ROLE_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "namespace_role.yaml";
    String INSTALL_ARTEMIS_CO_070_NAMESPACE_ROLE_BINDING_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "namespace_role_binding.yaml";
    String INSTALL_ARTEMIS_CO_080_ELECTION_ROLE_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "election_role.yaml";
    String INSTALL_ARTEMIS_CO_090_ELECTION_ROLE_BINDING_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "election_role_binding.yaml";
    String INSTALL_ARTEMIS_CO_110_OPERATOR_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "operator.yaml";
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
    String OLD_INSTALL_ARTEMIS_CO_110_OPERATOR_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "operator.yaml";                               // 110_operator.yaml
    String OLD_EXAMPLE_ADDRESS_QUEUE_PATH = Constants.EXAMPLES_DIR_PATH + "address/address-queue-create.yaml";
    // TODO alternative file does not exist?
//    String OLD_EXAMPLE_ADDRESS_TOPIC_PATH = Constants.EXAMPLES_DIR_PATH + "address/address_topic.yaml";
    String OLD_EXAMPLE_ARTEMIS_SINGLE_PATH = Constants.EXAMPLES_DIR_PATH + "artemis/artemis-basic-deployment.yaml";


    // Log Strings
    String IS_LIVE_LOG_MSG = " INFO  [org.apache.activemq.artemis.core.server] AMQ221007: Server is now";
    String USING_CUSTOM_LOG_MSG = "There is a custom logger configuration defined in JAVA_ARGS_APPEND: -Dlog4j2.configurationFile=";
    String USING_DEFAULT_LOG_MSG = "Using default logging configuration(console only)";
    String LOG_PATTERN_FAILED_AUTH_304 = "AMQ601716: User .* failed authentication, reason: 304";
    String LOG_EXCEPTION = "java.lang.Exception";
    String LOG_EXCEPTION_CAUSE = "Caused by: ";

    // Audit Log patterns
    String LOG_AUDIT_AUTHENTICATION_SUCC_PATTERN = ".* AMQ601715: User %s\\(%s\\)@.* successfully authenticated";
    String LOG_AUDIT_AUTHENTICATION_FAIL_PRODUCE_PATTERN = ".* AMQ601264: User %s\\(%s\\).* gets security check failure.*AMQ229032: User: %s does not have permission='SEND' on address %s.*";
    String LOG_AUDIT_AUTHENTICATION_FAIL_CONSUME_PATTERN = ".* AMQ601264: User %s\\(%s\\).* gets security check failure.*AMQ229213: User: %s does not have permission='CONSUME' for queue %s on address %s.*";
    // 2023-10-18 09:07:00,454 [AUDIT](Thread-12 (activemq-netty-threads)) AMQ601264: User anonymous@192.168.0.2:59866 gets security check failure: ActiveMQSecurityException[errorType=SECURITY_EXCEPTION message=AMQ229213: User: alice does not have permission='CONSUME' for queue myQueue on address myAddress]
    String LOG_AUDIT_CREATE_ADDRESS_PATTERN_CORE = ".* AMQ601262: User %s\\(%s\\).* is creating address on target resource:.*with parameters:.*%s::%s.*";
    String LOG_AUDIT_CREATE_ADDRESS_PATTERN_AMQP = ".* AMQ601262: User %s\\(%s\\)@.* is creating address on target resource:.* with parameters:.*name=%s.*";
    String LOG_AUDIT_CREATE_QUEUE_PATTERN = ".* AMQ601065: User %s\\(%s\\).*is creating a queue on target resource:.*with parameters.* name=myQueue.*address=myAddress.*";
    String LOG_AUDIT_SENT_MESSAGE_PATTERN = ".* AMQ601500: User %s\\(%s\\)@.* sent a message .* address=%s::%s.*";
    String LOG_AUDIT_RECEIVED_MESSAGE_PATTERN = ".* AMQ601501: User %s\\(%s\\)@.* is consuming a message from %s.*";
}
