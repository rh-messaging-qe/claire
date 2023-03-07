/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.logging;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisBuilder;
import io.brokerqe.AbstractSystemTests;
import io.brokerqe.ArtemisVersion;
import io.brokerqe.Constants;
import io.brokerqe.ResourceManager;
import io.brokerqe.TestUtils;
import io.brokerqe.junit.TestValidSince;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

@TestValidSince(ArtemisVersion.VERSION_2_28)
public class ArtemisLoggingTests extends AbstractSystemTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArtemisLoggingTests.class);
    private static final String LOGGER_FILE = Constants.PROJECT_TEST_DIR + "/resources/logging/persistence-enabled-log4j2.properties";
    private static final String LOGGER_CONFIG_MAP_NAME = "artemis-cm-logging-config";
    private static final String LOGGER_SECRET_NAME = "artemis-secret-logging-config";
    private static final String LOGGING_PROPERTIES_KEY = "logging.properties";
    private static final String AUDIT_LOG_FILE = "audit.log";
    private static final String ARTEMIS_LOG_FILE = "artemis.log";

    private final String testNamespace = getRandomNamespaceName("artemis-log-tests", 6);

    @BeforeAll
    void setupClusterOperator() {
        setupDefaultClusterOperator(testNamespace);
    }

    @AfterAll
    void teardownClusterOperator() {
        teardownDefaultClusterOperator(testNamespace);
    }

    @Test
    void defaultLoggingTest() {
        String artemisName = "artemis-default-log";
        ActiveMQArtemis artemis = ResourceManager.createArtemis(testNamespace, artemisName);
        Pod artemisPod = getClient().getFirstPodByPrefixName(testNamespace, artemisName);

        String artemisLogs = getClient().getLogsFromPod(artemisPod);
        LOGGER.info("[{}] Ensure artemis pod logs contains using default log message", testNamespace);
        assertThat(artemisLogs, containsString(Constants.ARTEMIS_USING_DEFAULT_LOG_MSG));
        LOGGER.info("[{}] Ensure artemis pod logs contains INFO level", testNamespace);
        assertThat(artemisLogs, containsString(Constants.ARTEMIS_IS_LIVE_LOG_MSG));

        assertLogIsNotInFilesystem(artemisPod);

        ResourceManager.deleteArtemis(testNamespace, artemis);
    }

    @Test
    void providedLoggingUsingSecretTest() throws IOException {
        getClient().createSecretEncodedData(testNamespace, LOGGER_SECRET_NAME, Map.of(LOGGING_PROPERTIES_KEY,
                TestUtils.getFileContentAsBase64(LOGGER_FILE)), true);

        String artemisName = "artemis-secret-log";
        ActiveMQArtemis artemis = new ActiveMQArtemisBuilder()
                .editOrNewMetadata()
                    .withName(artemisName)
                    .withNamespace(testNamespace)
                .endMetadata()
                .editOrNewSpec()
                    .editOrNewDeploymentPlan()
                        .withSize(1)
                        .withImage("placeholder")
                        .editOrNewExtraMounts()
                            .withSecrets(LOGGER_SECRET_NAME)
                        .endExtraMounts()
                    .endDeploymentPlan()
                .endSpec()
                .build();
        artemis = ResourceManager.createArtemis(testNamespace, artemis);
        Pod artemisPod = getClient().getFirstPodByPrefixName(testNamespace, artemisName);

        assertCustomLogMsg(artemisPod);

        assertLoggingIsInFilesystem(artemisPod);

        ResourceManager.deleteArtemis(testNamespace, artemis);
        getClient().deleteSecret(testNamespace, LOGGER_SECRET_NAME);
    }
    
    @Test
    void providedLoggingUsingConfigMapTest() throws IOException {
        createLoggerConfigMap(TestUtils.getFileContentAsBase64(ArtemisLoggingTests.LOGGER_FILE));

        String artemisName = "artemis-cm-log";
        ActiveMQArtemis artemis = new ActiveMQArtemisBuilder()
                .editOrNewMetadata()
                    .withName(artemisName)
                    .withNamespace(testNamespace)
                .endMetadata()
                .editOrNewSpec()
                    .editOrNewDeploymentPlan()
                        .withSize(1)
                        .withImage("placeholder")
                        .editOrNewExtraMounts()
                            .withConfigMaps(LOGGER_CONFIG_MAP_NAME)
                        .endExtraMounts()
                    .endDeploymentPlan()
                .endSpec()
                .build();
        artemis = ResourceManager.createArtemis(testNamespace, artemis);
        Pod artemisPod = getClient().getFirstPodByPrefixName(testNamespace, artemisName);

        assertCustomLogMsg(artemisPod);

        assertLoggingIsInFilesystem(artemisPod);

        ResourceManager.deleteArtemis(testNamespace, artemis);
        getClient().deleteConfigMap(testNamespace, LOGGER_CONFIG_MAP_NAME);
    }

    @Test
    void providedLoggingUsingEmptyConfigMapTest() {
        createLoggerConfigMap("");

        String artemisName = "artemis-empty-cm-log";
        ActiveMQArtemis artemis = new ActiveMQArtemisBuilder()
                .editOrNewMetadata()
                    .withName(artemisName)
                    .withNamespace(testNamespace)
                .endMetadata()
                .editOrNewSpec()
                    .editOrNewDeploymentPlan()
                        .withSize(1)
                        .withImage("placeholder")
                        .editOrNewExtraMounts()
                            .withConfigMaps(LOGGER_CONFIG_MAP_NAME)
                        .endExtraMounts()
                    .endDeploymentPlan()
                .endSpec()
                .build();
        artemis = ResourceManager.createArtemis(testNamespace, artemis);
        Pod artemisPod = getClient().getFirstPodByPrefixName(testNamespace, artemisName);

        assertCustomLogMsg(artemisPod);

        assertLogIsNotInFilesystem(artemisPod);

        ResourceManager.deleteArtemis(testNamespace, artemis);
        getClient().deleteConfigMap(testNamespace, LOGGER_CONFIG_MAP_NAME);
    }

    @Test
    void providedLoggingUsingConfigMapAndSecretTest() throws IOException {
        getClient().createSecretEncodedData(testNamespace, LOGGER_SECRET_NAME, Map.of(LOGGING_PROPERTIES_KEY,
                TestUtils.getFileContentAsBase64(LOGGER_FILE)), true);
        createLoggerConfigMap(TestUtils.getFileContentAsBase64(ArtemisLoggingTests.LOGGER_FILE));

        String artemisName = "artemis-cm-secret-log";
        ActiveMQArtemis artemis = new ActiveMQArtemisBuilder()
                .editOrNewMetadata()
                    .withName(artemisName)
                    .withNamespace(testNamespace)
                .endMetadata()
                .editOrNewSpec()
                    .editOrNewDeploymentPlan()
                        .withSize(1)
                        .withImage("placeholder")
                        .editOrNewExtraMounts()
                            .withConfigMaps(LOGGER_CONFIG_MAP_NAME)
                            .withSecrets(LOGGER_SECRET_NAME)
                        .endExtraMounts()
                    .endDeploymentPlan()
                .endSpec()
                .build();
        artemis = ResourceManager.createArtemis(testNamespace, artemis);
        Pod artemisPod = getClient().getFirstPodByPrefixName(testNamespace, artemisName);

        assertCustomLogMsg(artemisPod);
        assertLoggingIsInFilesystem(artemisPod);

        ResourceManager.deleteArtemis(testNamespace, artemis);
        getClient().deleteConfigMap(testNamespace, LOGGER_CONFIG_MAP_NAME);
        getClient().deleteSecret(testNamespace, LOGGER_SECRET_NAME);
    }

    private void assertLogIsNotInFilesystem(Pod artemisPod) {
        LOGGER.info("[{}] Ensure artemis pod is not logging into filesystem", testNamespace);
        String testDirName = testInfo.getTestClass().orElseThrow().getName() + Constants.FILE_SEPARATOR
                + testInfo.getTestMethod().orElseThrow().getName();
        Path tmpDirName = createTestTemporaryDir(testDirName);
        Path logDestDir = getClient().getPodDir(artemisPod, Constants.CONTAINER_BROKER_HOME_LOG_DIR, tmpDirName);
        assertThat(Files.isDirectory(logDestDir), is(Boolean.TRUE));
        try (Stream<Path> entries = Files.list(logDestDir)) {
            entries.forEach(e -> assertThat(e.toFile().length(), equalTo(0L)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        TestUtils.deleteDirectoryRecursively(tmpDirName);
    }

    private void assertCustomLogMsg(Pod artemisPod) {
        LOGGER.info("[{}] Ensure artemis pod logs contains custom log message", testNamespace);
        String artemisLogs = getClient().getLogsFromPod(artemisPod);
        assertThat(artemisLogs, containsString(Constants.ARTEMIS_USING_CUSTOM_LOG_MSG));
    }

    private void assertLoggingIsInFilesystem(Pod artemisPod) throws IOException {
        String testDirName = testInfo.getTestClass().orElseThrow().getName() + Constants.FILE_SEPARATOR
                + testInfo.getTestMethod().orElseThrow().getName();
        Path tmpDirName = createTestTemporaryDir(testDirName);
        Path logDestDir = getClient().getPodDir(artemisPod, Constants.CONTAINER_BROKER_HOME_LOG_DIR, tmpDirName);
        assertThat(Files.isDirectory(logDestDir), is(Boolean.TRUE));

        LOGGER.info("[{}] Ensure artemis pod is logging into " + ARTEMIS_LOG_FILE + " into filesystem", testNamespace);
        Path artemisLogFile = Paths.get(logDestDir + Constants.FILE_SEPARATOR + ARTEMIS_LOG_FILE);
        assertThat(artemisLogFile.toFile().length(), greaterThan(0L));
        List<String> artemisLogs = Files.readAllLines(artemisLogFile);
        assertThat(artemisLogs, hasItem(containsString(Constants.ARTEMIS_IS_LIVE_LOG_MSG)));

        LOGGER.info("[{}] Ensure artemis pod is logging into " + AUDIT_LOG_FILE + " into filesystem", testNamespace);
        Path auditLogFile = Paths.get(logDestDir + Constants.FILE_SEPARATOR + AUDIT_LOG_FILE);
        assertThat(auditLogFile.toFile().length(), greaterThan(0L));
        List<String> auditLogs = Files.readAllLines(auditLogFile);
        assertThat(auditLogs, hasItem(containsString(" [AUDIT]")));

        TestUtils.deleteDirectoryRecursively(tmpDirName);
    }

    private void createLoggerConfigMap(String data) {
        ConfigMap cm = new ConfigMapBuilder()
                .editOrNewMetadata()
                    .withName(ArtemisLoggingTests.LOGGER_CONFIG_MAP_NAME)
                .endMetadata()
                .withBinaryData(Map.of(LOGGING_PROPERTIES_KEY, data))
                .build();
        getKubernetesClient().configMaps().inNamespace(testNamespace).resource(cm).createOrReplace();
    }
}