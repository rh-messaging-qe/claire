/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire;

import io.brokerqe.claire.database.Database;
import io.brokerqe.claire.exception.ClaireNotImplementedException;
import io.brokerqe.claire.exception.ClaireRuntimeException;
import io.brokerqe.claire.helpers.SerializationFormat;
import io.brokerqe.claire.operator.ArtemisCloudClusterOperatorFile;
import io.brokerqe.claire.operator.ArtemisFileProvider;
import io.fabric8.openshift.api.model.operatorhub.lifecyclemanager.v1.PackageManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private List<String> kubeContexts;
    private String kubeCredentials;
    private String artemisVersion;
    private final ArtemisVersion artemisTestVersion;
    private final String brokerImage;
    private final String brokerInitImage;
    private final String operatorImage;
    private final String bundleImage;
    private final boolean projectManagedClusterOperator;
    private final String logsDirLocation;
    private final String tmpDirLocation;
    private String keycloakOperatorName;
    private String keycloakVersion;
    private String keycloakChannel;
    static final Logger LOGGER = LoggerFactory.getLogger(Environment.class);
    private Map<String, KubeClient> kubeClients;
    private final boolean collectTestData;
    private boolean teardownEnv = true;
    private boolean playwrightDebug;
    private final int customExtraDelay;
    private final boolean serializationEnabled;
    private final String serializationDirectory;
    private final String serializationFormat;
    private PackageManifest pm;
    private KubernetesVersion kubernetesVersion;

    protected EnvironmentOperator() {
        this.set(this);
        String initialTimestamp = TestUtils.generateTimestamp();
        initializeKubeContexts(System.getenv().getOrDefault(Constants.EV_KUBE_CONTEXT, null));
        kubeCredentials = System.getenv().getOrDefault(Constants.EV_KUBE_CREDENTIALS, null);
        artemisVersion = System.getenv(Constants.EV_ARTEMIS_VERSION);
        testLogLevel = System.getenv(Constants.EV_TEST_LOG_LEVEL);
        logsDirLocation = System.getenv().getOrDefault(Constants.EV_LOGS_LOCATION, Constants.LOGS_DEFAULT_DIR) + Constants.FILE_SEPARATOR + initialTimestamp;
        tmpDirLocation = System.getenv().getOrDefault(Constants.EV_TMP_LOCATION, Constants.TMP_DEFAULT_DIR) + Constants.FILE_SEPARATOR + initialTimestamp;
        collectTestData = Boolean.parseBoolean(System.getenv().getOrDefault(Constants.EV_COLLECT_TEST_DATA, "true"));
        serializationEnabled = Boolean.parseBoolean(System.getenv().getOrDefault(Constants.EV_DUMP_ENABLED, "false"));
        serializationDirectory = System.getenv().getOrDefault(Constants.EV_DUMP_LOCATION, Constants.DUMP_DEFAULT_DIR) + Constants.FILE_SEPARATOR + initialTimestamp;
        serializationFormat = System.getenv().getOrDefault(Constants.EV_DUMP_FORMAT, Constants.DUMP_DEFAULT_TYPE);
        teardownEnv = Boolean.parseBoolean(System.getenv().getOrDefault(Constants.EV_TEARDOWN, "true"));
        playwrightDebug = Boolean.parseBoolean(System.getenv().getOrDefault(Constants.EV_PLAYWRIGHT_DEBUG, "false"));

        disabledRandomNs = Boolean.parseBoolean(System.getenv(Constants.EV_DISABLE_RANDOM_NAMESPACES));
        customExtraDelay = Integer.parseInt(System.getenv().getOrDefault(Constants.EV_CUSTOM_EXTRA_DELAY, "0"));

        projectManagedClusterOperator = Boolean.parseBoolean(System.getenv().getOrDefault(Constants.EV_CLUSTER_OPERATOR_MANAGED, "true"));

        testUpgradePlan = System.getenv(Constants.EV_UPGRADE_PLAN);
        testUpgradePackageManifest = System.getenv(Constants.EV_UPGRADE_PACKAGE_MANIFEST);
        olmIndexImageBundle = System.getenv().getOrDefault(Constants.EV_OLM_IIB, null);
        olmChannel = System.getenv().getOrDefault(Constants.EV_OLM_CHANNEL, null);
        olmReleased = Boolean.parseBoolean(System.getenv().getOrDefault(Constants.EV_OLM_RELEASED, "false"));
        olmLts = Boolean.parseBoolean(System.getenv().getOrDefault(Constants.EV_OLM_LTS, "false"));
        olmInstallation = olmReleased || olmChannel != null && olmIndexImageBundle != null || testUpgradePlan != null || testUpgradePackageManifest != null;

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

        printAllUsedTestVariables();
    }

    @SuppressWarnings("checkstyle:NPathComplexity")
    private void printAllUsedTestVariables() {
        StringBuilder envVarsSB = new StringBuilder("List of all used Claire related variables:").append(Constants.LINE_SEPARATOR);
        envVarsSB.append(Constants.EV_KUBE_CONTEXT).append("=").append(String.join(" ", kubeContexts)).append(Constants.LINE_SEPARATOR);
        envVarsSB.append(Constants.EV_DISABLE_RANDOM_NAMESPACES).append("=").append(disabledRandomNs).append(Constants.LINE_SEPARATOR);
        envVarsSB.append(Constants.EV_CLUSTER_OPERATOR_MANAGED).append("=").append(projectManagedClusterOperator).append(Constants.LINE_SEPARATOR);
        envVarsSB.append(Constants.EV_COLLECT_TEST_DATA).append("=").append(collectTestData).append(Constants.LINE_SEPARATOR);
        envVarsSB.append(Constants.EV_DUMP_ENABLED).append("=").append(serializationEnabled).append(Constants.LINE_SEPARATOR);
        envVarsSB.append(Constants.EV_TEARDOWN).append("=").append(teardownEnv).append(Constants.LINE_SEPARATOR);

        if (kubeCredentials != null) {
            envVarsSB.append(Constants.EV_KUBE_CREDENTIALS).append("=").append(kubeCredentials).append(Constants.LINE_SEPARATOR);
        }
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
        if (testUpgradePackageManifest != null) {
            envVarsSB.append(Constants.EV_UPGRADE_PACKAGE_MANIFEST).append("=").append(testUpgradePackageManifest).append(Constants.LINE_SEPARATOR);
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
        if (playwrightDebug) {
            envVarsSB.append(Constants.EV_PLAYWRIGHT_DEBUG).append("=").append(playwrightDebug).append(Constants.LINE_SEPARATOR);
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
    public boolean isTeardownEnv() {
        return teardownEnv;
    }

    @Override
    public boolean isPlaywrightDebug() {
        return playwrightDebug;
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

    public String getKeycloakOperatorName() {
        if (keycloakOperatorName == null || keycloakOperatorName.isEmpty() || keycloakOperatorName.isBlank()) {
            keycloakOperatorName = System.getenv().getOrDefault(Constants.EV_KEYCLOAK_OPERATOR_NAME, getDefaultKeycloakOperatorName());
        }
        return keycloakOperatorName;
    }

    @Override
    public String getKeycloakVersion() {
        getKeycloakChannel();
        if (keycloakVersion == null || keycloakVersion.isEmpty() || keycloakVersion.isBlank()) {
            keycloakVersion = System.getenv().getOrDefault(Constants.EV_KEYCLOAK_VERSION, getDefaultKeycloakVersion(keycloakChannel));
        }
        return keycloakVersion;
    }

    public String getKeycloakChannel() {
        if (keycloakChannel == null || keycloakChannel.isEmpty() || keycloakChannel.isBlank()) {
            keycloakChannel = System.getenv().getOrDefault(Constants.EV_KEYCLOAK_CHANNEL, getDefaultKeycloakChannel());
        }
        return keycloakChannel;
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

    private void initializeKubeContexts(String contexts) {
        kubeClients = new LinkedHashMap<>(1);
        if (contexts == null) {
            // return default
            kubeContexts = List.of("default");
            kubeClients.put("default", new KubeClient(null));
            return;
        }
        if (!contexts.contains(",")) {
            // single context
            kubeContexts = List.of(contexts);
        } else {
            // multiple contexts
            kubeContexts = List.of(contexts.split(","));
        }
        kubeClients = new LinkedHashMap<>(kubeContexts.size());
        for (String context : kubeContexts) {
            kubeClients.put(context, new KubeClient(context));
        }
    }

    public KubeClient getDefaultKubeClient() {
        return kubeClients.get(kubeContexts.get(0));
    }

    public KubeClient getKubeClient(String context) {
        return kubeClients.get(context);
    }

    public Map<String, KubeClient> getKubeClients() {
        return kubeClients;
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

    public String[] getKubeCredentials() {
        if (kubeCredentials != null) {
            if (kubeCredentials.contains("/")) {
                return kubeCredentials.split("/");
            } else {
                throw new ClaireRuntimeException("Provided " + Constants.EV_KUBE_CREDENTIALS +
                        " credentials are in incorrect format! Missing '/' as username/password delimiter!");
            }
        } else {
            throw new ClaireRuntimeException("Missing environment variable " + Constants.EV_KUBE_CREDENTIALS);
        }
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

    private String getDefaultKeycloakOperatorName() {
        if (isUpstreamArtemis()) {
            return Constants.DEFAULT_KEYCLOAK_OPERATOR_NAME;
        } else {
            return Constants.DEFAULT_RHSSO_OPERATOR_NAME;
        }
    }

    private String getDefaultKeycloakVersion(String keycloakChannel) {
        if (isUpstreamArtemis()) {
            return Constants.DEFAULT_KEYCLOAK_VERSION;
        } else {
            if (!getDefaultKubeClient().isKubernetesPlatform()) {
                return getDefaultKubeClient().getPackageManifestVersion(pm, keycloakChannel);
            } else {
                throw new ClaireRuntimeException("[RHSSO] Unable to find proper channel on non-OCP platform!");
            }
        }
    }

    private String getDefaultKeycloakChannel() {
        if (isUpstreamArtemis()) {
            return Constants.DEFAULT_KEYCLOAK_CHANNEL;
        } else {
            if (!getDefaultKubeClient().isKubernetesPlatform()) {
                getKeycloakOperatorName();
                pm = getDefaultKubeClient().getPackageManifest(keycloakOperatorName);
                return getDefaultKubeClient().getPackageManifestDefaultChannel(pm);
            } else {
                throw new UnsupportedOperationException("[RHSSO] Unable to find any channel for RHSSO installation");
            }
        }
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
