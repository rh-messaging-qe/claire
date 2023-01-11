/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe;

import io.brokerqe.operator.ArtemisFileProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Environment {

    private final boolean disabledRandomNs;
    private final String testLogLevel;
    private final String artemisVersion;
    private final String brokerImage;
    private final String brokerInitImage;
    private final String operatorImage;
    private final String bundleImage;
    private final boolean projectManagedClusterOperator;
    private final String logsDirLocation;

    static final Logger LOGGER = LoggerFactory.getLogger(Environment.class);

    public Environment() {
        disabledRandomNs = Boolean.parseBoolean(System.getenv(Constants.EV_DISABLE_RANDOM_NAMESPACES));
        testLogLevel = System.getenv(Constants.EV_TEST_LOG_LEVEL);
        artemisVersion = System.getenv(Constants.EV_ARTEMIS_VERSION);
        brokerImage = System.getenv(Constants.EV_BROKER_IMAGE);
        brokerInitImage = System.getenv(Constants.EV_BROKER_INIT_IMAGE);
        operatorImage = System.getenv(Constants.EV_OPERATOR_IMAGE);
        bundleImage = System.getenv(Constants.EV_BUNDLE_IMAGE);
        logsDirLocation = System.getProperty(Constants.EV_LOGS_LOCATION, Constants.LOGS_DEFAULT_DIR);
        projectManagedClusterOperator = Boolean.parseBoolean(System.getenv().getOrDefault(Constants.EV_CLUSTER_OPERATOR_MANAGED, "true"));

        printAllUsedTestVariables();
        checkSetProvidedImages();
    }

    private void printAllUsedTestVariables() {
        StringBuilder envVarsSB = new StringBuilder("List of all used Claire related variables:").append(Constants.LINE_SEPARATOR);
        envVarsSB.append(Constants.EV_DISABLE_RANDOM_NAMESPACES).append("=").append(disabledRandomNs).append(Constants.LINE_SEPARATOR);
        envVarsSB.append(Constants.EV_CLUSTER_OPERATOR_MANAGED).append("=").append(projectManagedClusterOperator).append(Constants.LINE_SEPARATOR);

        if (testLogLevel != null) {
            envVarsSB.append(Constants.EV_TEST_LOG_LEVEL).append("=").append(testLogLevel).append(Constants.LINE_SEPARATOR);
        }
        if (artemisVersion != null) {
            envVarsSB.append(Constants.EV_ARTEMIS_VERSION).append("=").append(artemisVersion).append(Constants.LINE_SEPARATOR);
        }
        if (brokerImage != null) {
            envVarsSB.append(Constants.EV_BROKER_IMAGE).append("=").append(brokerImage).append(Constants.LINE_SEPARATOR);
        }
        if (brokerInitImage != null) {
            envVarsSB.append(Constants.EV_BROKER_INIT_IMAGE).append("=").append(brokerInitImage).append(Constants.LINE_SEPARATOR);
        }
        if (operatorImage != null) {
            envVarsSB.append(Constants.EV_BUNDLE_IMAGE).append("=").append(operatorImage).append(Constants.LINE_SEPARATOR);
        }
        if (bundleImage != null) {
            envVarsSB.append(Constants.EV_ARTEMIS_VERSION).append("=").append(bundleImage).append(Constants.LINE_SEPARATOR);
        }
        if (logsDirLocation != null) {
            envVarsSB.append(Constants.EV_LOGS_LOCATION).append("=").append(logsDirLocation).append(Constants.LINE_SEPARATOR);
        }

        LOGGER.debug(envVarsSB.toString());
    }

    private void checkSetProvidedImages() {
        String operatorFile = ArtemisFileProvider.getOperatorInstallFile();

        Path operatorFilePath = Paths.get(operatorFile);
        if (brokerImage != null && !brokerImage.equals("")) {
            LOGGER.debug("Updating {} with {}", operatorFile, brokerImage);
            TestUtils.updateImagesInOperatorFile(operatorFilePath, Constants.BROKER_IMAGE_OPERATOR_PREFIX, brokerImage, artemisVersion);
        }

        if (brokerInitImage != null && !brokerInitImage.equals("")) {
            LOGGER.debug("Updating {} with {}", operatorFile, brokerInitImage);
            TestUtils.updateImagesInOperatorFile(operatorFilePath, Constants.BROKER_INIT_IMAGE_OPERATOR_PREFIX, brokerInitImage, artemisVersion);
        }

        if (operatorImage != null && !operatorImage.equals("")) {
            LOGGER.debug("Updating {} with {}", operatorFile, operatorImage);
            TestUtils.updateImagesInOperatorFile(operatorFilePath, Constants.OPERATOR_IMAGE_OPERATOR_PREFIX, operatorImage, null);
        }
    }

    public boolean isDisabledRandomNs() {
        return disabledRandomNs;
    }

    public String getArtemisVersion() {
        return artemisVersion;
    }

    public boolean isProjectManagedClusterOperator() {
        return projectManagedClusterOperator;
    }

    public String getTestLogLevel() {
        return testLogLevel;
    }

    public String getBrokerImage() {
        return brokerImage;
    }

    public String getBrokerInitImage() {
        return brokerInitImage;
    }

    public String getOperatorImage() {
        return operatorImage;
    }

    public String getBundleImage() {
        return bundleImage;
    }

    public String getLogsDirLocation() {
        return logsDirLocation;
    }
}