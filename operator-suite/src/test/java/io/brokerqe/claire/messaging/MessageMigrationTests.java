/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.messaging;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisAddress;
import io.amq.broker.v1beta1.activemqartemisspec.Env;
import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.clients.ClientType;
import io.brokerqe.claire.clients.MessagingClient;
import io.fabric8.kubernetes.api.model.Pod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class MessageMigrationTests extends AbstractSystemTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageMigrationTests.class);
    private final String testNamespace = getRandomNamespaceName("migration-tests", 3);

    @BeforeAll
    void setupClusterOperator() {
        setupDefaultClusterOperator(testNamespace);
        operator.setOperatorLogLevel("debug");
    }

    @AfterAll
    void teardownClusterOperator() {
        teardownDefaultClusterOperator(testNamespace);
    }

    @AfterEach
    void cleanResources() {
        cleanResourcesAfterTest(testNamespace);
    }

    @Test
    public void simpleScaledownMMTest() {
        int msgsExpected = 100;
        int initialSize = 3;
        int scaledDownSize = 1;
        ActiveMQArtemisAddress myAddress = ResourceManager.createArtemisAddress(testNamespace, "migration-scd", "migration-scd");
        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, "sd-broker", initialSize, false, false, true, true);

        String brokerName = broker.getMetadata().getName();
        List<Pod> brokerPods = getClient().listPodsByPrefixName(testNamespace, brokerName);
        Pod brokerPod1 = brokerPods.get(0);
        Pod brokerPod2 = brokerPods.get(1);
        String allDefaultPort = getServicePortNumber(testNamespace, getArtemisServiceHdls(testNamespace, broker), "all");

        LOGGER.info("[{}] Send {} messages to broker1", testNamespace, msgsExpected);
        MessagingClient messagingClientCore = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, brokerPod1, allDefaultPort, myAddress, msgsExpected);
        int sent1 = messagingClientCore.sendMessages();
        assertThat("Sent different amount of messages than expected", sent1, equalTo(msgsExpected));

        LOGGER.info("[{}] Send {} messages to broker2", testNamespace, msgsExpected);
        MessagingClient messagingClientCore2 = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, brokerPod2, allDefaultPort, myAddress, msgsExpected);
        int sent2 = messagingClientCore2.sendMessages();
        assertThat("Sent different amount of messages than expected", sent2, equalTo(msgsExpected));

        broker.getSpec().getDeploymentPlan().setSize(scaledDownSize);
        broker = ResourceManager.getArtemisClient().inNamespace(testNamespace).resource(broker).createOrReplace();
        waitForScaleDownDrainer(testNamespace, operator.getOperatorName(), brokerName, Constants.DURATION_2_MINUTES, initialSize, scaledDownSize);
        LOGGER.info("[{}] Performed Broker scaledown {} -> {}", testNamespace, initialSize, scaledDownSize);

        brokerPods = getClient().listPodsByPrefixName(testNamespace, brokerName);
        assertThat("Broker didn't scale down to expected size (1)", brokerPods.size(), equalTo(1));

        brokerPod1 = brokerPods.get(0);
        MessagingClient messagingClientCore3 = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, brokerPod1, allDefaultPort, myAddress, 200);
        int received = messagingClientCore3.receiveMessages();

        LOGGER.info("[{}] Sent total {} - Received {} ", testNamespace, sent1 + sent2, received);
        assertThat("Received different amount of messages than expected from post-MM", received, equalTo(200));
        assertThat("Received different amount of messages than expected from post-MM", received, equalTo(sent1 + sent2));

        ResourceManager.deleteArtemisAddress(testNamespace, myAddress);
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    private ActiveMQArtemis sendMessagesAndScaledown(ActiveMQArtemis broker, Pod pod, String allDefaultPort, ActiveMQArtemisAddress address, int msgExpected, int targetSize) {
        LOGGER.info("[{}] Send {} messages to {}", testNamespace, msgExpected, pod.getMetadata().getName());
        MessagingClient coreClient = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, pod, allDefaultPort, address, msgExpected);
        int sent = coreClient.sendMessages();
        assertThat("Sent different amount of messages than expected", sent, equalTo(msgExpected));
        // TODO assert number of messages! Check does nothing so far!
        Map<String, Map<String, String>> queueStats = checkMessageCount(testNamespace, pod);
        int initialSize = broker.getSpec().getDeploymentPlan().getSize();
        broker = doArtemisScale(testNamespace, broker, initialSize, targetSize);
        return broker;
    }

    @Test
    public void sequentialScaledownTest() {
        int initialSize = 4;
        int msgExpected = 100;

        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, "sd-broker", initialSize, false, false, true, true);
//        @Disabled("ENTMQBR-8195")
//        ActiveMQArtemisAddress myAddress = ResourceManager.createArtemisAddress(testNamespace, ArtemisFileProvider.getAddressQueueExampleFile());
        ActiveMQArtemisAddress myAddress = ResourceManager.createArtemisAddress(testNamespace, "migration-queue", "migration-queue");
        String brokerName = broker.getMetadata().getName();

        List<Pod> brokerPods = getClient().listPodsByPrefixName(testNamespace, brokerName);
        Pod pod0 = brokerPods.get(0);
        Pod pod1 = brokerPods.get(1);
        Pod pod2 = brokerPods.get(2);
        Pod pod3 = brokerPods.get(3);
        String allDefaultPort = getServicePortNumber(testNamespace, getArtemisServiceHdls(testNamespace, broker), "all");

        broker = sendMessagesAndScaledown(broker, pod3, allDefaultPort, myAddress, msgExpected, 3);
        for (Pod pod : getClient().listPodsByPrefixName(testNamespace, brokerName)) {
            // TODO check real MessageCount??
            checkMessageCount(testNamespace, pod);
        }
        MessagingClient receiver = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, pod0, allDefaultPort, myAddress, 100);
        int received = receiver.receiveMessages();
        assertThat("Received different amount of messages than expected", received, equalTo(msgExpected));

        broker = sendMessagesAndScaledown(broker, pod2, allDefaultPort, myAddress, msgExpected, 2);
        receiver = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, pod0, allDefaultPort, myAddress, 100);
        received = receiver.receiveMessages();
        assertThat("Received different amount of messages than expected", received, equalTo(msgExpected));

        broker = sendMessagesAndScaledown(broker, pod1, allDefaultPort, myAddress, msgExpected, 1);
        receiver = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, pod0, allDefaultPort, myAddress, 100);
        received = receiver.receiveMessages();
        assertThat("Received different amount of messages than expected", received, equalTo(msgExpected));

        ResourceManager.deleteArtemisAddress(testNamespace, myAddress);
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    @Test
    public void persistenceRestorationTest() {
        int initialSize = 2;
        int msgExpected = 100;

        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, "sd-broker", initialSize);
        ActiveMQArtemisAddress myAddress = ResourceManager.createArtemisAddress(testNamespace, "migration-prt", "migration-prt");

        String brokerName = broker.getMetadata().getName();
        List<Pod> brokerPods = getClient().listPodsByPrefixName(testNamespace, brokerName);
        Pod pod0 = brokerPods.get(0);
        Pod pod1 = brokerPods.get(1);
        String allDefaultPort = getServicePortNumber(testNamespace, getArtemisServiceHdls(testNamespace, broker), "all");

        LOGGER.info("[{}] Send {} messages to broker1", testNamespace, msgExpected);
        MessagingClient msgSender0 = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, pod0, allDefaultPort, myAddress, msgExpected);
        int sent0 = msgSender0.sendMessages();
        assertThat("Sent different amount of messages than expected", sent0, equalTo(msgExpected));

        LOGGER.info("[{}] Send {} messages to broker1", testNamespace, msgExpected);
        MessagingClient msgSender1 = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, pod1, allDefaultPort, myAddress, msgExpected);
        int sent1 = msgSender1.sendMessages();
        assertThat("Sent different amount of messages than expected", sent1, equalTo(msgExpected));

        doArtemisScale(testNamespace, broker, initialSize, 0);
        LOGGER.info("[{}] Scaled down Broker to 0", testNamespace);

        doArtemisScale(testNamespace, broker, 0, initialSize);
        LOGGER.info("[{}] Scaled up Broker to {}", testNamespace, initialSize);

        // Refresh Pods after the rescale
        brokerPods = getClient().listPodsByPrefixName(testNamespace, brokerName);
        LOGGER.debug("[{}] Found {} brokerPods", testNamespace, brokerPods.size());
        pod0 = brokerPods.get(0);
        pod1 = brokerPods.get(1);

        MessagingClient receiver = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, pod0, allDefaultPort, myAddress, 100);
        int received = receiver.receiveMessages();
        assertThat("Received different amount of messages than expected", received, equalTo(msgExpected));

        receiver = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, pod1, allDefaultPort, myAddress, 100);
        received = receiver.receiveMessages();
        assertThat("Received different amount of messages than expected", received, equalTo(msgExpected));

        ResourceManager.deleteArtemisAddress(testNamespace, myAddress);
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    @Test
    @Disabled("ENTMQBR-8106")
    public void enableMigrationRuntimeTest() {
        int initialSize = 1;
        int targetSize = 2;
        int msgExpected = 100;
        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, "sd-broker", initialSize);
        doArtemisScale(testNamespace, broker, initialSize, targetSize);

        ActiveMQArtemisAddress myAddress = ResourceManager.createArtemisAddress(testNamespace, "migration-runtime", "migration-runtime");

        String brokerName = broker.getMetadata().getName();
        List<Pod> brokerPods = getClient().listPodsByPrefixName(testNamespace, brokerName);
        Pod pod0 = brokerPods.get(0);
        Pod pod1 = brokerPods.get(1);

        String allDefaultPort = getServicePortNumber(testNamespace, getArtemisServiceHdls(testNamespace, broker), "all");
        LOGGER.info("[{}] Send {} messages to broker1", testNamespace, msgExpected);
        MessagingClient msgSender0 = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, pod0, allDefaultPort, myAddress, msgExpected);
        int sent0 = msgSender0.sendMessages();
        assertThat("Sent different amount of messages than expected", sent0, equalTo(msgExpected));

        LOGGER.info("[{}] Send {} messages to broker1", testNamespace, msgExpected);
        MessagingClient msgSender1 = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, pod1, allDefaultPort, myAddress, msgExpected);
        int sent1 = msgSender1.sendMessages();
        assertThat("Sent different amount of messages than expected", sent1, equalTo(msgExpected));

        doArtemisScale(testNamespace, broker, targetSize, 0);
        LOGGER.info("[{}] Scaled down Broker to 0", testNamespace);

        broker.getSpec().getDeploymentPlan().setMessageMigration(false);
        doArtemisScale(testNamespace, broker, 0, targetSize);
        brokerPods = getClient().listPodsByPrefixName(testNamespace, brokerName);
        pod0 = brokerPods.get(0);

        broker.getSpec().getDeploymentPlan().setSize(initialSize);
        broker = ResourceManager.getArtemisClient().inNamespace(testNamespace).resource(broker).createOrReplace();
        ResourceManager.waitForBrokerDeployment(testNamespace, broker, true, pod0, Constants.DURATION_5_MINUTES);

        MessagingClient receiver = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, pod0, allDefaultPort, myAddress, msgExpected * targetSize);
        int received = receiver.receiveMessages();
        assertThat("Received different amount of messages than expected", received, equalTo(msgExpected * targetSize));

        ResourceManager.deleteArtemisAddress(testNamespace, myAddress);
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    @Test
    @Disabled("ENTMQBR-8106")
    public void disableMigrationRuntimeTest() {
        int initialSize = 2;
        int msgExpected = 100;

        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, "sd-broker", initialSize, false, false, false, true);
        ActiveMQArtemisAddress myAddress = ResourceManager.createArtemisAddress(testNamespace, "migration-runtime-dis", "migration-runtime-dis");

        String brokerName = broker.getMetadata().getName();
        List<Pod> brokerPods = getClient().listPodsByPrefixName(testNamespace, brokerName);
        Pod pod0 = brokerPods.get(0);
        Pod pod1 = brokerPods.get(1);
        String allDefaultPort = getServicePortNumber(testNamespace, getArtemisServiceHdls(testNamespace, broker), "all");

        LOGGER.info("[{}] Send {} messages to broker0", testNamespace, msgExpected);
        MessagingClient msgSender0 = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, pod0, allDefaultPort, myAddress, msgExpected);
        int sent0 = msgSender0.sendMessages();
        assertThat("Sent different amount of messages than expected", sent0, equalTo(msgExpected));

        LOGGER.info("[{}] Send {} messages to broker1", testNamespace, msgExpected);
        MessagingClient msgSender1 = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, pod1, allDefaultPort, myAddress, msgExpected);
        int sent1 = msgSender1.sendMessages();
        assertThat("Sent different amount of messages than expected", sent1, equalTo(msgExpected));

        broker.getSpec().getDeploymentPlan().setMessageMigration(false);
        doArtemisScale(testNamespace, broker, initialSize, 1);
        LOGGER.info("[{}] Scaled down Broker to 1", testNamespace);

        MessagingClient receiver = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, pod0, allDefaultPort, myAddress, 100);
        int received = receiver.receiveMessages();
        assertThat("Received different amount of messages than expected", received, equalTo(msgExpected));

        ResourceManager.deleteArtemisAddress(testNamespace, myAddress);
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    @Test
    public void nonPersistentMessagesMMTest() {
        int initialSize = 2;
        int msgExpected = 100;

        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, "sd-broker", initialSize, false, false, false, true);
        ActiveMQArtemisAddress myAddress = ResourceManager.createArtemisAddress(testNamespace, "migration-non-pers", "migration-non-pers");

        String brokerName = broker.getMetadata().getName();
        List<Pod> brokerPods = getClient().listPodsByPrefixName(testNamespace, brokerName);
        Pod pod0 = brokerPods.get(0);
        Pod pod1 = brokerPods.get(1);
        String allDefaultPort = getServicePortNumber(testNamespace, getArtemisServiceHdls(testNamespace, broker), "all");

        LOGGER.info("[{}] Send {} messages to broker0", testNamespace, msgExpected);
        MessagingClient msgSender0 = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, pod0, allDefaultPort, myAddress, msgExpected, true);

        int sent0 = msgSender0.sendMessages();
        assertThat("Sent different amount of messages than expected", sent0, equalTo(msgExpected));

        LOGGER.info("[{}] Send {} messages to broker1", testNamespace, msgExpected * 2);
        MessagingClient msgSender1 = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, pod1, allDefaultPort, myAddress, msgExpected, true);
        MessagingClient msgSender1P = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, pod1, allDefaultPort, myAddress, msgExpected, false);

        int sent1 = msgSender1.sendMessages();
        int sentP = msgSender1P.sendMessages();
        assertThat("Sent different amount of messages than expected", sent1, equalTo(msgExpected));
        assertThat("Sent different amount of messages than expected", sentP, equalTo(msgExpected));

        LOGGER.info("[{}] Scale down Broker to 1", testNamespace);
        broker = doArtemisScale(testNamespace, broker, 2, 1);
        MessagingClient receiver = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, pod0, allDefaultPort, myAddress, 200);
        int received = receiver.receiveMessages();

        assertThat("Received different amount of messages than expected", received, equalTo(msgExpected * 2));

        ResourceManager.deleteArtemisAddress(testNamespace, myAddress);
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    @Test
    @Disabled("mkrutov: bug or fix needed")
    public void restartWithoutMigration() {
        int initialSize = 2;
        int msgExpected = 100;

        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, "sd-broker", initialSize);
        ActiveMQArtemisAddress myAddress = ResourceManager.createArtemisAddress(testNamespace, "no-migration", "no-migration");
        String brokerName = broker.getMetadata().getName();
        List<Pod> brokerPods = getClient().listPodsByPrefixName(testNamespace, brokerName);
        Pod pod1 = brokerPods.get(1);
        String allDefaultPort = getServicePortNumber(testNamespace, getArtemisServiceHdls(testNamespace, broker), "all");

        LOGGER.info("[{}] Send {} messages to broker0", testNamespace, msgExpected);
        MessagingClient msgSender0 = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, pod1, allDefaultPort, myAddress, msgExpected);

        int sent0 = msgSender0.sendMessages();
        assertThat("Sent different amount of messages than expected", sent0, equalTo(msgExpected));

        Env envPair = new Env();
        envPair.setName("testEnv");
        envPair.setValue("testValue");
        broker.getSpec().setEnv(new ArrayList<>());
        broker.getSpec().getEnv().add(envPair);
        broker = ResourceManager.getArtemisClient().inNamespace(testNamespace).resource(broker).createOrReplace();
        ResourceManager.waitForBrokerDeployment(testNamespace, broker, true, pod1, Constants.DURATION_5_MINUTES);

        brokerPods = getClient().listPodsByPrefixName(testNamespace, brokerName);
        pod1 = brokerPods.get(1);
        allDefaultPort = getServicePortNumber(testNamespace, getArtemisServiceHdls(testNamespace, broker), "all");
        MessagingClient receiver = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, pod1, allDefaultPort, myAddress, 100);
        int received = receiver.receiveMessages();
        assertThat("Received different amount of messages than expected", received, equalTo(100));
        ResourceManager.deleteArtemisAddress(testNamespace, myAddress);
        ResourceManager.deleteArtemis(testNamespace, broker);
    }
}
