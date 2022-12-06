/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.operator;

import io.brokerqe.Constants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AMQClusterOperator extends ActiveMQArtemisClusterOperator {

    static final List<String> DEFAULT_OPERATOR_INSTALL_FILES = Arrays.asList(
            Constants.INSTALL_ARTEMIS_CO_010_CRD_ARTEMIS_PATH,
            Constants.INSTALL_ARTEMIS_CO_020_CRD_SECURITY_PATH,
            Constants.INSTALL_ARTEMIS_CO_030_CRD_ADDRESS_PATH,
            Constants.INSTALL_ARTEMIS_CO_040_CRD_SCALEDOWN_PATH,
            Constants.INSTALL_ARTEMIS_CO_050_SERVICE_ACCOUNT_PATH,
            Constants.INSTALL_ARTEMIS_CO_080_ELECTION_ROLE_PATH,
            Constants.INSTALL_ARTEMIS_CO_090_ELECTION_ROLE_BINDING_PATH,
            Constants.INSTALL_ARTEMIS_CO_100_OPERATOR_CONFIG_PATH,
            Constants.INSTALL_ARTEMIS_CO_110_OPERATOR_PATH
    );

    static final String EXAMPLE_ADDRESS_QUEUE_PATH = Constants.EXAMPLES_DIR_PATH + "address/address-queue-create.yaml";
    static final String EXAMPLE_ARTEMIS_SINGLE_PATH = Constants.EXAMPLES_DIR_PATH + "artemis/artemis-basic-deployment.yaml";

    public AMQClusterOperator(String namespace) {
        this(namespace, false, true, Constants.AMQ_OPERATOR_NAME);
    }

    public AMQClusterOperator(String namespace, boolean isOlmInstallation, boolean isNamespaced, String operatorName) {
        super(namespace, isOlmInstallation, isNamespaced, operatorName);
    }

    @Override
    protected List<String> getClusteredOperatorInstallFiles() {
        List<String> temp = new ArrayList<String>(DEFAULT_OPERATOR_INSTALL_FILES);
        temp.add(Constants.INSTALL_ARTEMIS_CO_060_CLUSTER_ROLE_PATH);
        temp.add(Constants.INSTALL_ARTEMIS_CO_070_CLUSTER_ROLE_BINDING_PATH);
        return temp;
    }

    @Override
    protected List<String> getNamespacedOperatorInstallFiles() {
        List<String> temp = new ArrayList<String>(DEFAULT_OPERATOR_INSTALL_FILES);
        temp.add(Constants.INSTALL_ARTEMIS_CO_060_NAMESPACE_ROLE_PATH);
        temp.add(Constants.INSTALL_ARTEMIS_CO_070_NAMESPACE_ROLE_BINDING_PATH);
        return temp;
    }

    @Override
    public String getArtemisSingleExamplePath() {
        return EXAMPLE_ARTEMIS_SINGLE_PATH;
    }

    @Override
    public String getArtemisAddressQueueExamplePath() {
        return EXAMPLE_ADDRESS_QUEUE_PATH;
    }

}
