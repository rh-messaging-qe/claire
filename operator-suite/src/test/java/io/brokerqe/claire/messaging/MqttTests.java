/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.messaging;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.activemqartemisspec.Acceptors;
import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.clients.CliJavaDeployment;
import io.brokerqe.claire.clients.DeployableClient;
import io.brokerqe.claire.clients.MessagingClient;
import io.brokerqe.claire.clients.container.MqttClient;
import io.brokerqe.claire.clients.container.MqttV5Client;
import io.brokerqe.claire.operator.ArtemisFileProvider;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

public class MqttTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttTests.class);
    private final String testNamespace = getRandomNamespaceName("mqtt-tests", 3);

    @BeforeAll
    void setupClusterOperator() {
        setupDefaultClusterOperator(testNamespace);
    }

    @AfterAll
    void teardownClusterOperator() {
        teardownDefaultClusterOperator(testNamespace);
    }

    @Test
    void brokerMqttV3ClientFullTest() {
        LOGGER.info("[{}] Starting MQTT V3 Broker test from hivemq mqtt client", testNamespace);
        Map<String, String> testOptions = Map.of(
                "all", "",
                "qosTries", "100000",
                "mqttVersion", "3",
                "timeOut", "120"
        );
        List<String> expectedKeyResults = List.of(
                "MQTT 3: OK",
                "QoS 0: Received 100000/100000 publishes",
                "QoS 1: Received 100000/100000 publishes",
                "QoS 2: Received 100000/100000 publishes",
                "Retain: OK",
                "Wildcard subscriptions: OK",
                "Shared subscriptions: OK",
                "Payload size: >= 100000 bytes",
                "Unsupported Ascii Chars: ALL SUPPORTED"
        );
        brokerMqttClientFullTest(testOptions, expectedKeyResults);
    }

    @Test
    @Disabled("ARTEMIS-4365")
    void brokerMqttV5ClientFullTest() {
        LOGGER.info("[{}] Starting MQTT V5 Broker test from hivemq mqtt client", testNamespace);
        Map<String, String> testOptions = Map.of(
                "all", "",
                "qosTries", "100000",
                "mqttVersion", "5",
                "timeOut", "120"
        );
        List<String> expectedKeyResults = List.of(
                "Connect restrictions: ",
            "> Retain: OK",
            "> Wildcard subscriptions: OK",
            "> Shared subscriptions: OK",
            "> Subscription identifiers: OK",
            "> Maximum QoS: 2",
            "> Receive maximum: 65535",
            "> Topic alias maximum: 65535",
            "> Session expiry interval: Client-based",
            "> Server keep alive: Client-based",
            "Maximum topic length: 65535 bytes",
            "QoS 0: Received 100000/100000 publishes in",
            "QoS 1: Received 100000/100000 publishes in",
            "QoS 2: Received 100000/100000 publishes in",
            "Retain: OK",
            "Wildcard subscriptions: OK",
            "Shared subscriptions: OK",
            "Payload size: >= 100000 bytes",
            "Maximum client id length: 65535 bytes",
            "Unsupported Ascii Chars: ALL SUPPORTED"
        );

        brokerMqttClientFullTest(testOptions, expectedKeyResults);
    }

    void brokerMqttClientFullTest(Map<String, String> testOptions, List<String> expectedResults) {
        Deployment clients = ResourceManager.deployClientsContainer(testNamespace);
        Pod clientsPod = getClient().getFirstPodByPrefixName(testNamespace, Constants.PREFIX_SYSTEMTESTS_CLIENTS);

        ActiveMQArtemis artemisBroker = ResourceManager.createArtemis(testNamespace, ArtemisFileProvider.getArtemisSingleExampleFile());
        Acceptors amqpAcceptors = createAcceptor("amqp-owire-acceptor", "amqp,openwire,mqtt", 5672);
        artemisBroker = addAcceptorsWaitForPodReload(testNamespace, List.of(amqpAcceptors), artemisBroker);
        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, artemisBroker.getMetadata().getName());

        DeployableClient deployableClient = new CliJavaDeployment(clientsPod.getMetadata().getNamespace());
        MessagingClient mqttBrokerTestClient = new MqttV5Client(deployableClient, brokerPod.getStatus().getPodIP(), "5672", testOptions);
        String result = ((MqttClient) mqttBrokerTestClient).testBroker();
        checkBrokerTestOutput(result, expectedResults);
        ResourceManager.undeployClientsContainer(testNamespace, clients);
        ResourceManager.deleteArtemis(testNamespace, artemisBroker);
    }

    private void checkBrokerTestOutput(String cmdOutput, List<String> expectedResults) {
        for (String result : expectedResults) {
            assertThat("Expected result is not present!", cmdOutput, containsString(result));
        }
    }
}
