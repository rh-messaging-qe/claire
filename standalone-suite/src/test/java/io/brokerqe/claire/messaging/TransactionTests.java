/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.messaging;

import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.client.deployment.ArtemisDeployment;
import io.brokerqe.claire.clients.DeployableClient;
import io.brokerqe.claire.clients.MessagingClient;
import io.brokerqe.claire.clients.bundled.ArtemisCommand;
import io.brokerqe.claire.clients.bundled.BundledArtemisClient;
import io.brokerqe.claire.clients.container.AmqpQpidClient;
import io.brokerqe.claire.junit.TestDisabledOnProvidedDb;
import io.brokerqe.claire.client.deployment.BundledClientDeployment;
import io.brokerqe.claire.client.deployment.StJavaClientDeployment;
import io.brokerqe.claire.container.ArtemisContainer;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// TODO separate deployment!
@Tag(Constants.TAG_JDBC)
public class TransactionTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionTests.class);
    private ArtemisContainer artemis;
    private DeployableClient deployableClient;
    private final String username = ArtemisConstants.ADMIN_NAME;
    private final String password = ArtemisConstants.ADMIN_PASS;
    private String brokerUri;

    @BeforeAll
    void setupEnv() {
        String artemisName = "artemis";
        LOGGER.info("Creating artemis instance: " + artemisName);
        artemis = ArtemisDeployment.createArtemis(artemisName);
        deployableClient = new StJavaClientDeployment();
        brokerUri = Constants.AMQP_URL_PREFIX + artemis.getName() + ":" + DEFAULT_AMQP_PORT;
    }

    private void doTestTransactions(String address, Map<String, String> senderOptions, Map<String, String> receiverOptions, int receiverExpMsgCount, int leftoverMessages) {
        if (receiverOptions == null || receiverOptions.size() == 0) {
            receiverOptions = Map.of(
            "conn-username", username,
            "conn-password", password,
            "address", address,
            "count", "0"
            );
        }

        LOGGER.info("[{}] Sending {} (tx) messages to {}.", artemis.getName(), senderOptions.get("count"), address);
        MessagingClient messagingClient = new AmqpQpidClient(deployableClient, brokerUri, senderOptions, receiverOptions);
        messagingClient.sendMessages();
        LOGGER.info("[{}] Receiving some (tx) messages from {}.", artemis.getName(), address);
        int received = messagingClient.receiveMessages();

        assertThat(received, equalTo(receiverExpMsgCount));
        List<JSONObject> expectedMessages = ((List<JSONObject>) messagingClient.getSentMessages()).subList(0, receiverExpMsgCount);
        assertTrue(messagingClient.compareMessages(expectedMessages, messagingClient.getReceivedMessages()));
        checkQueueMessageCount(address, receiverExpMsgCount, leftoverMessages);
        doCleanQueue(address);
    }

    public void checkQueueMessageCount(String queueName, int expMessagesAdded, int expMessageCount) {
        checkQueueMessageCount(queueName, expMessagesAdded, expMessageCount, -1);
    }

    public void checkQueueMessageCount(String queueName, int expMessagesAdded, int expMessageCount, int expConsumerCount) {
        LOGGER.info("[{}] Checking {} queue message stats.", artemis.getName(), queueName);
        Map<String, String> commandOptions = Map.of(
                "maxColumnSize", "-1",
                "maxRows", "1000"
        );
        BundledArtemisClient bac = new BundledArtemisClient(new BundledClientDeployment(), ArtemisCommand.QUEUE_STAT, commandOptions);
        Map<String, Map<String, String>> queueStats = (Map<String, Map<String, String>>) bac.executeCommand();
        assertEquals(String.valueOf(expMessageCount), queueStats.get(queueName).get("message_count"));
        assertEquals(String.valueOf(expMessagesAdded), queueStats.get(queueName).get("messages_added"));
        if (expConsumerCount != -1) {
            assertEquals(String.valueOf(expConsumerCount), queueStats.get(queueName).get("consumer_count"));
        }
    }

    private void doCleanQueue(String address) {
        LOGGER.info("[{}] Cleaning queue {}", artemis.getName(), address);
        MessagingClient consumer = new AmqpQpidClient(deployableClient, artemis.getName(), DEFAULT_ALL_PORT, address, address, 0, username, password);
        try {
            consumer.receiveMessages();
        } catch (Exception exception) {
            // whatever happens here, move on
            LOGGER.debug(exception.getMessage());
        }
    }

    @Test
    @Tag(Constants.TAG_JDBC)
    public void testSimpleTransaction() {
        int messages = 10;
        String addressName = getTestRandomName();
        Map<String, String> senderOptions = Map.of(
                "conn-username", username,
                "conn-password", password,
                "address", addressName,
                "tx-endloop-action", "commit",
                "count", String.valueOf(messages)
        );
        doTestTransactions(addressName, senderOptions, null, messages, 0);
    }

    @Test
    @Tag(Constants.TAG_JDBC)
    public void testSimpleTransactionSenderBatch() {
        // Send & commit 12 messages in 3 transactions. Receive them all.
        int messages = 14;
        String addressName = getTestRandomName();
        Map<String, String> senderOptions = Map.of(
                "conn-username", username,
                "conn-password", password,
                "address", addressName,
                "tx-endloop-action", "commit",
                "tx-size", "4",
                "count", String.valueOf(14)
        );
        doTestTransactions(addressName, senderOptions, null, messages, 0);
    }

    @Test
    @Tag(Constants.TAG_JDBC)
    public void testSimpleTransactionSenderRollback() {
        // Send 10 messages and do a full rollback. Receive 0 messages.
        String addressName = getTestRandomName();
        Map<String, String> senderOptions = Map.of(
                "conn-username", username,
                "conn-password", password,
                "address", addressName,
                "tx-endloop-action", "rollback",
                "count", String.valueOf(10)
        );
        doTestTransactions(addressName, senderOptions, null, 0, 0);
    }

    @Test
    @Tag(Constants.TAG_JDBC)
    public void testSimpleTransactionSenderBatchLeftovers() {
        // Send & commit 11 messages in 3 transactions. Receive 9 messages (2 leftovers).
        String addressName = getTestRandomName();
        Map<String, String> senderOptions = Map.of(
                "conn-username", username,
                "conn-password", password,
                "address", addressName,
                "tx-size", "3",
                "tx-action", "commit",
                "tx-endloop-action", "none",
                "count", String.valueOf(11)
        );
        doTestTransactions(addressName, senderOptions, null, 9, 0);
    }

    @Test
    @Tag(Constants.TAG_JDBC)
    public void testSimpleTransactionSenderBatchCommitLeftovers() {
        // Send & commit 11 messages in 3 transactions and commit leftovers. Receive all 11 messages
        String addressName = getTestRandomName();
        Map<String, String> senderOptions = Map.of(
                "conn-username", username,
                "conn-password", password,
                "address", addressName,
                "tx-size", "3",
                "tx-action", "commit",
                "tx-endloop-action", "commit",
                "count", String.valueOf(11)
        );
        doTestTransactions(addressName, senderOptions, null, 11, 0);
    }

    @Test
    @Tag(Constants.TAG_JDBC)
    public void testSimpleTransactionSenderBatchSizeLeftoversBigger() {
        // Send & commit 4000 messages in 130 transactions. Receive 3900 messages (0 leftovers). """
        String addressName = getTestRandomName();
        Map<String, String> senderOptions = Map.of(
                "conn-username", username,
                "conn-password", password,
                "address", addressName,
                "tx-size", "300",
                "tx-action", "commit",
                "tx-endloop-action", "none",
                "count", String.valueOf(4000)
        );
        doTestTransactions(addressName, senderOptions, null, 3900, 0);
    }

    @Test
    @Tag(Constants.TAG_JDBC)
    @TestDisabledOnProvidedDb
    public void testSimpleTransactionSenderBatchSizeLeftoversBigger3MiB() {
        // Send & commit 4000 messages in 130 transactions. Receive 3900 messages (0 leftovers). """
        String addressName = getTestRandomName();
        String contentFile = deployableClient.createFile("3MiB_msg", 3, "MiB");
        Map<String, String> senderOptions = Map.of(
                "conn-username", username,
                "conn-password", password,
                "address", addressName,
                "tx-size", "30",
                "tx-action", "commit",
                "tx-endloop-action", "none",
                "count", String.valueOf(400),
                "msg-content-from-file", contentFile,
                "msg-content-hashed", "true"
        );
        Map<String, String> receiverOptions = Map.of(
                "conn-username", username,
                "conn-password", password,
                "address", addressName,
                "tx-size", "30",
                "tx-action", "commit",
                "tx-endloop-action", "none",
                "count", "0",
                "msg-content-hashed", "true"
        );
        doTestTransactions(addressName, senderOptions, receiverOptions, 390, 0);
    }

    @Test
    @Tag(Constants.TAG_JDBC)
    public void testTransactionSendReceive() {
        // Send few messages in one tx and receive in transaction.
        String addressName = getTestRandomName();
        int messages = 20;
        Map<String, String> senderOptions = Map.of(
                "conn-username", username,
                "conn-password", password,
                "address", addressName,
                "tx-endloop-action", "commit",
                "count", String.valueOf(messages)
        );
        Map<String, String> receiverOptions = Map.of(
                "conn-username", username,
                "conn-password", password,
                "address", addressName,
                "tx-endloop-action", "commit",
                "count", "0",
                "timeout", "2"
        );
        doTestTransactions(addressName, senderOptions, receiverOptions, messages, 0);
    }

    @Test
    @Tag(Constants.TAG_JDBC)
    public void testTransactionSendReceiveBatch() {
        // Send few messages in 4 tx, commit by 3 msgs and receive in transaction.
        String addressName = getTestRandomName();
        int messages = 14;
        Map<String, String> senderOptions = Map.of(
                "conn-username", username,
                "conn-password", password,
                "address", addressName,
                "tx-size", "3",
                "tx-action", "commit",
                "tx-endloop-action", "rollback",
                "count", String.valueOf(messages)
        );
        Map<String, String> receiverOptions = Map.of(
                "conn-username", username,
                "conn-password", password,
                "address", addressName,
                "tx-size", "3",
                "tx-action", "commit",
                "count", "0",
                "timeout", "2"
        );
        doTestTransactions(addressName, senderOptions, receiverOptions, 12, 0);
    }

    @Test
    @Tag(Constants.TAG_JDBC)
    public void testSimpleTransactionReceiver() {
        // Send few messages (no tx) and receive in transaction.
        String addressName = getTestRandomName();
        int messages = 10;
        Map<String, String> senderOptions = Map.of(
                "conn-username", username,
                "conn-password", password,
                "address", addressName,
                "count", String.valueOf(messages)
        );
        Map<String, String> receiverOptions = Map.of(
                "conn-username", username,
                "conn-password", password,
                "address", addressName,
                "tx-endloop-action", "commit",
                "count", "0",
                "timeout", "2"
        );
        doTestTransactions(addressName, senderOptions, receiverOptions, messages, 0);
    }

    @Test
    @Tag(Constants.TAG_JDBC)
    public void testSimpleTransactionReceiverCommit() {
        // Send few messages (no tx) and receive in transaction.
        String addressName = getTestRandomName();
        int messages = 10;
        Map<String, String> senderOptions = Map.of(
                "conn-username", username,
                "conn-password", password,
                "address", addressName,
                "count", String.valueOf(messages)
        );
        Map<String, String> receiverOptions = Map.of(
                "conn-username", username,
                "conn-password", password,
                "address", addressName,
                "tx-size", "3",
                "tx-action", "commit",
                "tx-endloop-action", "commit",
                "count", "0",
                "timeout", "2"
        );
        doTestTransactions(addressName, senderOptions, receiverOptions, messages, 0);
    }

    @Test
    @Tag(Constants.TAG_JDBC)
    public void testTransactionCommitRollbackCommit() {
        // Send 10 messages commit, send again rollback, receive first 10 messages and commit.
        String addressName = getTestRandomName();
        int messages = 10;
        Map<String, String> senderOptions = Map.of(
                "conn-username", username,
                "conn-password", password,
                "address", addressName,
                "tx-size", "2",
                "tx-action", "commit",
                "count", String.valueOf(messages)
        );
        Map<String, String> senderRollbackOptions = Map.of(
                "conn-username", username,
                "conn-password", password,
                "address", addressName,
                "tx-size", "2",
                "tx-action", "rollback",
                "count", String.valueOf(messages)
        );
        Map<String, String> receiverOptions = Map.of(
                "conn-username", username,
                "conn-password", password,
                "address", addressName,
                "tx-endloop-action", "commit",
                "count", "0",
                "timeout", "5"
        );
        LOGGER.info("Send {} messages and {} them.", messages, senderOptions.get("tx-action"));
        MessagingClient messagingClient = new AmqpQpidClient(deployableClient, brokerUri, senderOptions, receiverOptions);
        int sent = messagingClient.sendMessages();
        checkQueueMessageCount(addressName, messages, messages);

        LOGGER.info("Send {} messages and {} them.", messages, senderRollbackOptions.get("tx-action"));
        MessagingClient messagingClientRollback = new AmqpQpidClient(deployableClient, brokerUri, senderRollbackOptions, receiverOptions);
        messagingClientRollback.sendMessages(); // messages were rolled back
        checkQueueMessageCount(addressName, messages, messages);

        LOGGER.info("Receive all messages and {} them.", receiverOptions.get("tx-endloop-action"));
        int received = messagingClient.receiveMessages();
        assertThat(received, equalTo(sent));
        assertTrue(messagingClient.compareMessages(messagingClient.getSentMessages(), messagingClient.getReceivedMessages()));

        checkQueueMessageCount(addressName, messages, 0);
    }

    @Test
    @Tag(Constants.TAG_JDBC)
    public void testTransactionSendRollbackCommitDeliveryCount() {
        /*
          1. Send 1 message,
          2. Consume in transaction,
          3. Rollback transaction,
          4. Consume message again,
          5. Commit transaction
          6. Check JMSXDeliveryCount counter
         */
        String addressName = getTestRandomName();
        int messages = 2;
        Map<String, String> senderOptions = Map.of(
                "conn-username", username,
                "conn-password", password,
                "address", addressName,
                "tx-endloop-action", "commit",
                "count", String.valueOf(messages)
        );
        Map<String, String> receiverRollbackOptions = Map.of(
                "conn-username", username,
                "conn-password", password,
                "address", addressName,
                "tx-endloop-action", "rollback",
                "count", "0",
                "timeout", "2"
        );
        Map<String, String> receiverCommitOptions = Map.of(
                "conn-username", username,
                "conn-password", password,
                "address", addressName,
                "tx-endloop-action", "commit",
                "count", "0",
                "timeout", "2"
        );
        LOGGER.info("[{}] Send {} messages and {} them.", artemis.getName(), messages, senderOptions.get("tx-endloop-action"));
        MessagingClient messagingCommitClient = new AmqpQpidClient(deployableClient, brokerUri, senderOptions, receiverCommitOptions);
        int sent = messagingCommitClient.sendMessages();
        checkQueueMessageCount(addressName, messages, messages);

        LOGGER.info("[{}] Send {} messages and {} them.", artemis.getName(), messages, receiverRollbackOptions.get("tx-endloop-action"));
        MessagingClient messagingClientRollback = new AmqpQpidClient(deployableClient, brokerUri, senderOptions, receiverRollbackOptions);
        messagingClientRollback.receiveMessages(); // messages were rolled back
        checkQueueMessageCount(addressName, messages, messages);

        LOGGER.info("[{}] Check rollback message properties: 'redelivered' is 'false' & 'delivery-count' is '0'", artemis.getName());
        List<JSONObject> messagesRollbacked = (List<JSONObject>) messagingClientRollback.getReceivedMessages();
        for (JSONObject rbMessage : messagesRollbacked) {
            assertEquals("false", rbMessage.get("redelivered").toString().toLowerCase(Locale.ROOT));
            assertEquals(0, rbMessage.get("delivery-count"));
        }

        LOGGER.info("[{}] Receive all messages and {} them.", artemis.getName(), receiverCommitOptions.get("tx-endloop-action"));
        int received = messagingCommitClient.receiveMessages();

        LOGGER.info("[{}] Check rollback message properties: 'redelivered' is 'true' & 'delivery-count' is '1'", artemis.getName());
        List<JSONObject> messagesReceived = (List<JSONObject>) messagingCommitClient.getReceivedMessages();
        for (JSONObject message : messagesReceived) {
            assertEquals("true", message.get("redelivered").toString().toLowerCase(Locale.ROOT));
            assertEquals(1, message.get("delivery-count"));
        }
        assertThat(received, equalTo(sent));
        assertTrue(messagingCommitClient.compareMessages(messagingCommitClient.getSentMessages(), messagingCommitClient.getReceivedMessages()));

        checkQueueMessageCount(addressName, messages, 0);
    }

    public void doTestTopicTransactions(String addressName, String queueName, Map<String, String> senderOptions, Map<String, String> receiverOptions, int receiverExpMsgCount) {
        LOGGER.info("[{}] Subscribe 2 receivers to {}.", artemis.getName(), addressName);
        MessagingClient client1 = new AmqpQpidClient(deployableClient, brokerUri, senderOptions, receiverOptions);
        MessagingClient subscriber2 = new AmqpQpidClient(deployableClient, brokerUri, senderOptions, receiverOptions); //null senderOpts
        client1.subscribe();
        subscriber2.subscribe();

        checkQueueMessageCount(queueName, 0, 0, 2);

        LOGGER.info("[{}] Send few messages to {} and {} them.", artemis.getName(), addressName, senderOptions.get("tx-action"));
        client1.sendMessages();
        checkQueueMessageCount(queueName, receiverExpMsgCount, 0, 2);

        int received1 = client1.receiveMessages();
        int received2 = subscriber2.receiveMessages();
        assertEquals(receiverExpMsgCount, received1 + received2);

        List<JSONObject> expectedMessages = ((List<JSONObject>) client1.getSentMessages()).subList(0, receiverExpMsgCount);
        List<JSONObject> receivedMessages = (List<JSONObject>) client1.getReceivedMessages();
        receivedMessages.addAll((List<JSONObject>) subscriber2.getReceivedMessages());

        assertTrue(client1.compareMessages(expectedMessages, receivedMessages));
    }

    @Test
    @Tag(Constants.TAG_JDBC)
    public void testTransactionTopicCommit() {
        // Subscribe 2 receivers, send few messages to topic and commit them.
        String queueName = TestUtils.generateRandomName();
        String addressName = "topic://" + getTestRandomName() + "::" + queueName;
        int messages = 10;
        int expMessageCount = 9;
        Map<String, String> senderOptions = Map.of(
                "conn-username", username,
                "conn-password", password,
                "address", addressName,
                "tx-size", "3",
                "tx-action", "commit",
                "count", String.valueOf(messages),
                "msg-durable", "true"
        );
        Map<String, String> receiverOptions = Map.of(
                "conn-username", username,
                "conn-password", password,
                "address", addressName,
                "timeout", "10"
        );
        doTestTopicTransactions(addressName, queueName, senderOptions, receiverOptions, expMessageCount);
    }

    @Test
    @Tag(Constants.TAG_JDBC)
    public void testTransactionTopicRollback() {
        // Subscribe 2 receivers, send few messages to topic and rollback them.
        String queueName = TestUtils.generateRandomName();
        String addressName = "topic://" + getTestRandomName() + "::" + queueName;
        int messages = 10;
        int expMessageCount = 0;
        Map<String, String> senderOptions = Map.of(
                "conn-username", username,
                "conn-password", password,
                "address", addressName,
                "tx-size", "3",
                "tx-action", "rollback",
                "count", String.valueOf(messages),
                "msg-durable", "true"
                );
        Map<String, String> receiverOptions = Map.of(
                "conn-username", username,
                "conn-password", password,
                "address", addressName,
                "timeout", "10"
        );
        doTestTopicTransactions(addressName, queueName, senderOptions, receiverOptions, expMessageCount);
    }
}
