/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.clients.bundled;

import io.brokerqe.claire.Constants;
import io.brokerqe.claire.clients.DeployableClient;
import io.brokerqe.claire.clients.MessagingClient;
import io.brokerqe.claire.clients.MessagingClientException;
import io.brokerqe.claire.exception.ClaireRuntimeException;
import io.brokerqe.claire.executor.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public abstract class BundledMessagingClient implements MessagingClient {

    private DeployableClient deployableClient;
    private static final Logger LOGGER = LoggerFactory.getLogger(BundledMessagingClient.class);

    private final String destinationAddress;
    private String destinationUrl;
    private final String destinationPort;
    private final String destinationQueue;
    private final String protocol;
    private final String username;
    private final String password;
    private Boolean isMulticast;
    private Boolean persistenceDisabled;
    // -1 is usually used for infinite number of messages, so use -2 as default unset value
    private int messageCount = -2;
    String clientDestination;
    private int receivedMessages = 0;
    private int sentMessages = 0;
    private Executor subscriberExecutor;
    private int timeout;
    private boolean disableOutput;


    public BundledMessagingClient(BundledClientOptions options) {
        this.protocol = getProtocol();
        this.deployableClient = options.deployableClient;
        this.destinationUrl = options.destinationUrl;
        this.destinationPort = options.destinationPort;
        this.destinationAddress = options.destinationAddress;
        this.destinationQueue = options.destinationQueue;
        this.messageCount = options.messageCount;
        this.password = options.password;
        this.username = options.username;
        this.persistenceDisabled = options.persistenceDisabled;
        this.isMulticast = options.multicast;
        this.timeout = options.timeout;
        this.disableOutput = options.disableOutput;
    }

    abstract String getProtocol();
    void setReceivedMessages(int messageCount) {
        this.receivedMessages = messageCount;
    }
    void setProducedMessages(int messageCount) {
        this.sentMessages = messageCount;
    }

    int parseMessageCount(String clientStdout, String clientType) {
        int messageCount;
        String expectedLine = null;
        String[] lines = clientStdout.split("\n");

        for (String line : lines) {
            String lcLine = line.toLowerCase(Locale.ROOT);
            if (clientType.equals(CONSUMER)) {
                if (lcLine.contains("consumer") && lcLine.contains("consumed:")) {
                    expectedLine = line;
                    break;
                }
            } else {
                if (lcLine.contains("producer") && lcLine.contains("produced:")) {
                    expectedLine = line;
                    break;
                }
            }
        }

        if (expectedLine != null && expectedLine.contains("message") && (expectedLine.contains(destinationQueue) || expectedLine.contains(destinationAddress))) {
            String messageCountText = expectedLine.substring(expectedLine.lastIndexOf(":") + 2, expectedLine.lastIndexOf(" messages"));
            messageCount = Integer.parseInt(messageCountText);
            if (clientType.equals(PRODUCER)) {
                setProducedMessages(messageCount);
            }
            if (clientType.equals(CONSUMER)) {
                setReceivedMessages(messageCount);
            }

            return messageCount;
        } else {
            LOGGER.error("[{}] Unable to parse number of messages!\n {}", deployableClient.getContainerName(), clientStdout);
            throw new MessagingClientException("Unable to parse number of messages \n" + clientStdout);
        }
    }

    private String[] constructClientCommand(String clientType) {
        // timeout 90s ./amq-broker/bin/artemis producer --url tcp://10.129.2.15:61616 --destination queue://demoQueue --message-count=50
        // timeout 90s ./amq-broker/bin/artemis consumer --url tcp://10.129.2.129:61616 --destination queue://demoQueue --message-count=50
        if (isMulticast) {
            clientDestination = "topic://" + destinationAddress;
        } else {
            if (destinationAddress.equals(destinationQueue)) {
                clientDestination = "queue://" + destinationAddress;
            } else {
                clientDestination = String.format("fqqn://%s::%s", destinationAddress, destinationQueue);
            }
        }

        if (!destinationUrl.contains("://")) {
            destinationUrl = "tcp://" + destinationUrl;
        }

        String timeoutCmd = "";
        if (timeout != 0) {
            timeoutCmd = String.format("timeout %ds ", timeout);
        }
        String command = String.format("%s%s/artemis %s --url %s:%s --protocol %s --destination %s",
                timeoutCmd, deployableClient.getExecutableHome(), clientType, destinationUrl, destinationPort, protocol, clientDestination);

        if (messageCount != -2) {
            command += " --message-count " + messageCount;
        }

        if (username != null) {
            command += " --user " + username;
        }

        if (password != null) {
            command += " --password " + password;
        }

        if (persistenceDisabled) {
            command += " --non-persistent";
        }

        return command.split(" ");
    }
    @Override
    public int sendMessages() {
        String cmdOutput;
        String[] command = constructClientCommand(PRODUCER);
        try {
            cmdOutput = deployableClient.getExecutor().executeCommand(Constants.DURATION_3_MINUTES, command).stdout;
            LOGGER.debug("[{}] {}", deployableClient.getContainerName(), cmdOutput);
            return parseMessageCount(cmdOutput, PRODUCER);
        } catch (ClaireRuntimeException e) {
            throw new MessagingClientException(e.getMessage(), e);
        }
    }

    @Override
    public int receiveMessages() {
        return receiveMessages(Constants.DURATION_3_MINUTES);
    }

    @Override
    public int receiveMessages(long duration) {
        if (subscriberExecutor != null) {
            // executed client on background
            return getSubscribedMessages();
        } else {
            // executed client on foreground
            String cmdOutput;
            String[] command = constructClientCommand(CONSUMER);
            cmdOutput = deployableClient.getExecutor().executeCommand(duration, command).stdout;
            if (!disableOutput) {
                LOGGER.debug("[{}] {}", deployableClient.getContainerName(), cmdOutput);
            }
            return parseMessageCount(cmdOutput, CONSUMER);
        }
    }

    @Override
    public Object getSentMessages() {
        throw new IllegalStateException("Bundled clients do not provide message output");
    }

    @Override
    public Object getReceivedMessages() {
        throw new IllegalStateException("Bundled clients do not provide message output");
    }

    /**
     * Subscribe to the address and wait for messages.
     * This is definitely not thread safe, nor reusable solution for multiple client calls.
     * Please spawn new instance when you need more background consumers.
     */
    public void subscribe() {
        String[] command = constructClientCommand(CONSUMER);
        subscriberExecutor = deployableClient.getExecutor();
        subscriberExecutor.execBackgroundCommand(command);
    }

    public void unsubscribe() {
        throw new UnsupportedOperationException("[" + deployableClient.getContainerName() + "] Unsubscribe not supported on bundled clients");
    }

    public int getSubscribedMessages() {
        String cmdOutput = subscriberExecutor.getBackgroundCommandData(5);
        return parseMessageCount(cmdOutput, CONSUMER);
    }

    public boolean compareMessages() {
        return sentMessages == receivedMessages;
    }

    @Override
    public boolean compareMessages(Object sentMessages, Object receivedMessages) {
        if (sentMessages instanceof Integer && receivedMessages instanceof Integer) {
            return sentMessages == receivedMessages;
        } else {
            throw new IllegalArgumentException("This clients currently supports only comparison of Integer (number of sent/received messages)");
        }
    }

}
