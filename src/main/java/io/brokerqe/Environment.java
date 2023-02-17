/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe;

import io.brokerqe.operator.ArtemisFileProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

public class Environment {

    private final boolean disabledRandomNs;
    private final String testLogLevel;
    private String artemisVersion;
    private final ArtemisVersion artemisTestVersion;
    private final String brokerImage;
    private final String brokerInitImage;
    private final String operatorImage;
    private final String bundleImage;
    private final boolean projectManagedClusterOperator;
    private final String logsDirLocation;
    private final String keycloakVersion;
    static final Logger LOGGER = LoggerFactory.getLogger(Environment.class);
    private final KubeClient kubeClient;
    private final boolean collectTestData;

    public Environment() {
        disabledRandomNs = Boolean.parseBoolean(System.getenv(Constants.EV_DISABLE_RANDOM_NAMESPACES));
        testLogLevel = System.getenv(Constants.EV_TEST_LOG_LEVEL);
        artemisVersion = System.getenv(Constants.EV_ARTEMIS_VERSION);
        brokerImage = System.getenv(Constants.EV_BROKER_IMAGE);
        brokerInitImage = System.getenv(Constants.EV_BROKER_INIT_IMAGE);
        operatorImage = System.getenv(Constants.EV_OPERATOR_IMAGE);
        bundleImage = System.getenv(Constants.EV_BUNDLE_IMAGE);
        logsDirLocation = System.getProperty(Constants.EV_LOGS_LOCATION, Constants.LOGS_DEFAULT_DIR) + Constants.FILE_SEPARATOR + createArchiveName();
        projectManagedClusterOperator = Boolean.parseBoolean(System.getenv().getOrDefault(Constants.EV_CLUSTER_OPERATOR_MANAGED, "true"));
        collectTestData = Boolean.parseBoolean(System.getenv().getOrDefault(Constants.EV_COLLECT_TEST_DATA, "true"));
        kubeClient = new KubeClient("default");
        keycloakVersion = System.getProperty(Constants.EV_KEYCLOAK_VERSION, getDefaultKeycloakVersion());

        Properties projectSettings = new Properties();
        FileInputStream projectSettingsFile;
        try {
            projectSettingsFile = new FileInputStream(Constants.PROJECT_SETTINGS_PATH);
            projectSettings.load(projectSettingsFile);
            artemisVersion = artemisVersion == null ? String.valueOf(projectSettings.get("artemis.version")) : artemisVersion;
            // Use ENV Var, project property or default to artemisVersion
            String artemisTestVersionStr = System.getenv().getOrDefault(Constants.EV_ARTEMIS_TEST_VERSION, String.valueOf(projectSettings.get("artemis.test.version")));
            artemisTestVersionStr = artemisTestVersionStr == null || artemisTestVersionStr.equals("null") ? artemisVersion : artemisTestVersionStr;
            artemisTestVersion = convertArtemisVersion(artemisTestVersionStr);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        printAllUsedTestVariables();
        checkSetProvidedImages();
    }

    private void printAllUsedTestVariables() {
        StringBuilder envVarsSB = new StringBuilder("List of all used Claire related variables:").append(Constants.LINE_SEPARATOR);
        envVarsSB.append(Constants.EV_DISABLE_RANDOM_NAMESPACES).append("=").append(disabledRandomNs).append(Constants.LINE_SEPARATOR);
        envVarsSB.append(Constants.EV_CLUSTER_OPERATOR_MANAGED).append("=").append(projectManagedClusterOperator).append(Constants.LINE_SEPARATOR);
        envVarsSB.append(Constants.EV_COLLECT_TEST_DATA).append("=").append(collectTestData).append(Constants.LINE_SEPARATOR);

        if (testLogLevel != null) {
            envVarsSB.append(Constants.EV_TEST_LOG_LEVEL).append("=").append(testLogLevel).append(Constants.LINE_SEPARATOR);
        }
        if (artemisVersion != null) {
            envVarsSB.append(Constants.EV_ARTEMIS_VERSION).append("=").append(artemisVersion).append(Constants.LINE_SEPARATOR);
        }
        if (artemisTestVersion != null) {
            envVarsSB.append(Constants.EV_ARTEMIS_TEST_VERSION).append("=").append(artemisTestVersion).append(Constants.LINE_SEPARATOR);
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

        LOGGER.info(envVarsSB.toString());
    }

    private void checkSetProvidedImages() {
        Path operatorFile = ArtemisFileProvider.getOperatorInstallFile();

        if (brokerImage != null && !brokerImage.equals("")) {
            LOGGER.debug("[ENV] Updating {} with {}", operatorFile, brokerImage);
            TestUtils.updateImagesInOperatorFile(operatorFile, Constants.BROKER_IMAGE_OPERATOR_PREFIX, brokerImage, artemisVersion);
        }

        if (brokerInitImage != null && !brokerInitImage.equals("")) {
            LOGGER.debug("[ENV] Updating {} with {}", operatorFile, brokerInitImage);
            TestUtils.updateImagesInOperatorFile(operatorFile, Constants.BROKER_INIT_IMAGE_OPERATOR_PREFIX, brokerInitImage, artemisVersion);
        }

        if (operatorImage != null && !operatorImage.equals("")) {
            LOGGER.debug("[ENV] Updating {} with {}", operatorFile, operatorImage);
            TestUtils.updateImagesInOperatorFile(operatorFile, Constants.OPERATOR_IMAGE_OPERATOR_PREFIX, operatorImage, null);
        }
    }

    public boolean isDisabledRandomNs() {
        return disabledRandomNs;
    }

    public String getArtemisVersion() {
        return artemisVersion;
    }

    public ArtemisVersion getArtemisTestVersion() {
        return artemisTestVersion;
    }

    public boolean isProjectManagedClusterOperator() {
        return projectManagedClusterOperator;
    }

    public boolean isCollectTestData() {
        return collectTestData;
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

    public String getKeycloakVersion() {
        return keycloakVersion;
    }

    private String getDefaultKeycloakVersion() {
        if (kubeClient.getKubernetesPlatform().equals(KubernetesPlatform.OPENSHIFT)) {
            return Constants.DEFAULT_RHSSO_VERSION;
        } else {
            return Constants.DEFAULT_KEYCLOAK_VERSION;
        }
    }

    public KubeClient getKubeClient() {
        return kubeClient;
    }

    private static String createArchiveName() {
        LocalDateTime date = LocalDateTime.now();
        String archiveName = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss"));
        LOGGER.debug(archiveName);
        return archiveName;
    }

    public ArtemisVersion convertArtemisVersion(String version) {
        ArtemisVersion versionRet = null;
        if (version.equals("main") || version.equals("upstream")) {
            return ArtemisVersion.values()[ArtemisVersion.values().length - 1];
        }
        // TODO: temporary downstream workaround
        if (version.startsWith("7.11")) {
            return ArtemisVersion.VERSION_2_28;
        }
        if (version.startsWith("7.10")) {
            return ArtemisVersion.VERSION_2_21;
        }

        String versionSplitted = version.replace(".", "");
        int dotsCount = version.length() - versionSplitted.length();
        if (dotsCount <= 2) {
            try {
                versionRet = ArtemisVersion.getByOrdinal(Integer.parseInt(versionSplitted));
            } catch (RuntimeException e) {
                LOGGER.error("[ENV] Provided unknown {} value {}!", Constants.EV_ARTEMIS_TEST_VERSION, version);
                throw new IllegalArgumentException("Unknown " + Constants.EV_ARTEMIS_TEST_VERSION + " version: " + version);
            }
        } else {
            LOGGER.error("[ENV] Provided incorrect {} value {}! Exiting.", Constants.EV_ARTEMIS_TEST_VERSION, version);
            throw new IllegalArgumentException("Unknown " + Constants.EV_ARTEMIS_TEST_VERSION + " version: " + version);
        }
        return versionRet;
    }
}
