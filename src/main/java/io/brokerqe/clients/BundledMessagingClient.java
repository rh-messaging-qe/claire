/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.clients;

import io.brokerqe.KubeClient;
import io.brokerqe.ResourceManager;
import io.brokerqe.executor.Executor;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.dsl.ExecWatch;

import java.util.Locale;
import java.util.concurrent.ExecutionException;

public abstract class BundledMessagingClient implements MessagingClient {
    private final Pod brokerPod;
    private final String destinationAddress;
    private final String destinationUrl;
    private final String destinationPort;
    private final String destinationQueue;
    private final String protocol;
    // -1 is usually used for infinite number of messages, so use -2 as default unset value
    private int messageCount = -2;
    String clientDestination;
    private int receivedMessages = 0;
    private int sentMessages = 0;
    private ExecWatch subscriberExecWatch;
    private Executor backgroundExecutor;
    private KubeClient client;

    public BundledMessagingClient(Pod sourcePod, String destinationUrl, String destinationPort, String destinationAddress, String destinationQueue, int messageCount) {
        this.brokerPod = sourcePod;
        this.destinationUrl = destinationUrl;
        this.destinationPort = destinationPort;
        this.destinationAddress = destinationAddress;
        this.destinationQueue = destinationQueue;
        this.messageCount = messageCount;

        this.client = ResourceManager.getKubeClient().inNamespace(brokerPod.getMetadata().getNamespace());
        this.protocol = getProtocol();
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
        String expectedLine;
        String[] lines = clientStdout.split("\n");

        if (clientType.equals(CONSUMER)) {
            expectedLine = lines[lines.length - 2];
        } else {
            expectedLine = lines[lines.length - 3];
        }

        if (expectedLine.toLowerCase(Locale.ROOT).contains(clientType) && expectedLine.contains("message") &&
                (expectedLine.contains(destinationQueue) || expectedLine.contains(destinationAddress))) {
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
            LOGGER.error("Unable to parse number of messages!\n" + clientStdout);
            return -1;
        }
    }

    private String constructClientCommand(String clientType) {
        // ./amq-broker/bin/artemis producer --url tcp://10.129.2.15:61616 --destination queue://demoQueue --message-count=50
        // ./amq-broker/bin/artemis consumer --url tcp://10.129.2.129:61616 --destination queue://demoQueue --message-count=50
        if (destinationAddress.equals(destinationQueue)) {
            clientDestination = "queue://" + destinationAddress;
        } else {
            clientDestination = String.format("fqqn://%s::%s", destinationAddress, destinationQueue);
        }

        String command = String.format("./amq-broker/bin/artemis %s --url tcp://%s:%s --protocol %s --destination %s",
                clientType, destinationUrl, destinationPort, protocol, clientDestination);

        if (messageCount != -2) {
            command += " --message-count " + messageCount;
        }

        return command;
    }

    @Override
    public int sendMessages() {
        String cmdOutput;
        String command = constructClientCommand(PRODUCER);
        try (Executor example = new Executor()) {
            cmdOutput = example.execCommandOnPod(this.brokerPod.getMetadata().getName(),
                    this.brokerPod.getMetadata().getNamespace(), 180, command.split(" "));
            LOGGER.debug(cmdOutput);
        }
        return parseMessageCount(cmdOutput, PRODUCER);
    }

    @Override
    public int receiveMessages() {
        if (subscriberExecWatch != null) {
            // executed client on background
            return getSubscribedMessages();
        } else {
            // executed client on foreground
            String cmdOutput;
            String command = constructClientCommand(CONSUMER);
            try (Executor example = new Executor()) {
                cmdOutput = example.execCommandOnPod(this.brokerPod.getMetadata().getName(),
                        this.brokerPod.getMetadata().getNamespace(), 180, command.split(" "));
                LOGGER.debug(cmdOutput);
            }
            return parseMessageCount(cmdOutput, CONSUMER);
        }
    }

    /**
     * Subscribe to the address and wait for messages.
     * This is definitely not thread safe, nor reusable solution for multiple client calls.
     * Please spawn new instance when you need more background consumers.
     */
    public void subscribe() {
        String command = constructClientCommand(CONSUMER);
        backgroundExecutor = new Executor();
        subscriberExecWatch = backgroundExecutor.execBackgroundCommandOnPod(this.brokerPod.getMetadata().getName(),
                this.brokerPod.getMetadata().getNamespace(), command.split(" "));
    }

    public int getSubscribedMessages() {
        String cmdOutput = waitUntilClientFinishes(5);
        return parseMessageCount(cmdOutput, CONSUMER);
    }

    public String getClientBackgroundCommandData() {
        String cmdOutput = null;
        if (subscriberExecWatch.exitCode().isDone()) {
            try {
                cmdOutput = backgroundExecutor.getListenerData().get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            } finally {
                subscriberExecWatch.close();
                backgroundExecutor.close();
                backgroundExecutor = null;
                subscriberExecWatch = null;
            }
        }
        return cmdOutput;
    }

    public String waitUntilClientFinishes(int waitSeconds) {
        String cmdOutput;
        while ((cmdOutput = getClientBackgroundCommandData()) == null) {
            LOGGER.debug("Waiting for client to finish (checking every {}s)", waitSeconds);
            try {
                Thread.sleep(waitSeconds * 1000L);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        LOGGER.debug(cmdOutput);
        return cmdOutput;
    }

    public Object getMessages() {
        LOGGER.error("Can't return individual messages from Bundled clients!");
        return null;
    }

    public boolean compareMessages() {
        return sentMessages == receivedMessages;
    }

}
