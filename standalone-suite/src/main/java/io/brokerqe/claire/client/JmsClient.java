/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.client;

import io.brokerqe.claire.Constants;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.helper.TimeHelper;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.exception.ClaireRuntimeException;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class JmsClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(JmsClient.class);

    public static final String TIMEOUT_EXCEED_OR_CONSUMER_WAS_CLOSED = "timeout exceed or consumer was closed";

    private final String clientId;
    private final ConnectionFactory cf;
    private String username;
    private String password;
    private boolean transactedSession;
    private int sessionAckMode;
    private Class<? extends Destination> dstClass;
    private String dstName;
    private Connection connection;
    private final Map<String, Future<Map<String, Message>>> producedMsgs;
    private final Map<String, Future<Map<String, Message>>> consumedMsgs;
    private long producerIdCounter;
    private long consumerIdCounter;

    public JmsClient(String clientId, ConnectionFactory cf) {
        this.clientId = clientId;
        this.cf = cf;
        transactedSession = true;
        sessionAckMode = Session.SESSION_TRANSACTED;
        producedMsgs = new ConcurrentHashMap<>();
        consumedMsgs = new ConcurrentHashMap<>();
        producerIdCounter = 1;
        consumerIdCounter = 1;
    }

    public JmsClient withCredentials(String username, String password) {
        this.username = username;
        this.password = password;
        return this;
    }

    public JmsClient withSessionOptions(boolean transactedSession, int sessionAckMode) {
        this.transactedSession = transactedSession;
        this.sessionAckMode = sessionAckMode;
        return this;
    }

    public JmsClient withDestination(Class<? extends Destination> dstClass, String dstName) {
        this.dstClass = dstClass;
        this.dstName = dstName;
        return this;
    }

    public JmsClient connect() {
        if (connection == null) {
            LOGGER.debug("Trying to open connection for client {}", clientId);
            try {
                if (username != null && !username.isEmpty() && !username.isBlank()
                        && password != null && !password.isEmpty() && !password.isBlank()) {
                    LOGGER.trace("Creating connection using user {} and password {}", username, password);
                    connection = cf.createConnection(username, password);
                } else {
                    LOGGER.trace("Creating connection without user and password");
                    connection = cf.createConnection();
                }
                connection.start();
            } catch (JMSException e) {
                String errMsg = String.format("Error on create connection: %s", e.getMessage());
                LOGGER.error(errMsg);
                throw new ClaireRuntimeException(errMsg, e);
            }
        } else {
            LOGGER.warn("Client {} already connect, ignoring new connection attempt", clientId);
        }
        return this;
    }

    public JmsClient disconnect() {
        LOGGER.debug("Trying to disconnect client {}", clientId);
        try {
            if (connection != null) {
                LOGGER.trace("Closing connection");
                connection.close();
            } else {
                LOGGER.trace("Client connection for client id {} is not opened, ignoring connection close", clientId);
            }
        } catch (JMSException e) {
            String errMsg = String.format("Error on disconnecting: %s", e.getMessage());
            LOGGER.error(errMsg);
            throw new ClaireRuntimeException(errMsg, e);
        }
        return this;
    }

    public Map<String, Message> getProducedMsgs() {
        Map<String, Message> allProducedMsgs = new HashMap<>();
        producedMsgs.values().forEach(e -> {
            try {
                allProducedMsgs.putAll(e.get());
            } catch (InterruptedException | ExecutionException ex) {
                String errMsg = String.format("Error on getting produced messages: %s", ex.getMessage());
                LOGGER.error(errMsg);
                throw new ClaireRuntimeException(errMsg, ex);
            }
        });
        return allProducedMsgs;
    }

    public Map<String, Message> getProducedMsgs(long producerId) {
        try {
            String id = "producer-" + producerId;
            return producedMsgs.get(id).get();
        } catch (InterruptedException | ExecutionException e) {
            String errMsg = String.format("Error on getting produced messages: %s", e.getMessage());
            LOGGER.error(errMsg);
            throw new ClaireRuntimeException(errMsg, e);
        }
    }

    public synchronized void clearProducedMsgs() {
        producedMsgs.clear();
    }

    public Map<String, Message> getConsumedMsgs() {
        Map<String, Message> allConsumedMsgs = new HashMap<>();
        consumedMsgs.values().forEach(e -> {
            try {
                allConsumedMsgs.putAll(e.get());
            } catch (InterruptedException | ExecutionException ex) {
                String errMsg = String.format("Error on getting consumed messages: %s", ex.getMessage());
                LOGGER.error(errMsg);
                throw new ClaireRuntimeException(errMsg, ex);
            }
        });
        return allConsumedMsgs;
    }

    public Map<String, Message> getConsumedMsgs(long consumerId) {
        try {
            String id = "consumer-" + consumerId;
            return consumedMsgs.get(id).get();
        } catch (InterruptedException | ExecutionException e) {
            String errMsg = String.format("Error on getting consumed messages: %s", e.getMessage());
            LOGGER.error(errMsg);
            throw new ClaireRuntimeException(errMsg, e);
        }
    }

    public void clearConsumedMsgs() {
        consumedMsgs.clear();
    }

    public long produce(long numOfMsgs) {
        return produce(numOfMsgs, -1, null, 1, 0, false);
    }

    public long produce(long numOfMsgs, boolean waitForCompletion) {
        return produce(numOfMsgs, -1, null, 1, 0, waitForCompletion);
    }

    public long produce(long numOfMsgs, int sizeOfMsgs, boolean waitForCompletion) {
        return produce(numOfMsgs, sizeOfMsgs, null, 1, 0, waitForCompletion);
    }

    public long produce(long numOfMsgs, Map<String, String> msgProperties, boolean waitForCompletion) {
        return produce(numOfMsgs, -1, msgProperties, 1, 0, waitForCompletion);
    }

    public long produce(long numOfMsgs, int sizeOfMsgs, Map<String, String> msgProperties, boolean waitForCompletion) {
        return produce(numOfMsgs, sizeOfMsgs, msgProperties, 1, 0, waitForCompletion);
    }

    public long produce(long numOfMsgs, int sizeOfMsg, Map<String, String> msgProperties, long commitOnEveryNMsgs,
                        long delayBetweenMsgs, boolean waitForCompletion) {
        long producerIdCounter = this.producerIdCounter;
        this.producerIdCounter++;
        String producerId = "producer-" + producerIdCounter;
        Callable<Map<String, Message>> callableProducer = () -> {
            try (Session session = openSession()) {
                Map<String, Message> msgsProduced = new HashMap<>();
                LOGGER.debug("Producing messages for client id {} and producer id {}", clientId, producerId);
                try (MessageProducer producer = createProducer(session, producerId)) {
                    long commitCounter = 1;
                    for (int i = 0; i < numOfMsgs; i++) {
                        String randomText = TestUtils.generateRandomText(sizeOfMsg);
                        TextMessage message = generateTextMessage(session, randomText);
                        populateMsgProperties(message, msgProperties);
                        producer.send(message);
                        String messageId = message.getJMSMessageID();
                        LOGGER.trace("Sent message with id {} for client id {} and producer id {}", messageId, clientId,
                                producerId);
                        if (transactedSession) {
                            commitCounter = evaluateCommitOnEveryNMsg(session, commitCounter, commitOnEveryNMsgs);
                        } else {
                            LOGGER.trace("Message with id {} sent but not commit yet for client id {} and producer id {}",
                                    messageId, clientId, producerId);
                        }
                        msgsProduced.put(messageId, message);
                        TimeHelper.waitFor(delayBetweenMsgs);
                    }
                    return msgsProduced;
                } catch (JMSException e) {
                    String errMsg = String.format("Error on producing message: %s", e.getMessage());
                    LOGGER.error(errMsg);
                    throw new ClaireRuntimeException(errMsg, e);
                }
            }
        };
        ExecutorService executorService = ResourceManager.getExecutorService();
        LOGGER.debug("Submitting produce task to executor service for client id {} and producer id {}", clientId, producerId);
        Future<Map<String, Message>> producerFuture = executorService.submit(callableProducer);
        if (waitForCompletion) {
            while (!producerFuture.isDone()) {
                TimeHelper.waitFor(Constants.DURATION_100_MILLISECONDS);
            }
        }
        producedMsgs.put(producerId, producerFuture);
        return producerIdCounter;
    }

    public long consume(long numOfMsgs) {
        return consume(numOfMsgs, null, 0, 1, 0, false);
    }

    public long consume(long numOfMsgs, boolean waitForCompletion) {
        return consume(numOfMsgs, null, 0, 1, 0, waitForCompletion);
    }

    public long consume(long numOfMsgs, String msgSelector, long msgTimeout, boolean waitForCompletion) {
        return consume(numOfMsgs, msgSelector, msgTimeout, 1, 0, waitForCompletion);
    }

    public long consume(long numOfMsgs, String msgSelector, long msgTimeout, long commitOnEveryNMsgs,
                        long delayBetweenMsgs, boolean waitForCompletion) {
        long consumerIdCounter = this.consumerIdCounter;
        this.consumerIdCounter++;
        String consumerId = "consumer-" + consumerIdCounter;
        Callable<Map<String, Message>> callableConsumer = () -> {
            Map<String, Message> msgsConsumed = new HashMap<>();
            long commitCounter = 1;
            try (Session session = openSession()) {
                LOGGER.debug("Consuming messages for client id {} and consumer id {}", clientId, consumerId);
                try (MessageConsumer consumer = createConsumer(session, consumerId, msgSelector)) {
                    for (int i = 0; i < numOfMsgs; i++) {
                        Message message;
                        if (msgTimeout <= 0) {
                            LOGGER.trace("Trying to consume message without a timeout");
                            message = consumer.receive();
                        } else {
                            LOGGER.trace("Trying to consume message with a timeout of {}", msgTimeout);
                            message = consumer.receive(msgTimeout);
                        }

                        if (message == null) {
                            String errMsg = TIMEOUT_EXCEED_OR_CONSUMER_WAS_CLOSED;
                            LOGGER.trace(errMsg);
                            throw new ClaireRuntimeException(errMsg);
                        }

                        String messageId = message.getJMSMessageID();
                        LOGGER.trace("Received message with id {} for client id {} and consumer id {}", messageId, clientId,
                                consumerId);
                        if (transactedSession) {
                            commitCounter = evaluateCommitOnEveryNMsg(session, commitCounter, commitOnEveryNMsgs);
                        } else {
                            LOGGER.trace("Message with id {} received but not commit yet for client id {} and consumer id {}",
                                    messageId, clientId, consumerId);
                        }
                        msgsConsumed.put(message.getJMSMessageID(), message);
                        TimeHelper.waitFor(delayBetweenMsgs);
                    }
                    return msgsConsumed;
                } catch (JMSException | ClaireRuntimeException e) {
                    String errMsg = String.format("Error on consuming message: %s", e.getMessage());
                    LOGGER.error(errMsg);
                    throw new ClaireRuntimeException(errMsg, e);
                }
            }
        };
        ExecutorService executorService = ResourceManager.getExecutorService();
        LOGGER.debug("Submitting consume task to executor service for client id {} and consumer id {}", clientId, consumerId);
        Future<Map<String, Message>> consumerFuture = executorService.submit(callableConsumer);
        if (waitForCompletion) {
            while (!consumerFuture.isDone()) {
                TimeHelper.waitFor(Constants.DURATION_100_MILLISECONDS);
            }
        }
        consumedMsgs.put(consumerId, consumerFuture);
        return consumerIdCounter;
    }

    private Session openSession() {
        if (connection == null) {
            connect();
        }
        LOGGER.debug("Trying to open session for client {}", clientId);
        Session session;
        try {
            session = connection.createSession(transactedSession, sessionAckMode);
        } catch (JMSException e) {
            String errMsg = String.format("Error on create session: %s", e.getMessage());
            LOGGER.error(errMsg);
            throw new ClaireRuntimeException(errMsg, e);
        }
        return session;
    }

    private Destination openDestination(Session session) {
        try {
            LOGGER.debug("Trying to open destination for client {}", clientId);
            Destination destination;
            if (Queue.class.isAssignableFrom(dstClass)) {
                LOGGER.trace("Creating a queue destination for client {} with name {}", clientId, dstName);
                destination = session.createQueue(dstName);
            } else if (Topic.class.isAssignableFrom(dstClass)) {
                LOGGER.trace("Creating a topic destination for client {} with name {}", clientId, dstName);
                destination = session.createTopic(dstName);
            } else {
                String errMsg = "Tried to create unsupported JMS destination";
                LOGGER.error(errMsg);
                throw new ClaireRuntimeException(errMsg);
            }
            return destination;
        } catch (JMSException e) {
            String errMsg = String.format("Failed on create JMS destination: %s", e.getMessage());
            LOGGER.error(errMsg);
            throw new ClaireRuntimeException(errMsg, e);
        }
    }

    private MessageProducer createProducer(Session session, String producerId) {
        try {
            LOGGER.trace("Creating producer for client {} and producer id {}", clientId, producerId);
            return session.createProducer(openDestination(session));
        } catch (JMSException e) {
            String errMsg = String.format("Error on create producer: %s", e.getMessage());
            LOGGER.error(errMsg);
            throw new ClaireRuntimeException(errMsg, e);
        }
    }

    private MessageConsumer createConsumer(Session session, String consumerId, String msgSelector) {
        try {
            LOGGER.debug("Trying to create consumer for client {} and consumer id {}", clientId, consumerId);
            MessageConsumer consumer;
            if (msgSelector != null && !msgSelector.isEmpty() && !msgSelector.isBlank()) {
                LOGGER.trace("Creating consumer for client {} and consumer id {} and message selector {}", clientId,
                        consumerId, msgSelector);
                consumer = session.createConsumer(openDestination(session), msgSelector);
            } else {
                LOGGER.trace("Creating consumer for client {} and consumer id {}", clientId, consumerId);
                consumer = session.createConsumer(openDestination(session));
            }
            return consumer;
        } catch (JMSException e) {
            String errMsg = String.format("Error on create consumer: %s", e.getMessage());
            LOGGER.error(errMsg);
            throw new ClaireRuntimeException(errMsg, e);
        }
    }

    private TextMessage generateTextMessage(Session session, String randomText) {
        try {
            LOGGER.trace("Creating to create message with text {}", randomText);
            return session.createTextMessage(randomText);
        } catch (JMSException e) {
            String errMsg = String.format("Error on creating text message: %s", e.getMessage());
            LOGGER.error(errMsg);
            throw new ClaireRuntimeException(errMsg, e);
        }
    }

    private void populateMsgProperties(Message message, Map<String, String> msgsProperties) {
        if (msgsProperties == null) {
            LOGGER.trace("Not populating message properties as message properties is null for client id {}", clientId);
            return;
        }
        msgsProperties.forEach((name, value) -> {
            if (name != null && !name.isEmpty() && !name.isBlank() &&
                    value != null && !value.isEmpty() && !value.isBlank()) {
                try {
                    LOGGER.trace("Populating message with property name {} and value {} for client id {}", name, value,
                            clientId);
                    message.setStringProperty(name, value);
                } catch (JMSException e) {
                    String errMsg = String.format("Error on setting message property: %s", e.getMessage());
                    LOGGER.error(errMsg);
                    throw new ClaireRuntimeException(errMsg, e);
                }
            } else {
                LOGGER.trace("Not populating message with property as name or value is null for client id {}", clientId);
            }
        });
    }

    private long evaluateCommitOnEveryNMsg(Session session, long commitCounter, long commitOn) {
        long counter;
        LOGGER.trace("Evaluating commit on every N messages with commit counter {} and commit on {} for client id {}",
                commitCounter, commitOn, clientId);
        if (commitCounter >= commitOn) {
            try {
                LOGGER.trace("Committing for client id {}", clientId);
                session.commit();
            } catch (JMSException e) {
                String errMsg = String.format("Failed on session commit: %s", e.getMessage());
                LOGGER.error(errMsg);
                throw new ClaireRuntimeException(errMsg, e);
            }
            counter = 1;
        } else {
            LOGGER.trace("Not committing yet for client id {} as commit counter {} < commit on {}", clientId, commitCounter,
                    commitOn);
            counter = commitCounter + 1;
        }
        return counter;
    }
}
