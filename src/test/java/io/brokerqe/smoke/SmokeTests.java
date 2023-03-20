/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.smoke;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisAddress;
import io.amq.broker.v1beta1.ActiveMQArtemisBuilder;
import io.amq.broker.v1beta1.activemqartemisspec.Acceptors;
import io.brokerqe.AbstractSystemTests;
import io.brokerqe.Constants;
import io.brokerqe.ResourceManager;
import io.brokerqe.clients.AmqpQpidClient;
import io.brokerqe.clients.BundledAmqpMessagingClient;
import io.brokerqe.clients.BundledCoreMessagingClient;
import io.brokerqe.clients.MessagingClient;
import io.brokerqe.operator.ArtemisFileProvider;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

public class SmokeTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(SmokeTests.class);
    private final String testNamespace = getRandomNamespaceName("smoke-tests", 6);

    @BeforeAll
    void setupClusterOperator() {
        setupDefaultClusterOperator(testNamespace);
    }

    @AfterAll
    void teardownClusterOperator() {
        teardownDefaultClusterOperator(testNamespace);
    }

    @Test
    @Disabled
    void brokerErrorTest() {
        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, "my-broken-artemis");
        broker.getSpec().getDeploymentPlan().setSize(3);
        broker = ResourceManager.getArtemisClient().inNamespace(testNamespace).resource(broker).createOrReplace();
        ResourceManager.waitForBrokerDeployment(testNamespace, broker, true);
        throw new RuntimeException("Throwing random exception, to trigger TestDataCollection.");
    }

    @Test
    void sendReceiveCoreMessageTest() {
        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, ArtemisFileProvider.getArtemisSingleExampleFile());
        ActiveMQArtemisAddress myAddress = ResourceManager.createArtemisAddress(testNamespace, ArtemisFileProvider.getAddressQueueExampleFile());

        String brokerName = broker.getMetadata().getName();
        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, brokerName);
        String allDefaultPort = getServicePortNumber(testNamespace, getArtemisServiceHdls(testNamespace, broker), "all");

        int msgsExpected = 100;
        MessagingClient messagingClientCore = new BundledCoreMessagingClient(brokerPod, brokerPod.getStatus().getPodIP(), allDefaultPort, myAddress.getSpec().getAddressName(), myAddress.getSpec().getQueueName(), msgsExpected);
        int sent = messagingClientCore.sendMessages();
        int received = messagingClientCore.receiveMessages();
        assertThat(sent, equalTo(msgsExpected));
        assertThat(sent, equalTo(received));
        assertThat(messagingClientCore.compareMessages(), is(true));

        ResourceManager.deleteArtemisAddress(testNamespace, myAddress);
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    @Test
    void sendReceiveAMQPMessageTest() {
        Acceptors amqpAcceptors = createAcceptor("amqp-owire-acceptor", "amqp,openwire", 5672);
        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
            .editOrNewMetadata()
                .withName("my-artemis")
                .withNamespace(testNamespace)
            .endMetadata()
            .editOrNewSpec()
                .editOrNewDeploymentPlan()
                    .withSize(1)
                    .withImage("placeholder")
                .endDeploymentPlan()
                .withAcceptors(List.of(amqpAcceptors))
            .endSpec()
            .build();

        broker = ResourceManager.createArtemis(testNamespace, broker, true, Constants.DURATION_2_MINUTES);
        ActiveMQArtemisAddress myAddress = ResourceManager.createArtemisAddress(testNamespace, ArtemisFileProvider.getAddressQueueExampleFile());
        // sending & receiving messages
        String brokerName = broker.getMetadata().getName();
        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, brokerName);

        // Get service/amqp acceptor name - svcName = "brokerName-XXXX-svc"
        Service amqp = getClient().getFirstServiceBrokerAcceptor(testNamespace, brokerName, "amqp-owire-acceptor");
        assertThat(amqp.getSpec().getPorts().get(0).getPort(), equalTo(5672));

        // Messaging tests
        int msgsExpected = 100;

        MessagingClient messagingClientAmqp = new BundledAmqpMessagingClient(brokerPod, brokerPod.getStatus().getPodIP(),
                amqp.getSpec().getPorts().get(0).getPort().toString(),
                myAddress.getSpec().getAddressName(), myAddress.getSpec().getQueueName(), msgsExpected);
        int sent = messagingClientAmqp.sendMessages();
        int received = messagingClientAmqp.receiveMessages();

        LOGGER.info("[{}] Sent {} - Received {}", testNamespace, sent, received);
        assertThat(sent, equalTo(msgsExpected));
        assertThat(sent, equalTo(received));
        assertThat(messagingClientAmqp.compareMessages(), is(true));

        ResourceManager.deleteArtemisAddress(testNamespace, myAddress);
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    @Test
    void subscriberMessageTest() {
        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, ArtemisFileProvider.getArtemisSingleExampleFile());
        ActiveMQArtemisAddress myAddress = ResourceManager.createArtemisAddress(testNamespace, ArtemisFileProvider.getAddressQueueExampleFile());

        String brokerName = broker.getMetadata().getName();
        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, brokerName);
        String allDefaultPort = getServicePortNumber(testNamespace, getArtemisServiceHdls(testNamespace, broker), "all");

        int msgsExpected = 100;
        MessagingClient messagingClientCore = new BundledCoreMessagingClient(brokerPod, brokerPod.getStatus().getPodIP(),
                allDefaultPort, myAddress.getSpec().getAddressName(), myAddress.getSpec().getQueueName(), msgsExpected);
        messagingClientCore.subscribe();
        int sent = messagingClientCore.sendMessages();
        int received = messagingClientCore.receiveMessages();

        LOGGER.info("[{}] Sent {} - Received {}", testNamespace, sent, received);
        assertThat(sent, equalTo(msgsExpected));
        assertThat(sent, equalTo(received));
        assertThat(messagingClientCore.compareMessages(), is(true));

        ResourceManager.deleteArtemisAddress(testNamespace, myAddress);
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    @Test
    void sendReceiveSystemTestsClientMessageTest() {
        Deployment clients = ResourceManager.deployClientsContainer(testNamespace);
        Pod clientsPod = getClient().getFirstPodByPrefixName(testNamespace, Constants.PREFIX_SYSTEMTESTS_CLIENTS);

        ActiveMQArtemis artemisBroker = ResourceManager.createArtemis(testNamespace, ArtemisFileProvider.getArtemisSingleExampleFile());
        Acceptors amqpAcceptors = createAcceptor("amqp-owire-acceptor", "amqp,openwire", 5672);
        artemisBroker = addAcceptorsWaitForPodReload(testNamespace, List.of(amqpAcceptors), artemisBroker);

        ActiveMQArtemisAddress myAddress = ResourceManager.createArtemisAddress(testNamespace, ArtemisFileProvider.getAddressQueueExampleFile());
        String brokerName = artemisBroker.getMetadata().getName();
        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, brokerName);

        int msgsExpected = 100;
        int sent = -1;
        int received = 0;

        // Publisher - Receiver
        MessagingClient messagingClient = new AmqpQpidClient(clientsPod, brokerPod.getStatus().getPodIP(), "5672", myAddress, msgsExpected);
        sent = messagingClient.sendMessages();
        received = messagingClient.receiveMessages();
        assertThat(sent, equalTo(msgsExpected));
        assertThat(sent, equalTo(received));
        assertThat(messagingClient.compareMessages(), is(true));

        // Subscriber - Publisher
        MessagingClient messagingSubscriberClient = new AmqpQpidClient(clientsPod, brokerPod.getStatus().getPodIP(), "5672", myAddress, msgsExpected);
        messagingSubscriberClient.subscribe();
        sent = messagingSubscriberClient.sendMessages();
        received = messagingSubscriberClient.receiveMessages();
        assertThat(sent, equalTo(msgsExpected));
        assertThat(sent, equalTo(received));
        assertThat(messagingSubscriberClient.compareMessages(), is(true));

        ResourceManager.undeployClientsContainer(testNamespace, clients);
        ResourceManager.deleteArtemisAddress(testNamespace, myAddress);
        ResourceManager.deleteArtemis(testNamespace, artemisBroker);
    }
}
