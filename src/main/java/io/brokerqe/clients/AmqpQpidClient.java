/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.clients;

import io.brokerqe.executor.Executor;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;


public class AmqpQpidClient extends MessagingAmqpClient {

    private Pod clientsPod;
    private String destinationUrl;
    private String destinationPort;
    private String destinationAddress;
    private String destinationQueue;
    String clientDestination;
    private int messageCount;

    private ExecWatch subscriberExecWatch;
    private Executor backgroundExecutor;
    private List<JSONObject> sentMessages;
    private List<JSONObject> receivedMessages;

    public AmqpQpidClient(Pod clientsPod, String destinationUrl, String destinationPort, String destinationAddress, String destinationQueue, int messageCount) {
        this.clientsPod = clientsPod;
        this.destinationUrl = destinationUrl;
        this.destinationPort = destinationPort;
        this.destinationAddress = destinationAddress;
        this.destinationQueue = destinationQueue;
        this.messageCount = messageCount;
    }

    @Override
    public int sendMessages() {
        String cmdOutput;
        String command = constructClientCommand(SENDER);
        try (Executor example = new Executor()) {
            cmdOutput = example.execCommandOnPod(this.clientsPod.getMetadata().getName(),
                    this.clientsPod.getMetadata().getNamespace(), 180, command.split(" "));
            LOGGER.debug(cmdOutput);
        }
        this.sentMessages = parseMessages(cmdOutput);
        return sentMessages.size();
    }

    @Override
    public int receiveMessages() {
        if (subscriberExecWatch != null) {
            // executed client on background
            return getSubscribedMessages();
        } else {
            // executed client on foreground
            String cmdOutput;
            String command = constructClientCommand(RECEIVER);
            try (Executor example = new Executor()) {
                cmdOutput = example.execCommandOnPod(this.clientsPod.getMetadata().getName(),
                        this.clientsPod.getMetadata().getNamespace(), 180, command.split(" "));
                LOGGER.debug(cmdOutput);
            }
            this.receivedMessages = parseMessages(cmdOutput);
            return receivedMessages.size();
        }
    }

    @Override
    public Object getMessages() {
        return super.getMessages();
    }

    private List<JSONObject> parseMessages(String output) {
        List<JSONObject> jsonMessages = new ArrayList<>();
        try {
            String[] lines = output.split("\n");
            for (String line: lines) {
                jsonMessages.add(new JSONObject(line));
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return jsonMessages;
    }

    @Override
    public boolean compareMessages() {
        // Method compares only number of sent and received messages and real comparision of messageIDs (if is present in other group)
        if (sentMessages.size() != receivedMessages.size()) {
            LOGGER.warn("Sent {} and received {} messages are not same!", sentMessages.size(), receivedMessages.size());
            return false;
        } else {
            try {
                // compare message IDs
                List<String> receivedIds = receivedMessages.stream().map(receivedMsg -> {
                    try {
                        return (String) receivedMsg.get("id");
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }).collect(Collectors.toList());
                for (JSONObject message : sentMessages) {
                    if (!receivedIds.contains(message.get("id"))) {
                        LOGGER.warn("Unable to find/compare messageId {}", message);
                        return false;
                    }
                }
            } catch (JSONException e) {
                LOGGER.error("Unable to parse/compare messages! {}", e.getMessage());
            }
            LOGGER.debug("All messages are same. Good.");
            return true;
        }
    }

    @Override
    public void subscribe() {
        String command = constructClientCommand(RECEIVER, 60);
        backgroundExecutor = new Executor();
        subscriberExecWatch = backgroundExecutor.execBackgroundCommandOnPod(this.clientsPod.getMetadata().getName(),
                this.clientsPod.getMetadata().getNamespace(), command.split(" "));
    }

    public int getSubscribedMessages() {
        String cmdOutput = waitUntilClientFinishes(5);
        this.receivedMessages = parseMessages(cmdOutput);
        return receivedMessages.size();
    }

    public String getClientBackgroundCommandData() {
        String cmdOutput = null;
        if (subscriberExecWatch.exitCode().isDone()) {
            try {
                cmdOutput = backgroundExecutor.getListenerData().get().toString();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
            backgroundExecutor.close();
            backgroundExecutor = null;
            subscriberExecWatch = null;
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

    private String constructClientCommand(String clientType) {
        return constructClientCommand(clientType, -2);
    }

    private String constructClientCommand(String clientType, int timeout) {
        // cli-qpid-sender --broker 172.30.177.210:5672 --address myAddress0::myQueue0 --count 10 --log-msgs dict
        // cli-qpid-receiver --broker 172.30.177.210:5672 --address myAddress0::myQueue0 --count 10 --log-msgs dict
        if (destinationAddress.equals(destinationQueue)) {
            clientDestination = destinationAddress;
        } else {
            clientDestination = String.format("%s::%s", destinationAddress, destinationQueue);
        }

        String command = String.format("cli-qpid-%s --broker %s:%s --address %s --log-msgs json",
                clientType, destinationUrl, destinationPort, clientDestination);

        if (messageCount != -2) {
            command += " --count " + messageCount;
        }

        if (timeout != -2) {
            command += " --timeout " + timeout;
        }

        return command;
    }

}
