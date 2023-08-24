/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.messaging;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisAddress;
import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.clients.ClientType;
import io.brokerqe.claire.clients.MessagingClient;
import io.brokerqe.claire.operator.ArtemisFileProvider;
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
    private final String testNamespace = getRandomNamespaceName("redistribution-tests", 3);

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
        List<Pod> brokerPods = getClient().listPodsByPrefixName(testNamespace, brokerName);
        Pod brokerPod1 = brokerPods.get(0);
        Pod brokerPod2 = brokerPods.get(1);
        String allDefaultPort = getServicePortNumber(testNamespace, getArtemisServiceHdls(testNamespace, broker), "all");

        // Messaging tests
        int msgsExpected = 3;
        MessagingClient messagingClientCore = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, brokerPod1, allDefaultPort, myAddress, msgsExpected);
        int sent = messagingClientCore.sendMessages();

        MessagingClient messagingClientCore2 = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, brokerPod2, allDefaultPort, myAddress, msgsExpected);
        int received = messagingClientCore2.receiveMessages();

        LOGGER.info("[{}] Sent {} - Received {}", testNamespace, sent, received);
        assertThat(sent, equalTo(msgsExpected));
        assertThat(sent, equalTo(received));
        assertTrue(messagingClientCore.compareMessages(sent, received));

        ResourceManager.deleteArtemisAddress(testNamespace, myAddress);
        ResourceManager.deleteArtemis(testNamespace, broker);
    }
}
