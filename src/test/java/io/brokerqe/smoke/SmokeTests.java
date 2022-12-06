/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.smoke;

import io.amq.broker.v2alpha3.ActiveMQArtemisAddress;
import io.brokerqe.AbstractSystemTests;
import io.brokerqe.Constants;
import io.brokerqe.ResourceManager;
import io.brokerqe.clients.BundledAmqpMessagingClient;
import io.brokerqe.clients.BundledCoreMessagingClient;
import io.brokerqe.clients.MessagingClient;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

public class SmokeTests extends AbstractSystemTests {

    private String testNamespace = "smoke-lala";

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
    }

    @Test
    void simpleBrokerDeploymentTest() {
        GenericKubernetesResource broker = createArtemisTypeless(testNamespace, operator.getArtemisSingleExamplePath());
//        ActiveMQArtemis broker = createArtemisTyped(testNamespace, artemisExampleFilePath, true);
        LOGGER.info(String.valueOf(broker));
        String brokerName = broker.getMetadata().getName();
        LOGGER.info("[{}] Check if broker pod with name {} is present.", testNamespace, brokerName);
        List<Pod> brokerPods = getClient().listPodsByPrefixInName(testNamespace, brokerName);
        assertThat(brokerPods.size(), is(1));

        deleteArtemisTypeless(testNamespace, brokerName);
//        deleteArtemisTyped(testNamespace, broker, true);
    }

    @Test
    void sendReceiveCoreMessageTest() {
        GenericKubernetesResource broker = createArtemisTypeless(testNamespace, operator.getArtemisSingleExamplePath());
        ActiveMQArtemisAddress myAddress = createArtemisAddress(testNamespace, operator.getArtemisAddressQueueExamplePath());

        String brokerName = broker.getMetadata().getName();
        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, brokerName);

        int msgsExpected = 100;
        MessagingClient messagingClientCore = new BundledCoreMessagingClient(brokerPod, brokerPod.getStatus().getPodIP(), "61616", myAddress.getSpec().getAddressName(), myAddress.getSpec().getQueueName(), msgsExpected);
        int sent = messagingClientCore.sendMessages();
        int received = messagingClientCore.receiveMessages();
        assertThat(sent, equalTo(msgsExpected));
        assertThat(messagingClientCore.compareMessages(), is(true));
    }

    public String brokerAcceptorConfigJoin() {
        return String.join(Constants.NEW_LINE_SEPARATOR,
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
        ActiveMQArtemisAddress myAddress = createArtemisAddress(testNamespace, operator.getArtemisAddressQueueExamplePath());

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
        GenericKubernetesResource broker = createArtemisTypeless(testNamespace, operator.getArtemisSingleExamplePath());
        ActiveMQArtemisAddress myAddress = createArtemisAddress(testNamespace, operator.getArtemisAddressQueueExamplePath());

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
    }
}
