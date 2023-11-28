/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire;

import io.brokerqe.claire.database.Database;
import io.brokerqe.claire.exception.ClaireNotImplementedException;
import io.brokerqe.claire.helpers.SerializationFormat;
import io.brokerqe.claire.operator.ArtemisCloudClusterOperatorFile;
import io.brokerqe.claire.operator.ArtemisFileProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

public class EnvironmentOperator extends Environment {

    private final boolean disabledRandomNs;
    private final String testLogLevel;
    private final String olmIndexImageBundle;
    private final String olmChannel;
    private final boolean olmReleased;
    private final boolean olmLts;
    private final boolean olmInstallation;
    private final String artemisOperatorName;
    private final String artemisOperatorType;
    private String artemisVersion;
    private final ArtemisVersion artemisTestVersion;
    private final String brokerImage;
    private final String brokerInitImage;
    private final String operatorImage;
    private final String bundleImage;
    private final String testUpgradePlan;
    private final boolean projectManagedClusterOperator;
    private final String logsDirLocation;
    private final String tmpDirLocation;
    private final String keycloakVersion;
    static final Logger LOGGER = LoggerFactory.getLogger(Environment.class);
    private final KubeClient kubeClient;
    private final boolean collectTestData;
    private final int customExtraDelay;
    private final boolean serializationEnabled;
    private final String serializationDirectory;
    private final String serializationFormat;

    public EnvironmentOperator() {
        this.set(this);
        artemisVersion = System.getenv(Constants.EV_ARTEMIS_VERSION);
        testLogLevel = System.getenv(Constants.EV_TEST_LOG_LEVEL);
        logsDirLocation = System.getenv().getOrDefault(Constants.EV_LOGS_LOCATION, Constants.LOGS_DEFAULT_DIR) + Constants.FILE_SEPARATOR + TestUtils.generateTimestamp();
        tmpDirLocation = System.getenv().getOrDefault(Constants.EV_TMP_LOCATION, Constants.TMP_DEFAULT_DIR) + Constants.FILE_SEPARATOR + TestUtils.generateTimestamp();
        collectTestData = Boolean.parseBoolean(System.getenv().getOrDefault(Constants.EV_COLLECT_TEST_DATA, "true"));
        serializationEnabled = Boolean.parseBoolean(System.getenv().getOrDefault(Constants.EV_DUMP_ENABLED, "false"));
        serializationDirectory = System.getenv().getOrDefault(Constants.EV_DUMP_LOCATION, Constants.DUMP_DEFAULT_DIR) + Constants.FILE_SEPARATOR + TestUtils.generateTimestamp();
        serializationFormat = System.getenv().getOrDefault(Constants.EV_DUMP_FORMAT, Constants.DUMP_DEFAULT_TYPE);

        kubeClient = new KubeClient("default");
        disabledRandomNs = Boolean.parseBoolean(System.getenv(Constants.EV_DISABLE_RANDOM_NAMESPACES));
        customExtraDelay = Integer.parseInt(System.getenv().getOrDefault(Constants.EV_CUSTOM_EXTRA_DELAY, "0"));

        projectManagedClusterOperator = Boolean.parseBoolean(System.getenv().getOrDefault(Constants.EV_CLUSTER_OPERATOR_MANAGED, "true"));

        testUpgradePlan = System.getenv(Constants.EV_UPGRADE_PLAN);
        olmIndexImageBundle = System.getenv().getOrDefault(Constants.EV_OLM_IIB, null);
        olmChannel = System.getenv().getOrDefault(Constants.EV_OLM_CHANNEL, null);
        olmReleased = Boolean.parseBoolean(System.getenv().getOrDefault(Constants.EV_OLM_RELEASED, "false"));
        olmLts = Boolean.parseBoolean(System.getenv().getOrDefault(Constants.EV_OLM_LTS, "false"));
        olmInstallation = olmReleased || olmChannel != null && olmIndexImageBundle != null || testUpgradePlan != null;

        brokerImage = System.getenv(Constants.EV_BROKER_IMAGE);
        brokerInitImage = System.getenv(Constants.EV_BROKER_INIT_IMAGE);
        operatorImage = System.getenv(Constants.EV_OPERATOR_IMAGE);
        bundleImage = System.getenv(Constants.EV_BUNDLE_IMAGE);

        Properties projectSettings = new Properties();
        FileInputStream projectSettingsFile;
        try {
            projectSettingsFile = new FileInputStream(Constants.PROJECT_SETTINGS_PATH);
            projectSettings.load(projectSettingsFile);
            artemisOperatorName = olmInstallation ? "amq-broker" : String.valueOf(projectSettings.get("artemis.name"));
            artemisOperatorType = String.valueOf(projectSettings.get("artemis.type"));
            artemisVersion = artemisVersion == null ? String.valueOf(projectSettings.get("artemis.version")) : artemisVersion;
            // Use ENV Var, project property or default to artemisVersion
            String artemisTestVersionStr = System.getenv().getOrDefault(Constants.EV_ARTEMIS_TEST_VERSION, String.valueOf(projectSettings.get("artemis.test.version")));
            artemisTestVersionStr = artemisTestVersionStr == null || artemisTestVersionStr.equals("null") || isOlmInstallation() ? artemisVersion : artemisTestVersionStr;
            artemisTestVersion = convertArtemisVersion(artemisTestVersionStr);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        keycloakVersion = System.getProperty(Constants.EV_KEYCLOAK_VERSION, getDefaultKeycloakVersion());

        printAllUsedTestVariables();
    }

    @SuppressWarnings("checkstyle:NPathComplexity")
    private void printAllUsedTestVariables() {
        StringBuilder envVarsSB = new StringBuilder("List of all used Claire related variables:").append(Constants.LINE_SEPARATOR);
        envVarsSB.append(Constants.EV_DISABLE_RANDOM_NAMESPACES).append("=").append(disabledRandomNs).append(Constants.LINE_SEPARATOR);
        envVarsSB.append(Constants.EV_CLUSTER_OPERATOR_MANAGED).append("=").append(projectManagedClusterOperator).append(Constants.LINE_SEPARATOR);
        envVarsSB.append(Constants.EV_COLLECT_TEST_DATA).append("=").append(collectTestData).append(Constants.LINE_SEPARATOR);
        envVarsSB.append(Constants.EV_DUMP_ENABLED).append("=").append(serializationEnabled).append(Constants.LINE_SEPARATOR);

        if (testLogLevel != null) {
            envVarsSB.append(Constants.EV_TEST_LOG_LEVEL).append("=").append(testLogLevel).append(Constants.LINE_SEPARATOR);
        }
        if (artemisVersion != null) {
            envVarsSB.append(Constants.EV_ARTEMIS_VERSION).append("=").append(artemisVersion).append(Constants.LINE_SEPARATOR);
        }
        if (artemisTestVersion != null) {
            envVarsSB.append(Constants.EV_ARTEMIS_TEST_VERSION).append("=").append(artemisTestVersion).append(Constants.LINE_SEPARATOR);
        }

        envVarsSB.append("OLM_INSTALLATION").append("=").append(olmInstallation).append(Constants.LINE_SEPARATOR);
        if (olmInstallation) {
            envVarsSB.append(Constants.EV_OLM_RELEASED).append("=").append(olmReleased).append(Constants.LINE_SEPARATOR);
        }
        if (olmIndexImageBundle != null) {
            envVarsSB.append(Constants.EV_OLM_IIB).append("=").append(olmIndexImageBundle).append(Constants.LINE_SEPARATOR);
        }
        if (olmChannel != null) {
            envVarsSB.append(Constants.EV_OLM_CHANNEL).append("=").append(olmChannel).append(Constants.LINE_SEPARATOR);
        }

        if (brokerImage != null) {
            envVarsSB.append(Constants.EV_BROKER_IMAGE).append("=").append(brokerImage).append(Constants.LINE_SEPARATOR);
        }
        if (brokerInitImage != null) {
            envVarsSB.append(Constants.EV_BROKER_INIT_IMAGE).append("=").append(brokerInitImage).append(Constants.LINE_SEPARATOR);
        }
        if (operatorImage != null) {
            envVarsSB.append(Constants.EV_OPERATOR_IMAGE).append("=").append(operatorImage).append(Constants.LINE_SEPARATOR);
        }
        if (bundleImage != null) {
            envVarsSB.append(Constants.EV_BUNDLE_IMAGE).append("=").append(bundleImage).append(Constants.LINE_SEPARATOR);
        }
        if (testUpgradePlan != null) {
            envVarsSB.append(Constants.EV_UPGRADE_PLAN).append("=").append(testUpgradePlan).append(Constants.LINE_SEPARATOR);
        }
        if (logsDirLocation != null) {
            envVarsSB.append(Constants.EV_LOGS_LOCATION).append("=").append(logsDirLocation).append(Constants.LINE_SEPARATOR);
        }
        if (tmpDirLocation != null) {
            envVarsSB.append(Constants.EV_TMP_LOCATION).append("=").append(tmpDirLocation).append(Constants.LINE_SEPARATOR);
        }
        if (customExtraDelay != 0) {
            envVarsSB.append(Constants.EV_CUSTOM_EXTRA_DELAY).append("=").append(customExtraDelay).append(Constants.LINE_SEPARATOR);
            LOGGER.warn("Detected {}. All non-kubernetes default waits will be prolonged by {}s", Constants.EV_CUSTOM_EXTRA_DELAY, customExtraDelay);
        }
        if (serializationEnabled) {
            envVarsSB.append(Constants.EV_DUMP_LOCATION).append("=").append(serializationDirectory).append(Constants.LINE_SEPARATOR);
            envVarsSB.append(Constants.EV_DUMP_FORMAT).append("=").append(serializationFormat).append(Constants.LINE_SEPARATOR);

        }

        LOGGER.info(envVarsSB.toString());
    }

    @Override
    public String getArtemisVersion() {
        return artemisVersion;
    }

    @Override
    public ArtemisVersion getArtemisTestVersion() {
        return artemisTestVersion;
    }

    @Override
    public boolean isCollectTestData() {
        return collectTestData;
    }

    @Override
    public boolean isUpstreamArtemis() {
        return artemisOperatorType.equals("upstream");
    }

    @Override
    public String getTestLogLevel() {
        return testLogLevel;
    }
    @Override
    public String getLogsDirLocation() {
        return logsDirLocation;
    }

    @Override
    public String getTmpDirLocation() {
        return tmpDirLocation;
    }

    @Override
    public String getKeycloakVersion() {
        return keycloakVersion;
    }

    @Override
    public int getCustomExtraDelay() {
        // convert seconds to ms
        return customExtraDelay * 1000;
    }

    @Override
    public Database getDatabase() {
        return null;
    }

    public void checkSetProvidedImages() {
        Path operatorFile = ArtemisFileProvider.getOperatorInstallFile();

        if (brokerImage != null && !brokerImage.equals("")) {
            LOGGER.debug("[ENV] Updating {} with {}", operatorFile, brokerImage);
            ArtemisCloudClusterOperatorFile.updateImagesInOperatorFile(operatorFile, ArtemisConstants.BROKER_IMAGE_OPERATOR_PREFIX, brokerImage, artemisVersion);
        }

        if (brokerInitImage != null && !brokerInitImage.equals("")) {
            LOGGER.debug("[ENV] Updating {} with {}", operatorFile, brokerInitImage);
            ArtemisCloudClusterOperatorFile.updateImagesInOperatorFile(operatorFile, ArtemisConstants.BROKER_INIT_IMAGE_OPERATOR_PREFIX, brokerInitImage, artemisVersion);
        }

        if (operatorImage != null && !operatorImage.equals("")) {
            LOGGER.debug("[ENV] Updating {} with {}", operatorFile, operatorImage);
            ArtemisCloudClusterOperatorFile.updateImagesInOperatorFile(operatorFile, ArtemisConstants.OPERATOR_IMAGE_OPERATOR_PREFIX, operatorImage, null);
        }
    }

    public boolean isDisabledRandomNs() {
        return disabledRandomNs;
    }

    public String getArtemisOperatorName() {
        return artemisOperatorName;
    }

    public boolean isProjectManagedClusterOperator() {
        return projectManagedClusterOperator;
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
    public String getTestUpgradePlanContent() {
        if (testUpgradePlan != null) {
            return TestUtils.readFileContent(new File(testUpgradePlan));
        } else {
            throw new IllegalArgumentException(Constants.EV_UPGRADE_PLAN + " variable has not been set!");
        }
    }

    private String getDefaultKeycloakVersion() {
        if (isUpstreamArtemis()) {
            return Constants.DEFAULT_KEYCLOAK_VERSION;
        } else {
            return Constants.DEFAULT_RHSSO_VERSION;
        }
    }

    public KubeClient getKubeClient() {
        return kubeClient;
    }

    public String getOlmIndexImageBundle() {
        return olmIndexImageBundle;
    }

    public String getOlmChannel() {
        return olmChannel;
    }

    public boolean isOlmInstallation() {
        return olmInstallation;
    }

    public boolean isOlmReleased() {
        return olmReleased;
    }
    public boolean isOlmLts() {
        return olmLts;
    }
    public void setupDatabase() {
        throw new ClaireNotImplementedException("Databases on Operator are not yet supported!");
    }

    public boolean isSerializationEnabled() {
        return serializationEnabled;
    }

    public String getSerializationDirectory() {
        return serializationDirectory;
    }

    public SerializationFormat getSerializationFormat() {
        if (serializationFormat.equals(Constants.DUMP_DEFAULT_TYPE)) {
            return SerializationFormat.YAML;
        } else if (serializationFormat.equals("json")) {
            return SerializationFormat.JSON;
        } else {
            LOGGER.warn("Unknown serialization type! Defaulting to YAML format.");
            return SerializationFormat.YAML;
        }
    }
}
