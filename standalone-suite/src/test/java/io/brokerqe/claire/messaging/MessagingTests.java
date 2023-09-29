/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.messaging;

import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.Environment;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.client.deployment.BundledClientDeployment;
import io.brokerqe.claire.client.deployment.StJavaClientDeployment;
import io.brokerqe.claire.clients.DeployableClient;
import io.brokerqe.claire.clients.MessagingClient;
import io.brokerqe.claire.clients.bundled.ArtemisCommand;
import io.brokerqe.claire.clients.bundled.BundledAmqpMessagingClient;
import io.brokerqe.claire.clients.bundled.BundledArtemisClient;
import io.brokerqe.claire.clients.bundled.BundledClientOptions;
import io.brokerqe.claire.clients.bundled.BundledCoreMessagingClient;
import io.brokerqe.claire.clients.container.AmqpQpidClient;
import io.brokerqe.claire.clients.container.CoreArtemisClient;
import io.brokerqe.claire.clients.container.OpenWireActiveMQClient;
import io.brokerqe.claire.container.ArtemisContainer;
import io.brokerqe.claire.container.database.ProvidedDatabase;
import io.brokerqe.claire.database.Database;
import io.brokerqe.claire.junit.TestDisabledOnProvidedDb;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MessagingTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessagingTests.class);
    private ArtemisContainer artemis;
    private String brokerUri;
    private DeployableClient stDeployableClient;
    private BundledClientDeployment artemisDeployableClient;
    private final String address = "lala";
    private final String queue = "lala";
    private final String username = ArtemisConstants.ADMIN_NAME;
    private final String password = ArtemisConstants.ADMIN_PASS;

    @BeforeAll
    void setupEnv() {
        String artemisName = "artemis";
        LOGGER.info("Creating artemis instance: " + artemisName);
        artemis = getArtemisInstance(artemisName);
        // BrokerService.getAmqpPort?
        brokerUri = Constants.AMQP_URL_PREFIX + artemis.getName() + ":" + DEFAULT_AMQP_PORT;
        stDeployableClient = new StJavaClientDeployment();
        artemisDeployableClient = new BundledClientDeployment();
    }


    @Test
    @Tag(Constants.TAG_SMOKE)
    @Tag(Constants.TAG_JDBC)
    public void testAmqpJmsMessaging() {
        LOGGER.info("Test SystemTests AMQP Messaging");
        int msgsExpected = 5;
        MessagingClient messagingClient = new AmqpQpidClient(stDeployableClient, artemis.getName(), DEFAULT_ALL_PORT, address, queue, msgsExpected, username, password);
        int sent = messagingClient.sendMessages();
        int received = messagingClient.receiveMessages();
        assertThat(sent, equalTo(msgsExpected));
        assertThat(sent, equalTo(received));
        assertTrue(messagingClient.compareMessages());
    }

    @Test
    @Tag(Constants.TAG_SMOKE)
    @Tag(Constants.TAG_JDBC)
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
    @Tag(Constants.TAG_SMOKE)
    @Tag(Constants.TAG_JDBC)
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
    @Tag(Constants.TAG_SMOKE)
    @Tag(Constants.TAG_JDBC)
    public void testBundledCoreMessaging() {
        LOGGER.info("Test Bundled Core Messaging");
        int msgsExpected = 5;
        BundledClientOptions options = new BundledClientOptions()
                .withDeployableClient(artemisDeployableClient)
                .withDestinationAddress(address)
                .withDestinationPort(DEFAULT_ALL_PORT)
                .withMessageCount(msgsExpected)
                .withPassword(password)
                .withUsername(username)
                .withDestinationQueue(queue)
                .withDestinationUrl(artemis.getName());
        MessagingClient bundledClient = new BundledCoreMessagingClient(options);
        int sent = bundledClient.sendMessages();
        int received = bundledClient.receiveMessages();
        assertThat(sent, equalTo(msgsExpected));
        assertThat(sent, equalTo(received));
        assertTrue(bundledClient.compareMessages());
    }

    @Test
    @Tag(Constants.TAG_SMOKE)
    @Tag(Constants.TAG_JDBC)
    public void testBundledAmqpMessaging() {
        LOGGER.info("Test Bundled AMQP Messaging");
        int msgsExpected = 5;
        BundledClientOptions options = new BundledClientOptions()
                .withDeployableClient(artemisDeployableClient)
                .withDestinationAddress(address)
                .withDestinationPort(DEFAULT_ALL_PORT)
                .withMessageCount(msgsExpected)
                .withPassword(password)
                .withUsername(username)
                .withDestinationQueue(queue)
                .withDestinationUrl(artemis.getName());
        MessagingClient bundledClient = new BundledAmqpMessagingClient(options);
        int sent = bundledClient.sendMessages();
        int received = bundledClient.receiveMessages();
        assertThat(sent, equalTo(msgsExpected));
        assertThat(sent, equalTo(received));
        assertTrue(bundledClient.compareMessages());
    }

    private void doSendReceiveDurableMsgQueue(String address, int messageCount, String messageContent) {
        doSendReceiveDurableMsgQueue(address, messageCount, messageContent, null, 0, null);
    }

    private void doSendReceiveDurableMsgQueue(String address, int messageCount, String fileName, int size, String unit) {
        doSendReceiveDurableMsgQueue(address, messageCount, null, fileName, size, unit);
    }

    private void doSendReceiveDurableMsgQueue(String address, int messageCount, String messageContent, String fileName, int size, String unit) {
         /*
        Auxiliary method to send & receive durable message into to a queue.
        Broker restart is placed in between of send & receive operations.

        Steps:
        1) Send a durable message to the addressed node
        2) Restart broker
        3) Read sent durable message
         */
        String testBrokerUri = brokerUri;
        Map<String, String> artemisCreateQueueOptions = new HashMap<>(Map.of(
                "name", address,
                "address", address,
                "anycast", "",
                "durable", "",
                "auto-create-address", "",
                "preserve-on-no-consumers", ""
        ));
        Map<String, String> artemisDeleteQueueOptions = new HashMap<>(Map.of(
                "name", address
        ));
        Map<String, String> artemisQueueStatOptions = new HashMap<>(Map.of(
                "maxColumnSize", "-1",
                "maxRows", "1000"
        ));
        Map<String, String> senderOptions = new HashMap<>(Map.of(
                "conn-username", username,
                "conn-password", password,
                "address", address,
                "count", String.valueOf(messageCount),
                "msg-durable", "true",
                "msg-content-hashed", "true",
                "conn-heartbeat", "180"
        ));

        Map<String, String> receiverOptions = new HashMap<>(Map.of(
                "conn-username", username,
                "conn-password", password,
                "address", address,
                "count", String.valueOf(messageCount),
                "msg-content-hashed", "true",
                "conn-heartbeat", "180"
        ));

        Duration timeout = calculateStartupTimeout(size, unit, messageCount);
        if (messageContent != null) {
            senderOptions.put("msg-content", messageContent);
            receiverOptions.put("timeout", String.valueOf(timeout.toSeconds()));
        } else {
            if (fileName != null && size != 0) {
                String contentFile = stDeployableClient.createFile(fileName, size, unit);
                senderOptions.put("msg-content-from-file", contentFile);
                testBrokerUri += "?jms.clientID=largeMessage&jms.prefetchPolicy.all=1";
            }
        }

        BundledArtemisClient artemisClient = new BundledArtemisClient(artemisDeployableClient, ArtemisCommand.QUEUE_CREATE, username, password, artemisCreateQueueOptions);
        artemisClient.executeCommand();

        LOGGER.info("[{}] Sending {} durable messages to {}.", artemis.getName(), senderOptions.get("count"), address);
        MessagingClient messagingClient = new AmqpQpidClient(stDeployableClient, testBrokerUri, senderOptions, receiverOptions);
        int sent = messagingClient.sendMessages();

        artemisClient = new BundledArtemisClient(artemisDeployableClient, ArtemisCommand.QUEUE_STAT, username, password, artemisQueueStatOptions);
        artemisClient.executeCommand();

        artemis.restartWithStop(timeout);
        ensureBrokerIsLive(artemis);

        artemisClient.executeCommand();

        LOGGER.info("[{}] Receiving {} durable messages from {}.", artemis.getName(), receiverOptions.get("count"), address);
        int received = messagingClient.receiveMessages();

        assertThat(received, equalTo(sent));
        assertTrue(messagingClient.compareMessages(messagingClient.getSentMessages(), messagingClient.getReceivedMessages()));

        artemisClient = new BundledArtemisClient(artemisDeployableClient, ArtemisCommand.QUEUE_DELETE, artemisDeleteQueueOptions);
        artemisClient.executeCommand();
    }

    private Duration calculateStartupTimeout(int size, String unit, int messageCount) {
        int seconds = messageCount;
        if (size != 0) {
            seconds *= size;
        }
        if (unit != null) {
            switch (unit) {
                case "KiB" -> seconds *= 0.5;
                case "MiB" -> seconds *= 15;
                case "GiB" -> seconds *= 100;
            }
        }
        return Duration.ofSeconds(seconds);
    }

    private int getMessageCount(int defaultCount) {
        Database db = Environment.get().getDatabase();
        if (db instanceof ProvidedDatabase) {
            return defaultCount / 10;
        } else {
            return defaultCount;
        }
    }

    @Test
    @Tag(Constants.TAG_SMOKE)
    @Tag(Constants.TAG_JDBC)
    public void testDurableMessageQueue() {
        LOGGER.info("Sending 500 durable amqp messages");
        String address = getTestRandomName();
        String messageContent = TestUtils.generateRandomText(27);
        doSendReceiveDurableMsgQueue(address, getMessageCount(500), messageContent);
    }

    @Test
    @Tag(Constants.TAG_JDBC)
    @TestDisabledOnProvidedDb
    public void testDurableMessageQueue4MiB() {
        LOGGER.info("Sending 50 durable amqp messages of 4 MiB size each");
        String address = getTestRandomName();
        doSendReceiveDurableMsgQueue(address, getMessageCount(50), "4mb_file", 4, "MiB");
    }

    @Test
    @Tag(Constants.TAG_JDBC)
    public void testDurableMessageQueueMany1MiB() {
        LOGGER.info("Sending 200 durable amqp messages of 1 MiB size each");
        String address = getTestRandomName();
        doSendReceiveDurableMsgQueue(address, getMessageCount(200), "1mb_file", 1, "MiB");
    }

    @Test
    @Tag(Constants.TAG_JDBC)
    @TestDisabledOnProvidedDb
    public void testDurableMessageQueueMany9MiB() {
        LOGGER.info("Sending 100 durable amqp messages of 9 MiB size each");
        String address = getTestRandomName();
        doSendReceiveDurableMsgQueue(address, getMessageCount(100), "9mb_file", 9, "MiB");
    }

    @Test
    @Tag(Constants.TAG_JDBC)
    public void testDurableMessageQueueMany100KiB() {
        LOGGER.info("Sending 1000 durable amqp messages of 100 KiB size each");
        String address = getTestRandomName();
        doSendReceiveDurableMsgQueue(address, getMessageCount(1000), "100KB_file", 100, "KiB");
    }

}
