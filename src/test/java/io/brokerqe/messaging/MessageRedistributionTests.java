/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.messaging;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisAddress;
import io.brokerqe.AbstractSystemTests;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MessageRedistributionTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageRedistributionTests.class);
    private final String testNamespace = getRandomNamespaceName("redistribution-tests", 6);

    @BeforeAll
    void setupClusterOperator() {
        setupDefaultClusterOperator(testNamespace);
    }

    @AfterAll
    void teardownClusterOperator() {
        teardownDefaultClusterOperator(testNamespace);
    }

    @Test
    void simpleRedistributionTest() {
        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, "my-broker", 2);
        ActiveMQArtemisAddress myAddress = ResourceManager.createArtemisAddress(testNamespace, ArtemisFileProvider.getAddressQueueExampleFile());

        String brokerName = broker.getMetadata().getName();
        List<Pod> brokerPods = getClient().listPodsByPrefixInName(testNamespace, brokerName);
        Pod brokerPod1 = brokerPods.get(0);
        Pod brokerPod2 = brokerPods.get(1);
        String allDefaultPort = getServicePortNumber(testNamespace, getArtemisServiceHdls(testNamespace, broker), "all");

        // Messaging tests
        int msgsExpected = 3;
        MessagingClient messagingClientCore = new BundledCoreMessagingClient(brokerPod1, brokerPod1.getStatus().getPodIP(), allDefaultPort, myAddress.getSpec().getAddressName(), myAddress.getSpec().getQueueName(), msgsExpected);
        int sent = messagingClientCore.sendMessages();

        MessagingClient messagingClientCore2 = new BundledCoreMessagingClient(brokerPod2, brokerPod2.getStatus().getPodIP(), allDefaultPort, myAddress.getSpec().getAddressName(), myAddress.getSpec().getQueueName(), msgsExpected);
        int received = messagingClientCore2.receiveMessages();

        LOGGER.info("[{}] Sent {} - Received {}", testNamespace, sent, received);
        assertThat(sent, equalTo(msgsExpected));
        assertThat(sent, equalTo(received));
        assertTrue(messagingClientCore.compareMessages(sent, received));

        ResourceManager.deleteArtemisAddress(testNamespace, myAddress);
        ResourceManager.deleteArtemis(testNamespace, broker);
    }
}
