/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.logging;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisBuilder;
import io.amq.broker.v1beta1.activemqartemisspec.EnvBuilder;
import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.exception.WaitException;
import io.brokerqe.claire.junit.TestValidSince;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import org.assertj.core.api.Assertions;
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
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestValidSince(ArtemisVersion.VERSION_2_28)
public class ArtemisLoggingTests extends AbstractSystemTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArtemisLoggingTests.class);
    private static final String LOGGER_FILE = Constants.PROJECT_TEST_DIR + "/resources/logging/persistence-enabled-log4j2.properties";
    private static final String JSON_TEMPLATE_LOGGER_FILE = Constants.PROJECT_TEST_DIR + "/resources/logging/json-template-log4j2.properties";
    private static final String LOGGER_CONFIG_MAP_NAME = "artemis-cm-logging-config";
    private static final String LOGGER_SECRET_NAME = "artemis-secret-logging-config";
    private static final String LOGGING_PROPERTIES_KEY = "logging.properties";
    private static final String LOGGING_MOUNT_ONLY_ONCE = "Spec.DeploymentPlan.ExtraMounts, entry with suffix -logging-config can only be supplied once";

    private static final String LOG4J_LAYOUT_TEMPLATE_JAR_URL = "https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-layout-template-json/2.19.0/log4j-layout-template-json-2.19.0.jar";
    private final String testNamespace = getRandomNamespaceName("artemis-log-tests", 3);

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
        LOGGER.info("[{}] Ensure artemis pod logs contains INFO level", testNamespace);
        assertThat(artemisLogs, containsString(ArtemisConstants.IS_LIVE_LOG_MSG));

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

        assertLoggingIsInFilesystem(artemisPod);
        assertLoggingDoesNotContainsException(artemisPod);

        ResourceManager.deleteArtemis(testNamespace, artemis);
        getClient().deleteSecret(testNamespace, LOGGER_SECRET_NAME);
    }
    
    @Test
    void providedLoggingUsingConfigMapTest() throws IOException {
        getClient().createConfigMap(testNamespace, LOGGER_CONFIG_MAP_NAME,
                Map.of(LOGGING_PROPERTIES_KEY, TestUtils.readFileContent(Paths.get(LOGGER_FILE).toFile())));

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

        assertLoggingIsInFilesystem(artemisPod);
        assertLoggingDoesNotContainsException(artemisPod);

        ResourceManager.deleteArtemis(testNamespace, artemis);
        getClient().deleteConfigMap(testNamespace, LOGGER_CONFIG_MAP_NAME);
    }

    private void assertLoggingDoesNotContainsException(Pod artemisPod) {
        LOGGER.info("[{}] Ensure artemis pod logs does not contains java exceptions", testNamespace);
        String artemisLogs = getClient().getLogsFromPod(artemisPod);
        Assertions.assertThat(artemisLogs).doesNotContain(ArtemisConstants.LOG_EXCEPTION);
        Assertions.assertThat(artemisLogs).doesNotContain(ArtemisConstants.LOG_EXCEPTION_CAUSE);
    }

    @Test
    void providedLoggingUsingEmptyConfigMapTest() {
        getClient().createConfigMap(testNamespace, LOGGER_CONFIG_MAP_NAME, Map.of(LOGGING_PROPERTIES_KEY, ""));

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

        String artemisLogs = getClient().getLogsFromPod(artemisPod);
        LOGGER.info("[{}] Ensure artemis pod logs not contains INFO level", testNamespace);
        assertThat(artemisLogs, not(containsString(ArtemisConstants.IS_LIVE_LOG_MSG)));
        assertLogIsNotInFilesystem(artemisPod);

        ResourceManager.deleteArtemis(testNamespace, artemis);
        getClient().deleteConfigMap(testNamespace, LOGGER_CONFIG_MAP_NAME);
    }

    @Test
    void providedLoggingUsingConfigMapAndSecretTest() {
        getClient().createSecretEncodedData(testNamespace, LOGGER_SECRET_NAME, Map.of(LOGGING_PROPERTIES_KEY,
                TestUtils.getFileContentAsBase64(LOGGER_FILE)), true);
        getClient().createConfigMap(testNamespace, LOGGER_CONFIG_MAP_NAME,
                Map.of(LOGGING_PROPERTIES_KEY, TestUtils.readFileContent(Paths.get(LOGGER_FILE).toFile())));

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

        assertThrows(WaitException.class, () -> ResourceManager.createArtemis(testNamespace, artemis, true, Constants.DURATION_30_SECONDS));
        boolean logStatus = ResourceManager.getArtemisStatus(testNamespace, artemis, ArtemisConstants.CONDITION_TYPE_VALID,
                ArtemisConstants.CONDITION_REASON_INVALID_EXTRA_MOUNT, LOGGING_MOUNT_ONLY_ONCE);
        assertThat("Artemis condition does not match", Boolean.TRUE, is(logStatus));

        ResourceManager.deleteArtemis(testNamespace, artemis);
        getClient().deleteConfigMap(testNamespace, LOGGER_CONFIG_MAP_NAME);
        getClient().deleteSecret(testNamespace, LOGGER_SECRET_NAME);
    }

    private void assertLogIsNotInFilesystem(Pod artemisPod) {
        LOGGER.info("[{}] Ensure artemis pod is not logging into filesystem", testNamespace);
        String testDirName = testInfo.getTestClass().orElseThrow().getName() + Constants.FILE_SEPARATOR
                + testInfo.getTestMethod().orElseThrow().getName();
        Path tmpDirName = TestUtils.createTestTemporaryDir(testDirName, testEnvironmentOperator.getTmpDirLocation());
        Path logDestDir = getClient().copyPodDir(artemisPod, ArtemisConstants.CONTAINER_BROKER_HOME_LOG_DIR, tmpDirName);
        assertThat(Files.isDirectory(logDestDir), is(Boolean.TRUE));
        try (Stream<Path> entries = Files.list(logDestDir)) {
            entries.forEach(e -> assertThat(e.toFile().length(), equalTo(0L)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        TestUtils.deleteDirectoryRecursively(tmpDirName);
    }

    private void assertLoggingIsInFilesystem(Pod artemisPod) throws IOException {
        String testDirName = testInfo.getTestClass().orElseThrow().getName() + Constants.FILE_SEPARATOR
                + testInfo.getTestMethod().orElseThrow().getName();
        Path tmpDirName = TestUtils.createTestTemporaryDir(testDirName, testEnvironmentOperator.getTmpDirLocation());
        Path logDestDir = getClient().copyPodDir(artemisPod, ArtemisConstants.CONTAINER_BROKER_HOME_LOG_DIR, tmpDirName);
        assertThat(Files.isDirectory(logDestDir), is(Boolean.TRUE));

        LOGGER.info("[{}] Ensure artemis pod is logging into " + ArtemisConstants.ARTEMIS_LOG_FILE + " into filesystem", testNamespace);
        Path artemisLogFile = Paths.get(logDestDir + Constants.FILE_SEPARATOR + ArtemisConstants.ARTEMIS_LOG_FILE);
        assertThat(artemisLogFile.toFile().length(), greaterThan(0L));
        List<String> artemisLogs = Files.readAllLines(artemisLogFile);
        assertThat(artemisLogs, hasItem(containsString(ArtemisConstants.IS_LIVE_LOG_MSG)));

        LOGGER.info("[{}] Ensure artemis pod is logging into " + ArtemisConstants.AUDIT_LOG_FILE + " into filesystem", testNamespace);
        Path auditLogFile = Paths.get(logDestDir + Constants.FILE_SEPARATOR + ArtemisConstants.AUDIT_LOG_FILE);
        assertThat(auditLogFile.toFile().length(), greaterThan(0L));
        List<String> auditLogs = Files.readAllLines(auditLogFile);
        assertThat(auditLogs, hasItem(containsString(" [AUDIT]")));

        TestUtils.deleteDirectoryRecursively(tmpDirName);
    }

    @Test
    void providerLoggingUsingExternalLibraryTest() {
        getClient().createConfigMap(testNamespace, LOGGER_CONFIG_MAP_NAME,
                Map.of(LOGGING_PROPERTIES_KEY, TestUtils.readFileContent(Paths.get(JSON_TEMPLATE_LOGGER_FILE).toFile())));

        String downloadDriverCommand = "mkdir -p /amq/init/config/extra-libs && wget -O /amq/init/config/extra-libs/log4j-layout-template-json-2.19.0.jar %s".formatted(LOG4J_LAYOUT_TEMPLATE_JAR_URL);
        StatefulSet log4jlibPatchSs = new StatefulSetBuilder()
                .editOrNewSpec()
                    .editOrNewTemplate()
                        .editOrNewSpec()
                            .withInitContainers(
                                new ContainerBuilder()
                                    .withName("log4j-json-template-init")
                                    .withImage("quay.io/almworks/alpine-curl:latest")
                                    .withVolumeMounts(
                                        new VolumeMountBuilder()
                                            .withName("amq-cfg-dir")
                                            .withMountPath("/amq/init/config")
                                            .build()
                                    )
                                    .withCommand(List.of("sh", "-c"))
                                    .withArgs(downloadDriverCommand)
                                .build()
                            )
                        .endSpec()
                    .endTemplate()
                .endSpec()
                .build();

        String artemisName = "artemis-json-log";
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
                    .addToEnv(new EnvBuilder()
                        .withName("ARTEMIS_EXTRA_LIBS")
                        .withValue("/amq/init/config/extra-libs")
                        .build()
                    )
                    .addNewResourceTemplate()
                        .withNewResourcetemplatesSelector()
                            .withKind("StatefulSet")
                        .endResourcetemplatesSelector()
                        .editOrNewPatch()
                            .withAdditionalProperties(Map.of(
                                "kind", "StatefulSet",
                                "spec", log4jlibPatchSs.getSpec())
                            )
                        .endPatch()
                    .endResourceTemplate()
                .endSpec()
                .build();
        artemis = ResourceManager.createArtemis(testNamespace, artemis);
        Pod artemisPod = getClient().getFirstPodByPrefixName(testNamespace, artemisName);

        String artemisLogs = getClient().getLogsFromPod(artemisPod);
        Assertions.assertThat(artemisLogs).doesNotContain("ERROR StatusConsoleListener Unable to invoke factory method in class org.apache.logging.log4j.layout.template.json.JsonTemplateLayout");
        Assertions.assertThat(artemisLogs).doesNotContain("java.lang.IllegalStateException: No factory method found for class org.apache.logging.log4j.layout.template.json.JsonTemplateLayout");

        ResourceManager.deleteArtemis(testNamespace, artemis);
        getClient().deleteConfigMap(testNamespace, LOGGER_CONFIG_MAP_NAME);
    }
}
