/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.messaging;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisAddress;
import io.brokerqe.AbstractSystemTests;
import io.brokerqe.Constants;
import io.brokerqe.ResourceManager;
import io.brokerqe.clients.BundledCoreMessagingClient;
import io.brokerqe.clients.MessagingClient;
import io.brokerqe.operator.ArtemisFileProvider;
import io.fabric8.kubernetes.api.model.Pod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class MessageMigrationTests extends AbstractSystemTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageMigrationTests.class);
    private final String testNamespace = getRandomNamespaceName("migration-tests", 6);

    @BeforeAll
    void setupClusterOperator() {
        setupDefaultClusterOperator(testNamespace);
    }

    @AfterAll
    void teardownClusterOperator() {
        teardownDefaultClusterOperator(testNamespace);
    }

    @Test
    public void simpleScaledownMMTest() {
        int msgsExpected = 100;
        int initialSize = 3;
        int scaledDownSize = 1;
        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, "sd-broker", initialSize);
        ActiveMQArtemisAddress myAddress = ResourceManager.createArtemisAddress(testNamespace, ArtemisFileProvider.getAddressQueueExampleFile());

        String brokerName = broker.getMetadata().getName();
        List<Pod> brokerPods = getClient().listPodsByPrefixInName(testNamespace, brokerName);
        Pod brokerPod1 = brokerPods.get(0);
        Pod brokerPod2 = brokerPods.get(1);
        String allDefaultPort = getServicePortNumber(testNamespace, getArtemisServiceHdls(testNamespace, broker), "all");

        LOGGER.info("[{}] Send {} messages to broker1", testNamespace, msgsExpected);
        MessagingClient messagingClientCore = new BundledCoreMessagingClient(brokerPod1, brokerPod1.getStatus().getPodIP(),
                allDefaultPort, myAddress.getSpec().getAddressName(), myAddress.getSpec().getQueueName(), msgsExpected);
        int sent1 = messagingClientCore.sendMessages();
        assertThat(sent1, equalTo(msgsExpected));

        LOGGER.info("[{}] Send {} messages to broker2", testNamespace, msgsExpected);
        MessagingClient messagingClientCore2 = new BundledCoreMessagingClient(brokerPod2, brokerPod2.getStatus().getPodIP(),
                allDefaultPort, myAddress.getSpec().getAddressName(), myAddress.getSpec().getQueueName(), msgsExpected);
        int sent2 = messagingClientCore2.sendMessages();
        assertThat(sent2, equalTo(msgsExpected));

        broker.getSpec().getDeploymentPlan().setSize(scaledDownSize);
        broker = ResourceManager.getArtemisClient().inNamespace(testNamespace).resource(broker).createOrReplace();
        waitForScaleDownDrainer(testNamespace, operator.getOperatorName(), brokerName, Constants.DURATION_2_MINUTES, initialSize - scaledDownSize);
        LOGGER.info("[{}] Performed Broker scaledown {} -> {}", testNamespace, initialSize, scaledDownSize);

        brokerPods = getClient().listPodsByPrefixInName(testNamespace, brokerName);
        assertThat(brokerPods.size(), equalTo(1));

        brokerPod1 = brokerPods.get(0);
        MessagingClient messagingClientCore3 = new BundledCoreMessagingClient(brokerPod1, brokerPod1.getStatus().getPodIP(),
                allDefaultPort, myAddress.getSpec().getAddressName(), myAddress.getSpec().getQueueName(), 200);
        int received = messagingClientCore3.receiveMessages();

        LOGGER.info("[{}] Sent total {} - Received {} ", testNamespace, sent1 + sent2, received);
        assertThat(received, equalTo(200));
        assertThat(received, equalTo(sent1 + sent2));

        ResourceManager.deleteArtemisAddress(testNamespace, myAddress);
        ResourceManager.deleteArtemis(testNamespace, broker);
    }
}
