/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.smoke;

import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.client.deployment.ArtemisDeployment;
import io.brokerqe.claire.clients.DeployableClient;
import io.brokerqe.claire.clients.MessagingClient;
import io.brokerqe.claire.clients.bundled.ArtemisCommand;
import io.brokerqe.claire.clients.bundled.BundledAmqpMessagingClient;
import io.brokerqe.claire.clients.bundled.BundledArtemisClient;
import io.brokerqe.claire.clients.bundled.BundledClientOptions;
import io.brokerqe.claire.clients.bundled.BundledCoreMessagingClient;
import io.brokerqe.claire.clients.container.AmqpProtonCppClient;
import io.brokerqe.claire.clients.container.AmqpProtonDotnetClient;
import io.brokerqe.claire.clients.container.AmqpProtonPythonClient;
import io.brokerqe.claire.clients.container.AmqpQpidClient;
import io.brokerqe.claire.clients.container.AmqpRheaClient;
import io.brokerqe.claire.clients.container.CoreArtemisClient;
import io.brokerqe.claire.clients.container.OpenWireActiveMQClient;
import io.brokerqe.claire.client.deployment.BundledClientDeployment;
import io.brokerqe.claire.client.deployment.StCppClientDeployment;
import io.brokerqe.claire.client.deployment.StJavaClientDeployment;
import io.brokerqe.claire.client.deployment.StProtonDotnetClientDeployment;
import io.brokerqe.claire.client.deployment.StProtonPythonClientDeployment;
import io.brokerqe.claire.client.deployment.StRheaClientDeployment;
import io.brokerqe.claire.container.ArtemisContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClientsMessagingTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientsMessagingTests.class);
    private ArtemisContainer artemis;
    private final String address = "lala";
    private final String queue = "lala";
    private final String username = "admin";
    private final String password = "admin";

    @BeforeAll
    void setupEnv() {
        String artemisName = "artemis";
        LOGGER.info("Creating artemis instance: " + artemisName);
        artemis = ArtemisDeployment.createArtemis(artemisName);
    }

    @Test
    public void testAmqpJmsMessaging() {
        LOGGER.info("Test SystemTests AMQP Messaging");
        int msgsExpected = 5;
        DeployableClient deployableClient = new StJavaClientDeployment();
        MessagingClient messagingClient = new AmqpQpidClient(deployableClient, artemis.getName(), DEFAULT_ALL_PORT, address, queue, msgsExpected, username, password);
        int sent = messagingClient.sendMessages();
        int received = messagingClient.receiveMessages();
        assertThat(sent, equalTo(msgsExpected));
        assertThat(sent, equalTo(received));
        assertTrue(messagingClient.compareMessages());
    }

    @Test
    public void testCoreJmsMessaging() {
        LOGGER.info("Test SystemTests Core Messaging");
        int msgsExpected = 5;
        DeployableClient deployableClient = new StJavaClientDeployment();
        MessagingClient messagingClient = new CoreArtemisClient(deployableClient, artemis.getName(), DEFAULT_ALL_PORT, address, queue, msgsExpected, username, password);
        int sent = messagingClient.sendMessages();
        int received = messagingClient.receiveMessages();
        assertThat(sent, equalTo(msgsExpected));
        assertThat(sent, equalTo(received));
        assertTrue(messagingClient.compareMessages());
    }

    @Test
    public void testOpenwireJmsMessaging() {
        LOGGER.info("Test SystemTests Openwire Messaging");
        int msgsExpected = 5;
        DeployableClient deployableClient = new StJavaClientDeployment();
        MessagingClient messagingClient = new OpenWireActiveMQClient(deployableClient, artemis.getName(), DEFAULT_ALL_PORT, address, queue, msgsExpected, username, password);
        int sent = messagingClient.sendMessages();
        int received = messagingClient.receiveMessages();
        assertThat(sent, equalTo(msgsExpected));
        assertThat(sent, equalTo(received));
        assertTrue(messagingClient.compareMessages());
    }

    @Test
    public void testAmqpProtonDotnetMessaging() {
        LOGGER.info("Test SystemTests AMQP Proton Dotnet Messaging");

        LOGGER.debug("Creating anycast queue for the client, otherwise multicast would be auto created");
        Map<String, String> commandOptions = Map.of(
                "name", address,
                "address", address,
                ArtemisConstants.ROUTING_TYPE_ANYCAST, "",
                "no-durable", "",
                "preserve-on-no-consumers", "",
                "auto-create-address", ""
        );
        BundledArtemisClient bac = new BundledArtemisClient(new BundledClientDeployment(), ArtemisCommand.QUEUE_CREATE, commandOptions);
        bac.executeCommand();

        LOGGER.debug("Running Proton Dotnet client");
        int msgsExpected = 5;
        DeployableClient deployableClient = new StProtonDotnetClientDeployment();
        MessagingClient messagingClient = new AmqpProtonDotnetClient(deployableClient, artemis.getName(), DEFAULT_ALL_PORT, address, queue, msgsExpected, username, password);
        int sent = messagingClient.sendMessages();
        int received = messagingClient.receiveMessages();
        assertThat(sent, equalTo(msgsExpected));
        assertThat(sent, equalTo(received));
        // TODO: Proton Dotnet does not print id's for received messages
        // assertTrue(messagingClient.compareMessages());
    }

    @Test
    @Disabled("Cli Proton Cpp does not support --log-msgs=json")
    public void testAmqpProtonCppMessaging() {
        LOGGER.info("Test SystemTests AMQP Proton Cpp Messaging");

        LOGGER.debug("Creating anycast queue for the client, otherwise multicast would be autocreated");
        Map<String, String> commandOptions = Map.of(
                "name", address,
                "address", address,
                ArtemisConstants.ROUTING_TYPE_ANYCAST, "",
                "no-durable", "",
                "preserve-on-no-consumers", "",
                "auto-create-address", ""
        );
        BundledArtemisClient bac = new BundledArtemisClient(new BundledClientDeployment(), ArtemisCommand.QUEUE_CREATE, commandOptions);
        bac.executeCommand();

        LOGGER.debug("Running Proton Cpp client");
        int msgsExpected = 5;
        DeployableClient deployableClient = new StCppClientDeployment();
        MessagingClient messagingClient = new AmqpProtonCppClient(deployableClient, artemis.getName(), DEFAULT_ALL_PORT, address, queue, msgsExpected, username, password);
        int sent = messagingClient.sendMessages();
        int received = messagingClient.receiveMessages();
        assertThat(sent, equalTo(msgsExpected));
        assertThat(sent, equalTo(received));
        // TODO: Proton Cpp does not print id's for received messages
        // assertTrue(messagingClient.compareMessages());
    }

    @Test
    @Disabled("java.lang.NullPointerException: at org.apache.activemq.artemis.protocol.amqp.broker.AMQPSessionCallback.getAddress(AMQPSessionCallback.java:727)")
    public void testAmqpProtonPythonMessaging() {
        LOGGER.info("Test SystemTests AMQP Proton Python Messaging");

        LOGGER.debug("Creating anycast queue for the client, otherwise multicast would be autocreated");
        Map<String, String> commandOptions = Map.of(
                "name", address,
                "address", address,
                ArtemisConstants.ROUTING_TYPE_ANYCAST, "",
                "no-durable", "",
                "preserve-on-no-consumers", "",
                "auto-create-address", ""
        );
        BundledArtemisClient bac = new BundledArtemisClient(new BundledClientDeployment(), ArtemisCommand.QUEUE_CREATE, commandOptions);
        bac.executeCommand();

        LOGGER.debug("Running Proton Python client");
        int msgsExpected = 5;
        DeployableClient deployableClient = new StProtonPythonClientDeployment();
        MessagingClient messagingClient = new AmqpProtonPythonClient(deployableClient, artemis.getName(), DEFAULT_ALL_PORT, address, queue, msgsExpected, username, password);
        int sent = messagingClient.sendMessages();
        int received = messagingClient.receiveMessages();
        assertThat(sent, equalTo(msgsExpected));
        assertThat(sent, equalTo(received));
        // TODO: Proton Python does not print id's for received messages
        // assertTrue(messagingClient.compareMessages());
    }

    @Test
    public void testAmqpRheaMessaging() {
        LOGGER.info("Test SystemTests AMQP Rhea Messaging");

        LOGGER.debug("Creating anycast queue for the client, otherwise multicast would be autocreated");
        Map<String, String> commandOptions = Map.of(
                "name", address,
                "address", address,
                ArtemisConstants.ROUTING_TYPE_ANYCAST, "",
                "no-durable", "",
                "preserve-on-no-consumers", "",
                "auto-create-address", ""
        );
        BundledArtemisClient bac = new BundledArtemisClient(new BundledClientDeployment(), ArtemisCommand.QUEUE_CREATE, commandOptions);
        bac.executeCommand();

        LOGGER.debug("Running Rhea client");
        int msgsExpected = 5;
        DeployableClient deployableClient = new StRheaClientDeployment();
        MessagingClient messagingClient = new AmqpRheaClient(deployableClient, artemis.getName(), DEFAULT_ALL_PORT, address, queue, msgsExpected, username, password);
        int sent = messagingClient.sendMessages();
        int received = messagingClient.receiveMessages();
        assertThat(sent, equalTo(msgsExpected));
        assertThat(sent, equalTo(received));
        // TODO: Rhea does not print id's for received messages
        // assertTrue(messagingClient.compareMessages());
    }

    @Test
    public void testBundledCoreMessaging() {
        LOGGER.info("Test Bundled Core Messaging");
        int msgsExpected = 5;
        DeployableClient deployableClient = new BundledClientDeployment();
        BundledClientOptions options = new BundledClientOptions()
                .withDeployableClient(deployableClient)
                .withDestinationAddress(address)
                .withDestinationPort(DEFAULT_ALL_PORT)
                .withMessageCount(msgsExpected)
                .withPassword(password)
                .withUsername(username)
                .withDestinationQueue(queue)
                .withDestinationUrl(artemis.getName())
                .withTimeout(0);
        MessagingClient bundledClient = new BundledCoreMessagingClient(options);
        int sent = bundledClient.sendMessages();
        int received = bundledClient.receiveMessages();
        assertThat(sent, equalTo(msgsExpected));
        assertThat(sent, equalTo(received));
        assertTrue(bundledClient.compareMessages());
    }

    @Test
    public void testBundledAmqpMessaging() {
        LOGGER.info("Test Bundled AMQP Messaging");
        int msgsExpected = 5;
        DeployableClient deployableClient = new BundledClientDeployment();
        BundledClientOptions options = new BundledClientOptions()
                .withDeployableClient(deployableClient)
                .withDestinationAddress(address)
                .withDestinationPort(DEFAULT_ALL_PORT)
                .withMessageCount(msgsExpected)
                .withPassword(password)
                .withUsername(username)
                .withDestinationQueue(queue)
                .withDestinationUrl(artemis.getName())
                .withTimeout(0);
        MessagingClient bundledClient = new BundledAmqpMessagingClient(options);
        int sent = bundledClient.sendMessages();
        int received = bundledClient.receiveMessages();
        assertThat(sent, equalTo(msgsExpected));
        assertThat(sent, equalTo(received));
        assertTrue(bundledClient.compareMessages());
    }

}
