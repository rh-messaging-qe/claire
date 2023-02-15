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
import java.nio.file.Path;
import java.nio.file.Paths;

public class ArtemisFileProvider {

    static final Logger LOGGER = LoggerFactory.getLogger(Environment.class);

    public static Path getArtemisCrdFile() {
        return getPresentFile(Constants.INSTALL_ARTEMIS_CO_010_CRD_ARTEMIS_PATH, Constants.OLD_INSTALL_ARTEMIS_CO_010_CRD_ARTEMIS_PATH);
    }

    public static Path getSecurityCrdFile() {
        return getPresentFile(Constants.INSTALL_ARTEMIS_CO_020_CRD_SECURITY_PATH, Constants.OLD_INSTALL_ARTEMIS_CO_020_CRD_SECURITY_PATH);
    }

    public static Path getAddressCrdFile() {
        return getPresentFile(Constants.INSTALL_ARTEMIS_CO_030_CRD_ADDRESS_PATH, Constants.OLD_INSTALL_ARTEMIS_CO_030_CRD_ADDRESS_PATH);
    }

    public static Path getScaledownCrdFile() {
        return getPresentFile(Constants.INSTALL_ARTEMIS_CO_040_CRD_SCALEDOWN_PATH, Constants.OLD_INSTALL_ARTEMIS_CO_040_CRD_SCALEDOWN_PATH);
    }

    public static Path getServiceAccountInstallFile() {
        return getPresentFile(Constants.INSTALL_ARTEMIS_CO_050_SERVICE_ACCOUNT_PATH, Constants.OLD_INSTALL_ARTEMIS_CO_050_SERVICE_ACCOUNT_PATH);
    }

    public static Path getClusterRoleInstallFile() {
        return getPresentFile(Constants.INSTALL_ARTEMIS_CO_060_CLUSTER_ROLE_PATH, Constants.OLD_INSTALL_ARTEMIS_CO_060_CLUSTER_ROLE_PATH);
    }

    public static Path getClusterRoleBindingInstallFile() {
        return getPresentFile(Constants.INSTALL_ARTEMIS_CO_070_CLUSTER_ROLE_BINDING_PATH, Constants.OLD_INSTALL_ARTEMIS_CO_070_CLUSTER_ROLE_BINDING_PATH);
    }

    public static Path getNamespaceRoleInstallFile() {
        return getPresentFile(Constants.INSTALL_ARTEMIS_CO_060_NAMESPACE_ROLE_PATH, Constants.OLD_INSTALL_ARTEMIS_CO_060_NAMESPACE_ROLE_PATH);
    }

    public static Path getNamespaceRoleBindingInstallFile() {
        return getPresentFile(Constants.INSTALL_ARTEMIS_CO_070_NAMESPACE_ROLE_BINDING_PATH, Constants.OLD_INSTALL_ARTEMIS_CO_070_NAMESPACE_ROLE_BINDING_PATH);
    }

    public static Path getElectionRoleInstallFile() {
        return getPresentFile(Constants.INSTALL_ARTEMIS_CO_080_ELECTION_ROLE_PATH, Constants.OLD_INSTALL_ARTEMIS_CO_080_ELECTION_ROLE_PATH);
    }

    public static Path getElectionRoleBindingInstallFile() {
        return getPresentFile(Constants.INSTALL_ARTEMIS_CO_090_ELECTION_ROLE_BINDING_PATH, Constants.OLD_INSTALL_ARTEMIS_CO_090_ELECTION_ROLE_BINDING_PATH);
    }

    public static Path getOperatorConfigInstallFile() {
        return getPresentFile(Constants.INSTALL_ARTEMIS_CO_100_OPERATOR_CONFIG_PATH, Constants.OLD_INSTALL_ARTEMIS_CO_100_OPERATOR_CONFIG_PATH);
    }

    public static Path getOperatorInstallFile() {
        return getPresentFile(Constants.INSTALL_ARTEMIS_CO_110_OPERATOR_PATH, Constants.OLD_INSTALL_ARTEMIS_CO_110_OPERATOR_PATH);
    }

    // Examples
    public static Path getArtemisSingleExampleFile() {
        return getPresentFile(Constants.EXAMPLE_ARTEMIS_SINGLE_PATH, Constants.OLD_EXAMPLE_ARTEMIS_SINGLE_PATH);
    }

    public static Path getAddressQueueExampleFile() {
        return getPresentFile(Constants.EXAMPLE_ADDRESS_QUEUE_PATH, Constants.OLD_EXAMPLE_ADDRESS_QUEUE_PATH);
    }

    public static Path getAddressTopicExampleFile() {
        // TODO match here something newer instead of null?
        return getPresentFile(Constants.EXAMPLE_ADDRESS_TOPIC_PATH, null);
    }

    private static Path getPresentFile(String newStyleFile, String oldStyleFile) {
        Path newPath = Paths.get(newStyleFile);
        Path oldPath = Paths.get(oldStyleFile);
        if (Files.exists(newPath)) {
            return newPath;
        } else if (Files.exists(oldPath)) {
            return oldPath;
        } else {
            LOGGER.error("Can't find any file! Have you ran `Makefile` properly? Exiting.");
            System.exit(2);
        }
        return null;
    }
}
