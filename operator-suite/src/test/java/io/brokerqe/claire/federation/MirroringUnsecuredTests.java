/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.federation;

import io.amq.broker.v1beta1.ActiveMQArtemisBuilder;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.clients.ClientType;
import io.brokerqe.claire.clients.MessagingClient;
import io.brokerqe.claire.clients.MessagingClientException;
import io.brokerqe.claire.junit.TestValidSince;
import io.fabric8.kubernetes.api.model.Pod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestValidSince(ArtemisVersion.VERSION_2_33)
public class MirroringUnsecuredTests extends MirroringTests {

    List<Pod> prodBrokerPods;
    List<Pod> drBrokerPods;
    Pod prodBrokerPod;
    Pod drBrokerPod;
    private int totalReceivedA = 0;
    private int totalReceivedB = 0;
    private int totalSentA = 0;
    private int totalSentB = 0;

    @BeforeEach
    void resetCounters() {
        totalReceivedA = 0;
        totalReceivedB = 0;
        totalSentA = 0;
        totalSentB = 0;
        prodBrokerPods = null;
        drBrokerPods = null;
        prodBrokerPod = null;
        drBrokerPod = null;
        setupDeployment(1);
    }

    @AfterEach
    void teardown() {
        teardownDeployment(false);
    }

    void setupDeployment(int size) {
        getClient().createSecretEncodedData(prodNamespace, LOGGER_SECRET_NAME, Map.of(ArtemisConstants.LOGGING_PROPERTIES_CONFIG_KEY, TestUtils.getFileContentAsBase64(DEBUG_LOG_FILE)), true);
        getClient().createSecretEncodedData(drNamespace, LOGGER_SECRET_NAME, Map.of(ArtemisConstants.LOGGING_PROPERTIES_CONFIG_KEY, TestUtils.getFileContentAsBase64(DEBUG_LOG_FILE)), true);
        List<String> drBrokerProperties = List.of(
                "maxDiskUsage=85",
                "clusterConfigurations.my-cluster.producerWindowSize=-1",
                "addressSettings.#.redeliveryMultiplier=5",
                "criticalAnalyzer=true",
                "criticalAnalyzerTimeout=6000",
                "criticalAnalyzerCheckPeriod=-1",
                "criticalAnalyzerPolicy=LOG"
        );

        drAmqpAcceptor = createAcceptor(AMQP_ACCEPTOR_NAME, "amqp", 5672, true);
        drAllAcceptor = createAcceptor(ALL_ACCEPTOR_NAME, "all", 61616, true);
        prodAmqpAcceptor = drAmqpAcceptor; //createAcceptor(amqpAcceptorName, "amqp", 5672, true);
        prodAllAcceptor = drAllAcceptor; //createAcceptor(allAcceptorName, "all", 61616, true);

        drBroker = new ActiveMQArtemisBuilder()
            .editOrNewMetadata()
                .withName(DR_BROKER_NAME)
                .withNamespace(drNamespace)
            .endMetadata()
            .editOrNewSpec()
                .withNewDeploymentPlan()
                    .withSize(size)
                    .withClustered(false)  // federation + mirring does not need clustered
                    .withPersistenceEnabled(true)
                    .withMessageMigration(true)
                    .withManagementRBACEnabled(true)
                    .withRequireLogin(true)
                    .withJournalType("aio")
                    .withEnableMetricsPlugin(true)
                    .withJolokiaAgentEnabled(true)
                    .editOrNewExtraMounts()
                        .withSecrets(LOGGER_SECRET_NAME)
                    .endExtraMounts()
                .endDeploymentPlan()
                .editOrNewConsole()
                    .withExpose(true)
                .endConsole()
                .withAcceptors(List.of(drAllAcceptor, drAmqpAcceptor))
                .withBrokerProperties(drBrokerProperties)
                .withAdminUser(ADMIN)
                .withAdminPassword(ADMIN_PASS)
            .endSpec()
            .build();

        LOGGER.info("[{}] Deploying Disaster Recovery Broker {}", drNamespace, DR_BROKER_NAME);
        drBroker = ResourceManager.createArtemis(drBroker);

        String amqpConnectionDrUri = createAmqpConnectionBrokerUri(drBroker, drAllAcceptor, false);
        LOGGER.warn("[{}] Broker {} is using AMQPConnections.dr.uri {}", prodNamespace, PROD_BROKER_NAME, amqpConnectionDrUri);
        List<String> prodBrokerProperties = List.of(
                "maxDiskUsage=85",
                "clusterConfigurations.my-cluster.producerWindowSize=-1",
                "addressSettings.#.redeliveryMultiplier=5",
                "criticalAnalyzer=true",
                "criticalAnalyzerTimeout=6000",
                "criticalAnalyzerCheckPeriod=-1",
                "criticalAnalyzerPolicy=LOG",
                // Mirror DR
                // tcp://dr-broker-all-acceptor-${STATEFUL_SET_ORDINAL}-svc.mirror-dr-tests.svc.cluster.local:61616
                // tcp://dr-broker-amqp-acceptor-${STATEFUL_SET_ORDINAL}-svc-rte-mirror-dr-tests.apps.qe-41256-d1.broker.app-services-dev.net
                // tcp://dr-broker-all-acceptor-${STATEFUL_SET_ORDINAL}-svc-rte-mirror-dr-tests.apps.qe-41256-d1.broker.app-services-dev.net:443?sslEnabled=true;verifyHost=false;trustStorePath=/amq/extra/secrets/prod-broker-tls-secret/client.ts;trustStorePassword=brokerPass;keyStorePath=/amq/extra/secrets/prod-broker-tls-secret/broker.ks;keyStorePassword=brokerPass;
                "AMQPConnections.dr.uri=" + amqpConnectionDrUri,
                "AMQPConnections.dr.retryInterval=5000",
                "AMQPConnections.dr.user=" + ADMIN,
                "AMQPConnections.dr.password=" + ADMIN_PASS,
                "AMQPConnections.dr.connectionElements.mirror.type=MIRROR",
                "AMQPConnections.dr.connectionElements.mirror.messageAcknowledgements=true",
                "AMQPConnections.dr.connectionElements.mirror.queueCreation=true",
                "AMQPConnections.dr.connectionElements.mirror.queueRemoval=true",

                // BackUp DR
                //                              tcp://dr-broker-all-acceptor-${STATEFUL_SET_ORDINAL}-svc-rte-mirror-dr-tests.apps.qe-41256-d1.broker.app-services-dev.net:443?sslEnabled=true;verifyHost=false;trustStorePath=/amq/extra/secrets/prod-broker-tls-secret/client.ts;trustStorePassword=brokerPass;keyStorePath=/amq/extra/secrets/prod-broker-tls-secret/broker.ks;keyStorePassword=brokerPass;
//                AMQPConnections.backup-dr.uri=tcp://broker-backup-dr-amqp-${STATEFUL_SET_ORDINAL}-svc-rte-dr.apps.abouchama-amq5.emea.aws.cee.support:443?sslEnabled=true;trustStorePath=/amq/extra/secrets/mytlssecret/client.ts;trustStorePassword=password;verifyHost=false
//        - AMQPConnections.backup-dr.retryInterval=5000
//                - AMQPConnections.backup-dr.user=admin
//                - AMQPConnections.backup-dr.password=admin
//                - AMQPConnections.backup-dr.connectionElements.mirror.type=MIRROR

                // Addresses
                // - addressConfigurations.myaddress-name.queueConfigs.myQueue0.address=myqueue
                //- addressConfigurations.myaddress-name.queueConfigs.myQueue0.routingType=ANYCAST
                "addressConfigurations.queuea.queueConfigs.queuea.address=queuea",
                "addressConfigurations.queuea.queueConfigs.queuea.routingType=ANYCAST",
                "addressSettings.queuea.configDeleteAddresses=FORCE",
                "addressSettings.queuea.configDeleteQueues=FORCE",

                "addressConfigurations.queueb.queueConfigs.queueb.address=queueb",
                "addressConfigurations.queueb.queueConfigs.queueb.routingType=ANYCAST",
                "addressSettings.queueb.configDeleteAddresses=FORCE",
                "addressSettings.queueb.configDeleteQueues=FORCE"
        );

        prodBroker = new ActiveMQArtemisBuilder()
            .editOrNewMetadata()
                .withName(PROD_BROKER_NAME)
                .withNamespace(prodNamespace)
            .endMetadata()
            .editOrNewSpec()
                .withNewDeploymentPlan()
                    .withSize(size)
                    .withClustered(false)  // federation + mirroring does not need clustered
                    .withPersistenceEnabled(true)
                    .withMessageMigration(true)
                    .withManagementRBACEnabled(true)
                    .withRequireLogin(true)
                    .withJournalType("aio")
                    .withEnableMetricsPlugin(true)
                    .withJolokiaAgentEnabled(true)
                    .editOrNewExtraMounts()
                        .withSecrets(LOGGER_SECRET_NAME)
                    .endExtraMounts()
                .endDeploymentPlan()
                .editOrNewConsole()
                    .withExpose(true)
                .endConsole()
                .withAcceptors(List.of(prodAllAcceptor, prodAmqpAcceptor))
                .withBrokerProperties(prodBrokerProperties)
                .withAdminUser(ADMIN)
                .withAdminPassword(ADMIN_PASS)
            .endSpec()
            .build();


        // ==== Prod broker settings ====
        LOGGER.info("[{}] Deploying Production Broker {}", prodNamespace, PROD_BROKER_NAME);
        prodBroker = ResourceManager.createArtemis(prodBroker);

        // 2024-05-22 10:23:33,284 INFO  [org.apache.activemq.artemis.protocol.amqp.logger] AMQ111003:
        //*******************************************************************************************************************************
        //Connected on Server AMQP Connection dr on dr-broker-all-acceptor-0-svc.mirror-dr-tests.svc.cluster.local:61616 after 0 retries
        //*******************************************************************************************************************************
        Pod prodBrokerPod = getClient().getFirstPodByPrefixName(prodNamespace, PROD_BROKER_NAME);
        String prodLogs = getClient().getLogsFromPod(prodBrokerPod);
        TestUtils.threadSleep(Constants.DURATION_10_SECONDS);
        LOGGER.info("[{}] Ensure {} logs contain successful AMQP Connection to {}", prodNamespace, PROD_BROKER_NAME, DR_BROKER_NAME);
        assertThat(prodLogs, allOf(
                containsString("AMQ111003"),
                containsString("Connected on Server AMQP Connection dr on " + DR_BROKER_NAME + "-" + ALL_ACCEPTOR_NAME)
        ));
        allDefaultPort = String.valueOf(prodAllAcceptor.getPort());
    }

    @Test
    void simpleMirroringTest() {
        String addressA = "queue_a";
        String addressB = "queue_b";
        Pod prodBrokerPod = getClient().getFirstPodByPrefixName(prodNamespace, PROD_BROKER_NAME);
        Pod drBrokerPod = getClient().getFirstPodByPrefixName(drNamespace, DR_BROKER_NAME);
        testMessaging(ClientType.BUNDLED_CORE, prodNamespace, prodBrokerPod, addressB, addressB, 10, ADMIN, ADMIN_PASS);

        int initialProdCount = 20;

        LOGGER.info("[{}] Send {} messages to {}", prodNamespace, initialProdCount, prodBrokerPod.getMetadata().getName());
        MessagingClient prodClient = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, prodBrokerPod, allDefaultPort, addressA, initialProdCount, ADMIN, ADMIN_PASS);
        int sent0 = prodClient.sendMessages();
        assertThat("Sent different amount of messages than expected", sent0, equalTo(initialProdCount));

        LOGGER.info("[{}] Read {} messages from {}", drNamespace, initialProdCount, drBrokerPod.getMetadata().getName());
        MessagingClient drClient = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, drBrokerPod, allDefaultPort, addressA, initialProdCount, ADMIN, ADMIN_PASS);
        int received0 = drClient.receiveMessages();
        assertThat("Sent different amount of messages than expected", sent0, equalTo(initialProdCount));
        assertThat("Sent & received different amount of messages than expected", sent0, equalTo(received0));

        int receiveProd = prodClient.receiveMessages();
        assertThat("Sent different amount of messages than expected", receiveProd, equalTo(20));

        LOGGER.info("[{}] {} - {}: mc: {}", prodNamespace, prodBrokerPod.getMetadata().getName(), addressA, getMessageCount(prodNamespace, prodBrokerPod, addressA, ADMIN, ADMIN_PASS));
        LOGGER.info("[{}] {} - {}: mc: {}", drNamespace, drBrokerPod.getMetadata().getName(), addressA, getMessageCount(drNamespace, drBrokerPod, addressA, ADMIN, ADMIN_PASS));

        checkMessageCount(prodNamespace, prodBrokerPod, addressA, 0, ADMIN, ADMIN_PASS);
        checkMessageCount(drNamespace, drBrokerPod, addressA, 0, ADMIN, ADMIN_PASS);

        // Queue deletion
        LOGGER.info("=== Testing of DR mirror: Address creation & deletion ===");
        List<String> testAddresses = List.of(
                "addressConfigurations.my-deletion1.queueConfigs.my-deletion1.address=my-deletion1",
                "addressConfigurations.my-deletion1.queueConfigs.my-deletion1.routingType=ANYCAST",
                "addressConfigurations.my-deletion2.queueConfigs.my-deletion2.address=my-deletion2",
                "addressConfigurations.my-deletion2.queueConfigs.my-deletion2.routingType=ANYCAST",
                "addressConfigurations.my-deletion3.queueConfigs.different-queue-deletion-name.address=my-deletion3",
                "addressConfigurations.my-deletion3.queueConfigs.different-queue-deletion-name.routingType=ANYCAST"
        );

        List<String> testAddressConfigs = List.of(
                "addressSettings.my-deletion1.configDeleteAddresses=FORCE",
                "addressSettings.my-deletion1.configDeleteQueues=FORCE",
                "addressSettings.my-deletion2.configDeleteAddresses=FORCE",
                "addressSettings.my-deletion2.configDeleteQueues=FORCE",
                "addressSettings.my-deletion3.configDeleteAddresses=FORCE",
                "addressSettings.my-deletion3.configDeleteQueues=FORCE"
        );

        List<String> deployAddresses = new ArrayList<>(testAddressConfigs);
        deployAddresses.addAll(testAddresses);
        prodBroker = ResourceManager.addToBrokerProperties(prodBroker, deployAddresses, false);

        // make sure given addresses are present on DR broker
        // TODO: make use of CONDITION_TYPE_BROKER_PROPERTIES_APPLIED?
//        ActiveMQArtemis brk = ResourceManager.getArtemisClient().inNamespace(prodNamespace).resource(prodBroker).get();
//        assertTrue(brk.getStatus().getConditions().contains(ArtemisConstants.CONDITION_TYPE_BROKER_PROPERTIES_APPLIED));
        checkMessageCount(prodNamespace, prodBrokerPod, "my-deletion1", 0, ADMIN, ADMIN_PASS);
        checkMessageCount(drNamespace, drBrokerPod, "my-deletion1", 0, ADMIN, ADMIN_PASS);
        checkMessageCount(drNamespace, drBrokerPod, "my-deletion2", 0, ADMIN, ADMIN_PASS);
        checkMessageCount(drNamespace, drBrokerPod, "different-queue-deletion-name", 0, ADMIN, ADMIN_PASS);

        prodBroker = ResourceManager.removeFromBrokerProperties(prodBroker, testAddresses, false);
        TestUtils.threadSleep(Constants.DURATION_10_SECONDS);

        assertThrows(NullPointerException.class, () -> {
            checkMessageCount(drNamespace, drBrokerPod, "my-deletion1", 0, ADMIN, ADMIN_PASS);
        });
        assertThrows(NullPointerException.class, () -> {
            checkMessageCount(drNamespace, drBrokerPod, "my-deletion2", 0, ADMIN, ADMIN_PASS);
        });
        assertThrows(NullPointerException.class, () -> {
            checkMessageCount(drNamespace, drBrokerPod, "different-queue-deletion-name", 0, ADMIN, ADMIN_PASS);
        });
    }

    @Test
    void addressFilteringTest() {
        LOGGER.info("=== Testing of DR mirror: Address creation & deletion ===");
        List<String> mirrorFilter = List.of("AMQPConnections.dr.connectionElements.mirror.addressFilter=\"am,x,lala,!eu\"");
        prodBroker = ResourceManager.addToBrokerProperties(prodBroker, mirrorFilter, true);
        Pod prodBrokerPod = getClient().getFirstPodByPrefixName(prodNamespace, prodBroker.getMetadata().getName());
        Pod drBrokerPod = getClient().getFirstPodByPrefixName(drNamespace, drBroker.getMetadata().getName());

        List<String> testAddresses = List.of(
                // Create missing queues for each address
                "addressConfigurations.eu.queueConfigs.eu.address=eu",
                "addressConfigurations.eu.queueConfigs.eu.routingType=ANYCAST",
                "addressConfigurations.am.queueConfigs.am.address=am",
                "addressConfigurations.am.queueConfigs.am.routingType=ANYCAST",

                "addressConfigurations.am.queueConfigs.br.address=am",
                "addressConfigurations.am.queueConfigs.br.routingType=ANYCAST",
                "addressConfigurations.am.queueConfigs.us.address=am",
                "addressConfigurations.am.queueConfigs.us.routingType=ANYCAST",

                "addressConfigurations.eu.queueConfigs.cz.address=eu",
                "addressConfigurations.eu.queueConfigs.cz.routingType=ANYCAST",
                "addressConfigurations.eu.queueConfigs.uk.address=eu",
                "addressConfigurations.eu.queueConfigs.uk.routingType=ANYCAST"
        );
        prodBroker = ResourceManager.addToBrokerProperties(prodBroker, testAddresses, false);

        LOGGER.info("=== Check predefined created queues. Present should be all on address am, none on eu ===");
        Map<String, Map<String, String>> prodQueues = getQueueStats(prodNamespace, prodBrokerPod, ADMIN, ADMIN_PASS);
        Map<String, Map<String, String>> drQueues = getQueueStats(drNamespace, drBrokerPod, ADMIN, ADMIN_PASS);

        assertEquals("am", prodQueues.get("am").get("address"));
        assertEquals("am", prodQueues.get("br").get("address"));
        assertEquals("am", prodQueues.get("us").get("address"));
        assertEquals("eu", prodQueues.get("cz").get("address"));
        assertEquals("eu", prodQueues.get("uk").get("address"));

        // TODO: WHY? BUG?
//        assertEquals("am", drQueues.get("am").get("address"));
//        assertEquals("am", drQueues.get("br").get("address"));
//        assertEquals("am", drQueues.get("us").get("address"));

        assertFalse(prodQueues.containsKey("sk"));
        assertFalse(prodQueues.containsKey("de"));
        assertFalse(drQueues.containsKey("de"));
        assertFalse(drQueues.containsKey("cz"));
        assertFalse(drQueues.containsKey("eu"));
        assertFalse(drQueues.containsKey("uk"));

        // Send messages
        int addressAmsgs = 5;
        LOGGER.info("[{}] Send {} messages to mirror-excluded address {}", prodNamespace, addressAmsgs, addressA);
        MessagingClient prodClientA = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, prodBrokerPod, allDefaultPort, addressA, addressAmsgs, ADMIN, ADMIN_PASS);
        int sentA = prodClientA.sendMessages();
        assertThat("Sent different amount of messages than expected", sentA, equalTo(addressAmsgs));

        LOGGER.info("[{}] Receive {} messages from excluded address {} (expecting fail)", drNamespace, addressAmsgs, addressA);
        MessagingClient drReceiverA = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, drBrokerPod, allDefaultPort, addressA, addressAmsgs, ADMIN, ADMIN_PASS);
        Throwable t = assertThrows(MessagingClientException.class, () -> drReceiverA.receiveMessages(Constants.DURATION_10_SECONDS));
        // Unable to parse number of message || Command execution timed out after 60000 ms
        assertThat(t.getMessage(), containsString("Unable to parse number of messages "));

        int receivedA = prodClientA.receiveMessages();
        assertThat("Received different amount of messages than expected", receivedA, equalTo(addressAmsgs));

        // Positive scenario send to new mirrored address/queue lala
        String lalaAddr = "lala";
        LOGGER.info("[{}] Send {} messages to mirrored address {}", prodNamespace, addressAmsgs, lalaAddr);
        MessagingClient lalaProdClient = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, prodBrokerPod, allDefaultPort, lalaAddr, addressAmsgs, ADMIN, ADMIN_PASS);
        int lalaSent = lalaProdClient.sendMessages();
        assertThat("Sent different amount of messages than expected", lalaSent, equalTo(addressAmsgs));

        LOGGER.info("[{}] Receive {} messages to excluded address {}", drNamespace, addressAmsgs, lalaAddr);
        MessagingClient lalaDrClient = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, drBrokerPod, allDefaultPort, lalaAddr, addressAmsgs, ADMIN, ADMIN_PASS);
        int lalaReceived = lalaDrClient.receiveMessages();
        assertThat("Received different amount of messages than expected", lalaReceived, equalTo(addressAmsgs));
    }

    @Test
    @Disabled("ENTMQBR-9474")
    void scaleUpDownTest() {
        int scaleUpSize = 4;
        int scaleDownSize = 2;
        // Scale to 4
        prodBroker = doArtemisScale(prodNamespace, prodBroker, 1, scaleUpSize);
        drBroker = doArtemisScale(drNamespace, drBroker, 1, scaleUpSize);

        // Send few messages & do checks
        int initialCountA = 200;
        int initialCountB = 50;

        int scaleupCountA = 150;
        int scaleupCountB = 40;

        prodBrokerPods = getClient().listPodsByPrefixName(prodNamespace, prodBroker.getMetadata().getName());
        drBrokerPods = getClient().listPodsByPrefixName(drNamespace, drBroker.getMetadata().getName());

        // Send messages
        int brokerCount = getClient().listPodsByPrefixName(prodNamespace, prodBroker.getMetadata().getName()).size();
        for (int i = 0; i < brokerCount; i++) {
            Pod prodBrokerPodI = prodBrokerPods.get(i);
            Pod drBrokerPodI = drBrokerPods.get(i);

            LOGGER.info("[{}] Send {} messages to {}/{}", prodNamespace, initialCountA, prodBrokerPodI.getMetadata().getName(), addressA);
            MessagingClient prodClientA = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, prodBrokerPodI, allDefaultPort, addressA, initialCountA, ADMIN, ADMIN_PASS);
            int sent0A = prodClientA.sendMessages();
            assertThat("Sent different amount of messages than expected", sent0A, equalTo(initialCountA));
            totalSentA += sent0A;

            LOGGER.info("[{}] Send {} messages to {}/{}", prodNamespace, initialCountB, prodBrokerPodI.getMetadata().getName(), addressB);
            MessagingClient prodClientB = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, prodBrokerPodI, allDefaultPort, addressB, initialCountB, ADMIN, ADMIN_PASS);
            int sent0B = prodClientB.sendMessages();
            assertThat("Sent different amount of messages than expected", sent0B, equalTo(initialCountB));
            totalSentB += sent0B;
        }
        updateCheckDeployment(initialCountA * scaleUpSize, initialCountB * scaleUpSize);
        receiveMessagesAB(prodBrokerPod, initialCountA - scaleupCountA, initialCountB - scaleupCountB);

        // Scale down to 2
        prodBroker = doArtemisScale(prodNamespace, prodBroker, scaleUpSize, scaleDownSize, true);
        drBroker = doArtemisScale(drNamespace, drBroker, scaleUpSize, scaleDownSize, true);
        // BUG: fails expected 750, got 400 messages. Message migration is not working with clustered -> false
        updateCheckDeployment(initialCountA * scaleUpSize - 50, initialCountB * scaleUpSize - 10);
        receiveMessagesAB(prodBrokerPod, 100, 30);

        // Scale down to 1
        prodBroker = doArtemisScale(prodNamespace, prodBroker, scaleDownSize, 1);
        drBroker = doArtemisScale(drNamespace, drBroker, scaleDownSize, 1);
        updateCheckDeployment(initialCountA * scaleUpSize, initialCountB * scaleUpSize);
        receiveMessagesAB(prodBrokerPod, 50, 10);

        assertThat("Total sentA != totalReceivedA!", totalSentA, equalTo(totalReceivedA));
        assertThat("Total sentB != totalReceivedB!", totalSentB, equalTo(totalReceivedB));
    }

    private void updateCheckDeployment(int expectedA, int expectedB) {
        prodBrokerPods = getClient().listPodsByPrefixName(prodNamespace, prodBroker.getMetadata().getName());
        drBrokerPods = getClient().listPodsByPrefixName(drNamespace, drBroker.getMetadata().getName());
        prodBrokerPod = prodBrokerPods.get(prodBrokerPods.size() - 1);
        drBrokerPod = drBrokerPods.get(drBrokerPods.size() - 1);
        Map<String, Map<String, String>> statsProd = getQueueStats(prodNamespace, prodBrokerPod, null, addressABPrefix, ADMIN, ADMIN_PASS, true);
        Map<String, Map<String, String>> statsDr = getQueueStats(drNamespace, drBrokerPod, null, addressABPrefix, ADMIN, ADMIN_PASS, true);
        checkClusteredMessageCount(prodBroker, drBroker, addressA, expectedA, ADMIN, ADMIN_PASS);
        checkClusteredMessageCount(prodBroker, drBroker, addressB, expectedB, ADMIN, ADMIN_PASS);
    }

    private void receiveMessagesAB(Pod prodBrokerPod, int receiveA, int receiveB) {
        LOGGER.info("[{}] Receive {} messages from {}/{}", drNamespace, receiveA, prodBrokerPod.getMetadata().getName(), addressA);
        MessagingClient prodClientA1 = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, prodBrokerPod, allDefaultPort, addressA, receiveA, ADMIN, ADMIN_PASS);
        int receivedA1 = prodClientA1.receiveMessages();
        assertThat("Received different amount of messages than expected", receivedA1, equalTo(50));
        totalReceivedA += receivedA1;

        LOGGER.info("[{}] Receive {} messages from {}/{}", drNamespace, receiveB, prodBrokerPod.getMetadata().getName(), addressB);
        MessagingClient prodClientB1 = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, prodBrokerPod, allDefaultPort, addressB, receiveB, ADMIN, ADMIN_PASS);
        int receivedB1 = prodClientB1.receiveMessages();
        assertThat("Received different amount of messages than expected", receivedB1, equalTo(10));
        totalReceivedB += receivedB1;
    }
}
