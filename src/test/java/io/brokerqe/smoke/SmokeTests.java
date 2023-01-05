/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.smoke;

import io.amq.broker.v1beta1.activemqartemisspec.Acceptors;

import io.amq.broker.v1beta1.ActiveMQArtemisAddress;
import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.brokerqe.AbstractSystemTests;
import io.brokerqe.Constants;
import io.brokerqe.ResourceManager;
import io.brokerqe.TestUtils;
import io.brokerqe.clients.AmqpQpidClient;
import io.brokerqe.clients.BundledAmqpMessagingClient;
import io.brokerqe.clients.BundledCoreMessagingClient;
import io.brokerqe.clients.MessagingAmqpClient;
import io.brokerqe.clients.MessagingClient;
import io.brokerqe.operator.ArtemisFileProvider;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

public class SmokeTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(SmokeTests.class);
    private final String testNamespace = getRandomNamespaceName("smoke-tests", 6);

    @BeforeAll
    void setupClusterOperator() {
        getClient().createNamespace(testNamespace, true);
        LOGGER.info("[{}] Creating new namespace to {}", testNamespace, testNamespace);
        operator = getClient().deployClusterOperator(testNamespace);
    }

    @AfterAll
    void teardownClusterOperator() {
        getClient().undeployClusterOperator(ResourceManager.getArtemisClusterOperator(testNamespace));
        if (!ResourceManager.isClusterOperatorManaged()) {
            LOGGER.info("[{}] Deleting namespace to {}", testNamespace, testNamespace);
            getClient().deleteNamespace(testNamespace);
        }
        ResourceManager.undeployAllClientsContainers();
    }

    @Test
    void brokerErrorTest() {
        ActiveMQArtemis broker = createArtemisTyped(testNamespace, ArtemisFileProvider.getArtemisSingleExampleFile(), true);
        broker.getSpec().getDeploymentPlan().setSize(3);
        broker = ResourceManager.getArtemisClient().inNamespace(testNamespace).resource(broker).createOrReplace();
        TestUtils.threadSleep(Constants.DURATION_5_SECONDS); // give time to StatefulSet to update itself
        getKubernetesClient().resource(broker).inNamespace(testNamespace).waitUntilReady(2L, TimeUnit.MINUTES);
        throw new RuntimeException("Throwing random exception, to trigger TestDataCollection.");
    }

    @Test
    void simpleBrokerDeploymentTest() {
//        GenericKubernetesResource broker = createArtemisTypeless(testNamespace, ArtemisFileProvider.getArtemisSingleExampleFile());
        ActiveMQArtemis broker = createArtemisTyped(testNamespace, ArtemisFileProvider.getArtemisSingleExampleFile(), true);
        LOGGER.info(String.valueOf(broker));
        String brokerName = broker.getMetadata().getName();
        LOGGER.info("[{}] Check if broker pod with name {} is present.", testNamespace, brokerName);
        List<Pod> brokerPods = getClient().listPodsByPrefixInName(testNamespace, brokerName);
        assertThat(brokerPods.size(), is(1));

        deleteArtemis(testNamespace, broker, true);
    }

    @Test
    void sendReceiveCoreMessageTest() {
        GenericKubernetesResource broker = createArtemisTypeless(testNamespace, ArtemisFileProvider.getArtemisSingleExampleFile());
        ActiveMQArtemisAddress myAddress = createArtemisAddress(testNamespace, ArtemisFileProvider.getAddressQueueExampleFile());

        String brokerName = broker.getMetadata().getName();
        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, brokerName);

        int msgsExpected = 100;
        MessagingClient messagingClientCore = new BundledCoreMessagingClient(brokerPod, brokerPod.getStatus().getPodIP(), "61616", myAddress.getSpec().getAddressName(), myAddress.getSpec().getQueueName(), msgsExpected);
        int sent = messagingClientCore.sendMessages();
        int received = messagingClientCore.receiveMessages();
        assertThat(sent, equalTo(msgsExpected));
        assertThat(messagingClientCore.compareMessages(), is(true));

        deleteArtemisAddress(testNamespace, myAddress);
        deleteArtemisTypeless(testNamespace, brokerName);
    }

    public String brokerAcceptorConfigJoin() {
        return String.join(Constants.LINE_SEPARATOR,
                "apiVersion: broker.amq.io/v1beta1",
                "kind: ActiveMQArtemis",
                "metadata:",
                "  name: ex-aao",
                "spec:",
                "  deploymentPlan:",
                "    size: 1",
                "    image: placeholder",
                "  acceptors:",
                "    - name: my-acceptor",
                "      protocols: amqp,openwire",
                "      port: 5672"
                );
    }
    @Test
    void sendReceiveAMQPMessageTest() {
//  Example of editing GKR via edit -> Putting Maps, lists objects
//        GenericKubernetesResource object = getKubernetesClient().genericKubernetesResources(brokerCrdContextFromCrd).inNamespace(testNamespace).withName("walrus").edit(broker -> {
//            ((Map<String, Object>) broker.getAdditionalProperties("spec")).put("image", "my-updated-awesome-walrus-image");
//            return broker;
//        });
        final String brokerWithAmqpAcceptor = brokerAcceptorConfigJoin();
        InputStream targetStream = new ByteArrayInputStream(brokerWithAmqpAcceptor.getBytes());
        GenericKubernetesResource broker = createArtemisTypelessFromString(testNamespace, targetStream, true);
        ActiveMQArtemisAddress myAddress = createArtemisAddress(testNamespace, ArtemisFileProvider.getAddressQueueExampleFile());

        // sending & receiving messages
        String brokerName = broker.getMetadata().getName();
        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, brokerName);

        // Get service/amqp acceptor name - svcName = "brokerName-XXXX-svc"
        ArrayList<LinkedHashMap> acceptors = (ArrayList) ((Map) getKubernetesClient().resource(broker).get().getAdditionalProperties().get("spec")).get("acceptors");
        String acceptorName = (String) ((LinkedHashMap<?, ?>) acceptors.stream().filter(acceptor -> ((String) acceptor.get("protocols")).contains("amqp")).findFirst().get()).get("name");
        Service amqp = getClient().getServiceBrokerAcceptor(testNamespace, brokerName, acceptorName);

        // Messaging tests
        int msgsExpected = 100;

        MessagingClient messagingClientAmqp = new BundledAmqpMessagingClient(brokerPod, brokerPod.getStatus().getPodIP(), amqp.getSpec().getPorts().get(0).getPort().toString(), myAddress.getSpec().getAddressName(), myAddress.getSpec().getQueueName(), msgsExpected);
        int sent = messagingClientAmqp.sendMessages();
        messagingClientAmqp.receiveMessages();
        assertThat(sent, equalTo(msgsExpected));
        assertThat(messagingClientAmqp.compareMessages(), is(true));

        deleteArtemisAddress(testNamespace, myAddress);
        deleteArtemisTypeless(testNamespace, brokerName);
    }

    @Test
    void subscriberMessageTest() {
        GenericKubernetesResource broker = createArtemisTypeless(testNamespace, ArtemisFileProvider.getArtemisSingleExampleFile());
        ActiveMQArtemisAddress myAddress = createArtemisAddress(testNamespace, ArtemisFileProvider.getAddressQueueExampleFile());

        String brokerName = broker.getMetadata().getName();
        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, brokerName);

        int msgsExpected = 100;
        MessagingClient messagingClientCore = new BundledCoreMessagingClient(brokerPod, brokerPod.getStatus().getPodIP(), "61616", myAddress.getSpec().getAddressName(), myAddress.getSpec().getQueueName(), msgsExpected);
        messagingClientCore.subscribe();
        int sent = messagingClientCore.sendMessages();
        int received = messagingClientCore.receiveMessages();

        LOGGER.info("[{}] Sent {} - Received {}", testNamespace, sent, received);
        assertThat(sent, equalTo(msgsExpected));
        assertThat(messagingClientCore.compareMessages(), is(true));

        deleteArtemisAddress(testNamespace, myAddress);
        deleteArtemisTypeless(testNamespace, brokerName);
    }

    @Test
    void sendReceiveSystemTestsClientMessageTest() {
        Deployment clients = MessagingAmqpClient.deployClientsContainer(testNamespace);
        Pod clientsPod = getClient().getFirstPodByPrefixName(testNamespace, "systemtests-clients");

        Acceptors amqpAcceptors = new Acceptors();
        amqpAcceptors.setName("amqp-owire-acceptor");
        amqpAcceptors.setProtocols("amqp,openwire");
        amqpAcceptors.setPort(5672);

        ActiveMQArtemis artemisBroker = TestUtils.configFromYaml(ArtemisFileProvider.getArtemisSingleExampleFile(), ActiveMQArtemis.class);
//        List<Acceptors> acceptors = List.of(new AcceptorsBuilder().withName("amqp-owire-acceptor").withProtocols("amqp,openwire").withPort(5672).build());
        artemisBroker.getSpec().setAcceptors(List.of(amqpAcceptors));
        artemisBroker = ResourceManager.getArtemisClient().inNamespace(testNamespace).resource(artemisBroker).createOrReplace();
        waitForBrokerDeployment(testNamespace, artemisBroker);

        ActiveMQArtemisAddress myAddress = createArtemisAddress(testNamespace, ArtemisFileProvider.getAddressQueueExampleFile());
        String brokerName = artemisBroker.getMetadata().getName();
        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, brokerName);

        int msgsExpected = 100;
        int sent = -1;
        int received = 0;

        // Publisher - Receiver
        MessagingClient messagingClient = new AmqpQpidClient(clientsPod, brokerPod.getStatus().getPodIP(), "5672", myAddress.getSpec().getAddressName(), myAddress.getSpec().getQueueName(), msgsExpected);
        sent = messagingClient.sendMessages();
        received = messagingClient.receiveMessages();
        assertThat(sent, equalTo(msgsExpected));
        assertThat(sent, equalTo(received));
        assertThat(messagingClient.compareMessages(), is(true));

        // Subscriber - Publisher
        MessagingClient messagingSubscriberClient = new AmqpQpidClient(clientsPod, brokerPod.getStatus().getPodIP(), "5672", myAddress.getSpec().getAddressName(), myAddress.getSpec().getQueueName(), msgsExpected);
        messagingSubscriberClient.subscribe();
        sent = messagingSubscriberClient.sendMessages();
        received = messagingSubscriberClient.receiveMessages();
        assertThat(sent, equalTo(msgsExpected));
        assertThat(sent, equalTo(received));
        assertThat(messagingSubscriberClient.compareMessages(), is(true));

        MessagingAmqpClient.undeployClientsContainer(testNamespace, clients);
        deleteArtemisAddress(testNamespace, myAddress);
        deleteArtemis(testNamespace, artemisBroker);
    }
}
