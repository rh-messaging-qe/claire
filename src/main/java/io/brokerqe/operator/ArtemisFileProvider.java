/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.operator;

import io.brokerqe.Constants;
import io.brokerqe.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Paths;

public class ArtemisFileProvider {

    static final Logger LOGGER = LoggerFactory.getLogger(Environment.class);

    public static String getArtemisCrdFile() {
        return getPresentFile(Constants.INSTALL_ARTEMIS_CO_010_CRD_ARTEMIS_PATH, Constants.OLD_INSTALL_ARTEMIS_CO_010_CRD_ARTEMIS_PATH);
    }

    public static String getSecurityCrdFile() {
        return getPresentFile(Constants.INSTALL_ARTEMIS_CO_020_CRD_SECURITY_PATH, Constants.OLD_INSTALL_ARTEMIS_CO_020_CRD_SECURITY_PATH);
    }

    public static String getAddressCrdFile() {
        return getPresentFile(Constants.INSTALL_ARTEMIS_CO_030_CRD_ADDRESS_PATH, Constants.OLD_INSTALL_ARTEMIS_CO_030_CRD_ADDRESS_PATH);
    }

    public static String getScaledownCrdFile() {
        return getPresentFile(Constants.INSTALL_ARTEMIS_CO_040_CRD_SCALEDOWN_PATH, Constants.OLD_INSTALL_ARTEMIS_CO_040_CRD_SCALEDOWN_PATH);
    }

    public static String getServiceAccountInstallFile() {
        return getPresentFile(Constants.INSTALL_ARTEMIS_CO_050_SERVICE_ACCOUNT_PATH, Constants.OLD_INSTALL_ARTEMIS_CO_050_SERVICE_ACCOUNT_PATH);
    }

    public static String getClusterRoleInstallFile() {
        return getPresentFile(Constants.INSTALL_ARTEMIS_CO_060_CLUSTER_ROLE_PATH, Constants.OLD_INSTALL_ARTEMIS_CO_060_CLUSTER_ROLE_PATH);
    }

    public static String getClusterRoleBindingInstallFile() {
        return getPresentFile(Constants.INSTALL_ARTEMIS_CO_070_CLUSTER_ROLE_BINDING_PATH, Constants.OLD_INSTALL_ARTEMIS_CO_070_CLUSTER_ROLE_BINDING_PATH);
    }

    public static String getNamespaceRoleInstallFile() {
        return getPresentFile(Constants.INSTALL_ARTEMIS_CO_060_NAMESPACE_ROLE_PATH, Constants.OLD_INSTALL_ARTEMIS_CO_060_NAMESPACE_ROLE_PATH);
    }

    public static String getNamespaceRoleBindingInstallFile() {
        return getPresentFile(Constants.INSTALL_ARTEMIS_CO_070_NAMESPACE_ROLE_BINDING_PATH, Constants.OLD_INSTALL_ARTEMIS_CO_070_NAMESPACE_ROLE_BINDING_PATH);
    }

    public static String getElectionRoleInstallFile() {
        return getPresentFile(Constants.INSTALL_ARTEMIS_CO_080_ELECTION_ROLE_PATH, Constants.OLD_INSTALL_ARTEMIS_CO_080_ELECTION_ROLE_PATH);
    }

    public static String getElectionRoleBindingInstallFile() {
        return getPresentFile(Constants.INSTALL_ARTEMIS_CO_090_ELECTION_ROLE_BINDING_PATH, Constants.OLD_INSTALL_ARTEMIS_CO_090_ELECTION_ROLE_BINDING_PATH);
    }

    public static String getOperatorConfigInstallFile() {
        return getPresentFile(Constants.INSTALL_ARTEMIS_CO_100_OPERATOR_CONFIG_PATH, Constants.OLD_INSTALL_ARTEMIS_CO_100_OPERATOR_CONFIG_PATH);
    }

    public static String getOperatorInstallFile() {
        return getPresentFile(Constants.INSTALL_ARTEMIS_CO_110_OPERATOR_PATH, Constants.OLD_INSTALL_ARTEMIS_CO_110_OPERATOR_PATH);
    }

    // Examples
    public static String getArtemisSingleExampleFile() {
        return getPresentFile(Constants.EXAMPLE_ARTEMIS_SINGLE_PATH, Constants.OLD_EXAMPLE_ARTEMIS_SINGLE_PATH);
    }

    public static String getAddressQueueExampleFile() {
        return getPresentFile(Constants.EXAMPLE_ADDRESS_QUEUE_PATH, Constants.OLD_EXAMPLE_ADDRESS_QUEUE_PATH);
    }

    public static String getAddressTopicExampleFile() {
        // TODO match here something newer instead of null?
        return getPresentFile(Constants.EXAMPLE_ADDRESS_TOPIC_PATH, null);
    }

    private static String getPresentFile(String newStyleFile, String oldStyleFile) {
        if (Files.exists(Paths.get(newStyleFile))) {
            return newStyleFile;
        } else if (Files.exists(Paths.get(oldStyleFile))) {
            return oldStyleFile;
        } else {
            LOGGER.error("Can't find any file! Have you ran `Makefile` properly? Exiting.");
            System.exit(2);
        }
        return null;
    }
}
