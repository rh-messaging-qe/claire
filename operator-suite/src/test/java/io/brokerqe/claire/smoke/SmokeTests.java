/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.smoke;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisAddress;
import io.amq.broker.v1beta1.ActiveMQArtemisBuilder;
import io.amq.broker.v1beta1.activemqartemisspec.Acceptors;
import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.KubernetesPlatform;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.clients.ClientType;
import io.brokerqe.claire.clients.MessagingClient;
import io.brokerqe.claire.exception.ClaireRuntimeException;
import io.brokerqe.claire.junit.DisabledTestPlatform;
import io.brokerqe.claire.junit.TestValidSince;
import io.brokerqe.claire.operator.ArtemisFileProvider;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.assertj.core.api.Assumptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

public class SmokeTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(SmokeTests.class);
    private final String testNamespace = getRandomNamespaceName("smoke-tests", 3);

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
        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, broker.getMetadata().getName());
        broker = ResourceManager.getArtemisClient().inNamespace(testNamespace).resource(broker).createOrReplace();
        ResourceManager.waitForBrokerDeployment(testNamespace, broker, false, brokerPod);
        throw new ClaireRuntimeException("Throwing random exception, to trigger TestDataCollection.");
    }

    @Test
    @Tag(Constants.TAG_SMOKE)
    void sendReceiveCoreMessageTest() {
        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, ArtemisFileProvider.getArtemisSingleExampleFile());
        ActiveMQArtemisAddress myAddress = ResourceManager.createArtemisAddress(testNamespace, ArtemisFileProvider.getAddressQueueExampleFile());

        String brokerName = broker.getMetadata().getName();
        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, brokerName);
        testMessaging(testNamespace, brokerPod, myAddress, 10);

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
        Integer amqpPort = amqp.getSpec().getPorts().get(0).getPort();
        assertThat(amqpPort, equalTo(5672));

        // Messaging tests
        int msgsExpected = 10;
        MessagingClient messagingClientAmqp = ResourceManager.createMessagingClient(ClientType.BUNDLED_AMQP, brokerPod,
                amqpPort.toString(), myAddress, msgsExpected);
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
    @Tag(Constants.TAG_SMOKE)
    void sendReceiveSystemTestsClientMessageTest() {
        Deployment clients = ResourceManager.deployClientsContainer(testNamespace);
        Pod clientsPod = getClient().getFirstPodByPrefixName(testNamespace, Constants.PREFIX_SYSTEMTESTS_CLIENTS);

        ActiveMQArtemis artemisBroker = ResourceManager.createArtemis(testNamespace, ArtemisFileProvider.getArtemisSingleExampleFile());
        Acceptors amqpAcceptors = createAcceptor("amqp-owire-acceptor", "amqp,openwire,mqtt", 5672);
        artemisBroker = addAcceptorsWaitForPodReload(testNamespace, List.of(amqpAcceptors), artemisBroker);

        ActiveMQArtemisAddress myAddress = ResourceManager.createArtemisAddress(testNamespace, ArtemisFileProvider.getAddressQueueExampleFile());
        String brokerName = artemisBroker.getMetadata().getName();
        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, brokerName);

        int msgsExpected = 10;
        // Publisher - Receiver
        LOGGER.info("[{}] Starting AMQP publisher - receiver test", testNamespace);
        MessagingClient messagingClient = ResourceManager.createMessagingClient(ClientType.ST_AMQP_QPID_JMS, clientsPod,
                brokerPod.getStatus().getPodIP(), "5672", myAddress, msgsExpected);
        int sent = messagingClient.sendMessages();
        int received = messagingClient.receiveMessages();
        assertThat(sent, equalTo(msgsExpected));
        assertThat(sent, equalTo(received));
        assertThat(messagingClient.compareMessages(), is(true));

        // Subscriber - Publisher
        LOGGER.info("[{}] Starting Bundled AMQP subscriber - publisher test", testNamespace);
        testMessaging(ClientType.BUNDLED_AMQP, testNamespace, brokerPod, myAddress, 10);

        LOGGER.info("[{}] Starting SystemTest clients AMQP subscriber - publisher test", testNamespace);
        testMessaging(ClientType.ST_AMQP_QPID_JMS, testNamespace, brokerPod, myAddress, 10);

        // MQTT Subscriber - Publisher
        LOGGER.info("[{}] Starting SystemTest clients MQTT subscriber - publisher test", testNamespace);
        msgsExpected = 1;
        MessagingClient messagingMqttClient = ResourceManager.createMessagingClient(ClientType.ST_MQTT_V5, clientsPod,
                brokerPod.getStatus().getPodIP(), "5672", myAddress, msgsExpected);
        messagingMqttClient.subscribe();
        sent = messagingMqttClient.sendMessages();
        messagingMqttClient.unsubscribe();
        received = messagingMqttClient.receiveMessages();
        assertThat(sent, equalTo(msgsExpected));
        assertThat(sent, equalTo(received));
        assertThat(messagingMqttClient.compareMessages(), is(true));

        ResourceManager.undeployClientsContainer(testNamespace, clients);
        ResourceManager.deleteArtemisAddress(testNamespace, myAddress);
        ResourceManager.deleteArtemis(testNamespace, artemisBroker);
    }

    @Test
    @Tag(Constants.TAG_SMOKE_CLIENTS)
    void sendReceiveSystemTestsProtonDotnetClientMessageTest() {
        Deployment clients = ResourceManager.deployCliProtonDotnetContainer(testNamespace);
        Pod protonDotnetClientPod = getClient().getFirstPodByPrefixName(testNamespace, Constants.PREFIX_SYSTEMTESTS_CLI_PROTON_DOTNET);

        assertThat(protonDotnetClientPod, is(notNullValue()));

        ActiveMQArtemis artemisBroker = ResourceManager.createArtemis(testNamespace, ArtemisFileProvider.getArtemisSingleExampleFile());
        Acceptors amqpAcceptors = createAcceptor("amqp-owire-acceptor", "amqp,openwire", 5672);
        artemisBroker = addAcceptorsWaitForPodReload(testNamespace, List.of(amqpAcceptors), artemisBroker);

        ActiveMQArtemisAddress myAddress = ResourceManager.createArtemisAddress(testNamespace, ArtemisFileProvider.getAddressQueueExampleFile());
        String brokerName = artemisBroker.getMetadata().getName();
        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, brokerName);

        int msgsExpected = 10;

        // Publisher - Receiver
        MessagingClient protonDotnetMessagingClient = ResourceManager.createMessagingClient(ClientType.ST_AMQP_PROTON_DOTNET, protonDotnetClientPod,
                brokerPod.getStatus().getPodIP(), "5672", myAddress, msgsExpected);
        int protonDotnetSent = protonDotnetMessagingClient.sendMessages();
        int protonDotnetReceived = protonDotnetMessagingClient.receiveMessages();
        assertThat(protonDotnetSent, equalTo(msgsExpected));
        assertThat(protonDotnetSent, equalTo(protonDotnetReceived));
        // TODO: Proton Dotnet does not print id's for received messages
        // assertThat(protonDotnetMessagingClient.compareMessages(), is(true));

        ResourceManager.undeployClientsContainer(testNamespace, clients);
        ResourceManager.deleteArtemisAddress(testNamespace, myAddress);
        ResourceManager.deleteArtemis(testNamespace, artemisBroker);
    }

    @Test
    @Tag(Constants.TAG_SMOKE_CLIENTS)
    @Disabled("Cli Proton Cpp does not support --log-msgs=json")
    void sendReceiveSystemTestsProtonCppClientMessageTest() {
        Deployment clients = ResourceManager.deployCliCppContainer(testNamespace);
        Pod cppClientPod = getClient().getFirstPodByPrefixName(testNamespace, Constants.PREFIX_SYSTEMTESTS_CLI_CPP);

        assertThat(cppClientPod, is(notNullValue()));

        ActiveMQArtemis artemisBroker = ResourceManager.createArtemis(testNamespace, ArtemisFileProvider.getArtemisSingleExampleFile());
        Acceptors amqpAcceptors = createAcceptor("amqp-owire-acceptor", "amqp,openwire", 5672);
        artemisBroker = addAcceptorsWaitForPodReload(testNamespace, List.of(amqpAcceptors), artemisBroker);

        ActiveMQArtemisAddress myAddress = ResourceManager.createArtemisAddress(testNamespace, ArtemisFileProvider.getAddressQueueExampleFile());
        String brokerName = artemisBroker.getMetadata().getName();
        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, brokerName);

        int msgsExpected = 10;

        // Publisher - Receiver
        MessagingClient protonCppMessagingClient = ResourceManager.createMessagingClient(ClientType.ST_AMQP_PROTON_CPP, cppClientPod,
                brokerPod.getStatus().getPodIP(), "5672", myAddress, msgsExpected);
        int protonCppSent = protonCppMessagingClient.sendMessages();
        int protonCppReceived = protonCppMessagingClient.receiveMessages();
        assertThat(protonCppSent, equalTo(msgsExpected));
        assertThat(protonCppSent, equalTo(protonCppReceived));
        // TODO: Proton Cpp does not print id's for received messages
        // assertThat(protonCppMessagingClient.compareMessages(), is(true));

        ResourceManager.undeployClientsContainer(testNamespace, clients);
        ResourceManager.deleteArtemisAddress(testNamespace, myAddress);
        ResourceManager.deleteArtemis(testNamespace, artemisBroker);
    }

    @Test
    @Tag(Constants.TAG_SMOKE_CLIENTS)
    void sendReceiveSystemTestsProtonPythonClientMessageTest() {
        Deployment clients = ResourceManager.deployCliProtonPythonContainer(testNamespace);
        Pod protonPythonClientPod = getClient().getFirstPodByPrefixName(testNamespace, Constants.PREFIX_SYSTEMTESTS_CLI_PROTON_PYTHON);

        assertThat(protonPythonClientPod, is(notNullValue()));

        ActiveMQArtemis artemisBroker = ResourceManager.createArtemis(testNamespace, ArtemisFileProvider.getArtemisSingleExampleFile());
        Acceptors amqpAcceptors = createAcceptor("amqp-owire-acceptor", "amqp,openwire", 5672);
        artemisBroker = addAcceptorsWaitForPodReload(testNamespace, List.of(amqpAcceptors), artemisBroker);

        ActiveMQArtemisAddress myAddress = ResourceManager.createArtemisAddress(testNamespace, ArtemisFileProvider.getAddressQueueExampleFile());
        String brokerName = artemisBroker.getMetadata().getName();
        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, brokerName);

        int msgsExpected = 10;

        // Publisher - Receiver
        MessagingClient protonPythonMessagingClient = ResourceManager.createMessagingClient(ClientType.ST_AMQP_PROTON_PYTHON, protonPythonClientPod,
                brokerPod.getStatus().getPodIP(), "5672", myAddress, msgsExpected);
        int protonPythonSent = protonPythonMessagingClient.sendMessages();
        int protonPythonReceived = protonPythonMessagingClient.receiveMessages();
        assertThat(protonPythonSent, equalTo(msgsExpected));
        assertThat(protonPythonSent, equalTo(protonPythonReceived));
        // TODO: Proton Python does not print id's for received messages
        // assertThat(protonPythonMessagingClient.compareMessages(), is(true));

        ResourceManager.undeployClientsContainer(testNamespace, clients);
        ResourceManager.deleteArtemisAddress(testNamespace, myAddress);
        ResourceManager.deleteArtemis(testNamespace, artemisBroker);
    }

    @Test
    @Tag(Constants.TAG_SMOKE_CLIENTS)
    void sendReceiveSystemTestsRheaClientMessageTest() {
        Deployment clients = ResourceManager.deployCliRheaContainer(testNamespace);
        Pod rheaClientPod = getClient().getFirstPodByPrefixName(testNamespace, Constants.PREFIX_SYSTEMTESTS_CLI_RHEA);

        assertThat(rheaClientPod, is(notNullValue()));

        ActiveMQArtemis artemisBroker = ResourceManager.createArtemis(testNamespace, ArtemisFileProvider.getArtemisSingleExampleFile());
        Acceptors amqpAcceptors = createAcceptor("amqp-owire-acceptor", "amqp,openwire", 5672);
        artemisBroker = addAcceptorsWaitForPodReload(testNamespace, List.of(amqpAcceptors), artemisBroker);

        ActiveMQArtemisAddress myAddress = ResourceManager.createArtemisAddress(testNamespace, ArtemisFileProvider.getAddressQueueExampleFile());
        String brokerName = artemisBroker.getMetadata().getName();
        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, brokerName);

        int msgsExpected = 10;

        // Publisher - Receiver
        MessagingClient rheaMessagingClient = ResourceManager.createMessagingClient(ClientType.ST_AMQP_RHEA, rheaClientPod,
                brokerPod.getStatus().getPodIP(), "5672", myAddress, msgsExpected);
        int rheaSent = rheaMessagingClient.sendMessages();
        int rheaReceived = rheaMessagingClient.receiveMessages();
        assertThat(rheaSent, equalTo(msgsExpected));
        assertThat(rheaSent, equalTo(rheaReceived));
        // TODO: Rhea does not print id's for received messages
        // assertThat(rheaMessagingClient.compareMessages(), is(true));

        ResourceManager.undeployClientsContainer(testNamespace, clients);
        ResourceManager.deleteArtemisAddress(testNamespace, myAddress);
        ResourceManager.deleteArtemis(testNamespace, artemisBroker);
    }

    @Test
    @Tag(Constants.TAG_SMOKE)
    void testDefaultBrokerVersion() {
        String expectedVersion = getExpectedVersion();
        assumeFalse(expectedVersion.equals("main"), "version supplied is \"main\", skipping test.");

        String expectedBrokerPattern = "Red Hat AMQ.*\\.GA";

        ActiveMQArtemis artemisBroker = ResourceManager.createArtemis(testNamespace, "smoke", 1);
        Pod brokerPod = getClient().listPodsByPrefixName(testNamespace, artemisBroker.getMetadata().getName()).get(0);
        LOGGER.info("[{}] Check expected version {} in {} pod logs", testNamespace, expectedVersion, brokerPod.getMetadata().getName());
        String brokerLogs = getClient().getLogsFromPod(brokerPod);
        Pattern versionPattern = Pattern.compile(expectedBrokerPattern);
        Matcher matcher = versionPattern.matcher(brokerLogs);
        if (matcher.find()) {
            String actualVersion = matcher.group();
            assertThat(String.format("Version (%s) didn't match the expected (%s) version", actualVersion, expectedVersion),
                    actualVersion, containsString(expectedVersion));
        } else {
            throw new AssertionError(String.format("Expected pattern %s was not found", expectedBrokerPattern));
        }
        ResourceManager.deleteArtemis(testNamespace, artemisBroker);
    }

    @Test
    @Tag(Constants.TAG_SMOKE)
    @TestValidSince(ArtemisVersion.VERSION_2_33)
    @DisabledTestPlatform(platforms = { KubernetesPlatform.KUBERNETES})
    void testConsoleProvidedVersion() {
        Assumptions.assumeThat(testEnvironmentOperator.isUpstreamArtemis()).isFalse();

        String brokerName = "smoke";
        ActiveMQArtemis artemis = new ActiveMQArtemisBuilder()
                .editOrNewMetadata()
                    .withName(brokerName)
                    .withNamespace(testNamespace)
                .endMetadata()
                .editOrNewSpec()
                    .editOrNewDeploymentPlan()
                        .withSize(1)
                        .withImage("placeholder")
                    .endDeploymentPlan()
                    .editOrNewConsole()
                        .withExpose(true)
                        .withSslEnabled(false)
                    .endConsole()
                .endSpec()
                .build();

        ResourceManager.createArtemis(testNamespace, artemis, true);

        HasMetadata webserviceUrl = getClient().getExternalAccessServicePrefixName(testNamespace,
                brokerName + "-" + ArtemisConstants.WEBCONSOLE_URI_PREFIX).get(0);
        String serviceUrl = "http://" + getClient().getExternalAccessServiceUrl(testNamespace, webserviceUrl.getMetadata().getName());
        waitConsoleReady(serviceUrl, 500, 5000);
        String url = serviceUrl + "/redhat-branding/plugin/amq-broker-version";
        checkHttpResponse(TestUtils.makeInsecureHttpsRequest(url), HttpURLConnection.HTTP_OK, testEnvironmentOperator.getArtemisVersion());

    }

    @Test
    @Tag(Constants.TAG_SMOKE)
    void testDefaultOperatorVersion() {
        String expectedVersion = getExpectedVersion();
        assumeFalse(expectedVersion.equals("main"), "version supplied is \"main\", skipping test.");
        String expectedOperatorVersionPattern = "Version of the operator: .*\n";
        Pod operatorPod = getClient().getFirstPodByPrefixName(testNamespace, operator.getOperatorName());

        LOGGER.info("[{}] Check expected version {} in {} pod logs", testNamespace, expectedVersion, operatorPod.getMetadata().getName());
        String operatorLogs = getClient().getLogsFromPod(operatorPod);
        Pattern versionPattern = Pattern.compile(expectedOperatorVersionPattern);
        Matcher matcher = versionPattern.matcher(operatorLogs);
        if (matcher.find()) {
            String actualVersion = matcher.group();
            assertThat(String.format("Version (%s) didn't match the expected (%s) version", actualVersion, expectedVersion),
                    actualVersion, containsString(expectedVersion));
        } else {
            throw new AssertionError(String.format("Expected pattern %s was not found", expectedOperatorVersionPattern));
        }
    }

}
