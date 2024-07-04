/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.persistence;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisAddress;
import io.amq.broker.v1beta1.ActiveMQArtemisBuilder;
import io.amq.broker.v1beta1.activemqartemisspec.EnvBuilder;
import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.clients.ClientType;
import io.brokerqe.claire.clients.MessagingClient;
import io.brokerqe.claire.db.Postgres;
import io.brokerqe.claire.junit.TestValidSince;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

@TestValidSince(ArtemisVersion.VERSION_2_32)
public class JdbcSingleDbTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcSingleDbTests.class);
    //    private final String testNamespace = getRandomNamespaceName("jdbc-tests", 2);
    private final String testNamespace = "jdbc-tests";
    private final String brokerName = "jdbc-brk";
    private Postgres postgres;
    private ActiveMQArtemis broker;
    private static final String LOGGER_SECRET_NAME = "artemis-secret-logging-config";
    private static final String LOGGING_PROPERTIES_KEY = "logging.properties";
    private static final String LOGGER_FILE = Constants.PROJECT_TEST_DIR + "/resources/logging/persistence-enabled-log4j2.properties";

    @BeforeAll
    void setupClusterOperator() {
        setupDefaultClusterOperator(testNamespace);
        deployPostgres();
    }

    @AfterAll
    void teardownClusterOperator() {
        ResourceManager.deleteArtemis(testNamespace, broker);
        teardownDefaultClusterOperator(testNamespace);
        postgres.undeployPostgres();
    }

    protected void deployPostgres() {
        postgres = ResourceManager.getPostgresInstance(testNamespace);
        postgres.deployPostgres("db-jar-plugin");
    }

    void deployBrokerWithDB(int brokerSize, boolean waitForDeployment) {
        getClient().createSecretEncodedData(testNamespace, LOGGER_SECRET_NAME, Map.of(LOGGING_PROPERTIES_KEY, TestUtils.getFileContentAsBase64(LOGGER_FILE)), true);

        String downloadDriverCommand = "mkdir -p /amq/init/config/extra-libs && wget -O /amq/init/config/extra-libs/postgresql.jar %s".formatted(Constants.POSTGRESQL_DRIVER_URL);
        StatefulSet jdbcPatchSs = new StatefulSetBuilder()
            .editOrNewSpec()
                .editOrNewTemplate()
                    .editOrNewSpec()
                        .withInitContainers(
                            new ContainerBuilder()
                                .withName("postgresql-jdbc-driver-init")
                                .withImage("quay.io/rh_integration/alpine-curl:latest")
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

        broker = new ActiveMQArtemisBuilder()
            .editOrNewMetadata()
                .withName(brokerName)
                .withNamespace(testNamespace)
            .endMetadata()
            .editOrNewSpec()
                .editOrNewDeploymentPlan()
                    .withSize(brokerSize)
                    .withPersistenceEnabled()
                    .editOrNewExtraMounts()
                        .withSecrets(LOGGER_SECRET_NAME)
                    .endExtraMounts()
                .endDeploymentPlan()
                .addToEnv(new EnvBuilder()
                        .withName("ARTEMIS_EXTRA_LIBS")
                        .withValue("/amq/init/config/extra-libs")
                        .build()
                )
                .withBrokerProperties(List.of(
                        "storeConfiguration=DATABASE",
                        "storeConfiguration.jdbcDriverClassName=org.postgresql.Driver",
                        "storeConfiguration.jdbcConnectionUrl=" + postgres.getJdbcUrl(),
//                        "HAPolicyConfiguration.failoverOnServerShutdown=true",
                        "HAPolicyConfiguration=SHARED_STORE_PRIMARY"
                ))
                .addNewResourceTemplate()
                    .withNewResourcetemplatesSelector()
                    .withKind("StatefulSet")
                    .endResourcetemplatesSelector()
                    .editOrNewPatch()
                        .withAdditionalProperties(Map.of(
                                "kind", "StatefulSet",
                                "spec", jdbcPatchSs.getSpec())
                        )
                    .endPatch()
                .endResourceTemplate()
            .endSpec().build();
        ResourceManager.createArtemis(testNamespace, broker, waitForDeployment, Constants.DURATION_2_MINUTES);
    }

    void undeployBrokerWithDB() {
        ResourceManager.deleteArtemis(testNamespace, broker);
        getClient().deleteSecret(testNamespace, LOGGER_SECRET_NAME);
    }

    @Test
    void testSingleBrokerMessaging() {
        deployBrokerWithDB(1, true);
        ActiveMQArtemisAddress myAddress = ResourceManager.createArtemisAddress(testNamespace, "testx", "testx");
        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, brokerName);
        int msgsExpected = 10;
        LOGGER.info("[{}] Send & receive messages", brokerPod.getMetadata().getName());
        testMessaging(testNamespace, brokerPod, myAddress, msgsExpected);

        LOGGER.info("[{}] Send, restart broker & receive messages", brokerPod.getMetadata().getName());
        String allDefaultPort = getServicePortNumber(testNamespace, getArtemisServiceHdls(testNamespace, broker), "all");
        MessagingClient messagingClientCore1 = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, brokerPod, allDefaultPort, myAddress, msgsExpected);
        int sent = messagingClientCore1.sendMessages();
        checkMessageCount(testNamespace, brokerPod, myAddress.getSpec().getQueueName(), msgsExpected);

        // pod reload - need new client
        brokerPod = getClient().reloadPodWithWait(testNamespace, brokerPod, brokerName);
        checkMessageCount(testNamespace, brokerPod, myAddress.getSpec().getQueueName(), msgsExpected);

        MessagingClient messagingClientCore = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, brokerPod, allDefaultPort, myAddress, msgsExpected);
        int recv = messagingClientCore.receiveMessages();
        assertThat("Send & received messages are not same!", sent, equalTo(recv));

        ResourceManager.deleteArtemisAddress(testNamespace, myAddress);
        undeployBrokerWithDB();
    }

    @Test
    void testJdbcLock() {
        String acquiredLockLog = "AMQ221035: Primary Server Obtained primary lock";
        String waitingForLockLog = "AMQ221034: Waiting indefinitely to obtain primary lock";

        deployBrokerWithDB(2, false);
        TestUtils.waitFor("First broker pod to run", Constants.DURATION_5_SECONDS, Constants.DURATION_2_MINUTES, () -> {
            List<Pod> pods = getClient().listPodsByPrefixName(testNamespace, brokerName);
            Boolean[] bools = {false, false};
            for (Pod pod : pods) {
                if (pod.getMetadata().getName().contains("0")) {
                    // ok pod
                    getClient().waitUntilPodIsReady(testNamespace, pod);
                    bools[0] = true;
                } else if (pod.getMetadata().getName().contains("1")) {
                    // failing pod
                    getClient().waitUntilPodCondition(testNamespace, pod,
                             pod1 -> pod1.getStatus().getPhase().equals("Running") || pod1.getStatus().getPhase().equals("CrashLoopBackOff")
                    );
                    bools[1] = true;
                }
            }
            return !Arrays.asList(bools).contains(false);
        });
        LOGGER.info("Deployment is successful, check JDBC locks");
        TestUtils.threadSleep(Constants.DURATION_10_SECONDS);

        List<Pod> brokerPods = getClient().listPodsByPrefixName(testNamespace, brokerName);
        for (Pod brokerPod : brokerPods) {
            LOGGER.info("[{}] Check for JDBC lock in pod logs {}", testNamespace, brokerPod.getMetadata().getName());
            if (brokerPod.getMetadata().getName().contains("0")) {
                String brokerLogs = getClient().getLogsFromPod(brokerPod);
                assertThat(brokerLogs, containsString(acquiredLockLog));
            } else if (brokerPod.getMetadata().getName().contains("1")) {
                TestUtils.waitFor(waitingForLockLog + " to show up", Constants.DURATION_2_SECONDS, Constants.DURATION_1_MINUTE, () -> {
                    String brokerLogs = getClient().getLogsFromPod(brokerPod);
                    return brokerLogs.contains(waitingForLockLog);
                });
            }
        }

        LOGGER.info("[{}] Delete pod 0 and check DB lock on pod 1", testNamespace);
        getClient().deletePod(testNamespace, getClient().getPod(testNamespace, brokerName + "-ss-0"), false);
        TestUtils.threadSleep(Constants.DURATION_5_SECONDS);
        TestUtils.waitFor(acquiredLockLog + " to show up", Constants.DURATION_5_SECONDS, Constants.DURATION_2_MINUTES, () -> {
            Pod brokerPod1 = getClient().getPod(testNamespace, brokerName + "-ss-1");
            return getClient().getLogsFromPod(brokerPod1).contains(acquiredLockLog);
        });

        undeployBrokerWithDB();
    }

}
