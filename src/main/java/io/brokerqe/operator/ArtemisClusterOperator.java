/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.operator;

import io.brokerqe.Constants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ArtemisClusterOperator extends ActiveMQArtemisClusterOperator {

    static final String INSTALL_ARTEMIS_CO_010_CRD_ARTEMIS_PATH = Constants.OPERATOR_CRDS_DIR_PATH + "010_crd_artemis.yaml";
    static final String INSTALL_ARTEMIS_CO_020_CRD_SECURITY_PATH = Constants.OPERATOR_CRDS_DIR_PATH + "020_crd_artemis_security.yaml";
    static final String INSTALL_ARTEMIS_CO_030_CRD_ADDRESS_PATH = Constants.OPERATOR_CRDS_DIR_PATH + "030_crd_artemis_address.yaml";
    static final String INSTALL_ARTEMIS_CO_040_CRD_SCALEDOWN_PATH = Constants.OPERATOR_CRDS_DIR_PATH + "040_crd_artemis_scaledown.yaml";
    static final String INSTALL_ARTEMIS_CO_050_SERVICE_ACCOUNT_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "050_service_account.yaml";
    static final String INSTALL_ARTEMIS_CO_060_CLUSTER_ROLE_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "060_cluster_role.yaml";
    static final String INSTALL_ARTEMIS_CO_070_CLUSTER_ROLE_BINDING_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "070_cluster_role_binding.yaml";
    static final String INSTALL_ARTEMIS_CO_060_NAMESPACE_ROLE_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "060_namespace_role.yaml";
    static final String INSTALL_ARTEMIS_CO_070_NAMESPACE_ROLE_BINDING_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "070_namespace_role_binding.yaml";
    static final String INSTALL_ARTEMIS_CO_080_ELECTION_ROLE_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "080_election_role.yaml";
    static final String INSTALL_ARTEMIS_CO_090_ELECTION_ROLE_BINDING_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "090_election_role_binding.yaml";
    static final String INSTALL_ARTEMIS_CO_100_OPERATOR_CONFIG_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "100_operator_config.yaml";
    static final String INSTALL_ARTEMIS_CO_110_OPERATOR_PATH = Constants.OPERATOR_INSTALL_DIR_PATH + "110_operator.yaml";

    static final List<String> DEFAULT_OPERATOR_INSTALL_FILES = Arrays.asList(
            INSTALL_ARTEMIS_CO_010_CRD_ARTEMIS_PATH,
            INSTALL_ARTEMIS_CO_020_CRD_SECURITY_PATH,
            INSTALL_ARTEMIS_CO_030_CRD_ADDRESS_PATH,
            INSTALL_ARTEMIS_CO_040_CRD_SCALEDOWN_PATH,
            INSTALL_ARTEMIS_CO_050_SERVICE_ACCOUNT_PATH,
            INSTALL_ARTEMIS_CO_080_ELECTION_ROLE_PATH,
            INSTALL_ARTEMIS_CO_090_ELECTION_ROLE_BINDING_PATH,
            INSTALL_ARTEMIS_CO_100_OPERATOR_CONFIG_PATH,
            INSTALL_ARTEMIS_CO_110_OPERATOR_PATH
    );

    public ArtemisClusterOperator(String namespace) {
        this(namespace, false, true);
    }

    public ArtemisClusterOperator(String namespace, boolean isOlmInstallation, boolean isNamespaced) {
        super(namespace, isOlmInstallation, isNamespaced, Constants.ARTEMIS_OPERATOR_NAME);
    }

    @Override
    protected List<String> getClusteredOperatorInstallFiles() {
        List<String> temp = new ArrayList<String>(DEFAULT_OPERATOR_INSTALL_FILES);
        temp.add(INSTALL_ARTEMIS_CO_060_CLUSTER_ROLE_PATH);
        temp.add(INSTALL_ARTEMIS_CO_070_CLUSTER_ROLE_BINDING_PATH);
        return temp;
    }

    @Override
    protected List<String> getNamespacedOperatorInstallFiles() {
        List<String> temp = new ArrayList<String>(DEFAULT_OPERATOR_INSTALL_FILES);
        temp.add(INSTALL_ARTEMIS_CO_060_NAMESPACE_ROLE_PATH);
        temp.add(INSTALL_ARTEMIS_CO_070_NAMESPACE_ROLE_BINDING_PATH);
        return temp;
    }

    @Override
    public String getArtemisSingleExamplePath() {
        return Constants.EXAMPLE_ARTEMIS_SINGLE_PATH;
    }

    @Override
    public String getArtemisAddressQueueExamplePath() {
        return Constants.EXAMPLE_ADDRESS_QUEUE_PATH;
    }
}
