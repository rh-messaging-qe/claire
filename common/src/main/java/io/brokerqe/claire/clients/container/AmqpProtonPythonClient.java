/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.clients.container;

import io.brokerqe.claire.Constants;
import io.brokerqe.claire.clients.DeployableClient;
import io.brokerqe.claire.clients.MessagingClient;
import io.brokerqe.claire.clients.MessagingClientException;
import io.brokerqe.claire.executor.Executor;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class AmqpProtonPythonClient extends SystemtestClient {
    private final String brokerUri;
    private List<JSONObject> sentMessages;
    private List<JSONObject> receivedMessages;

    private Executor subscriberExecWatch;

    private static final Logger LOGGER = LoggerFactory.getLogger(AmqpProtonPythonClient.class);

    String getCliExecutableBase() {
        return "cli-proton-python";
    }

    public AmqpProtonPythonClient(DeployableClient deployableClient, String destinationUrl, String destinationPort, String address, String queue, int messageCount) {
        String brokerUri = String.format("amqp://%s:%s", destinationUrl, destinationPort);
        String fqqn = String.format("%s::%s", address, queue);
        Map<String, String> senderOptions = new AmqpCliOptionsBuilder()
                .address(fqqn)
                .count(messageCount)
                .build();
        Map<String, String> receiverOptions = new AmqpCliOptionsBuilder()
                .address(fqqn)
                .count(messageCount)
                .build();
        this.deployableClient = deployableClient;
        this.brokerUri = brokerUri;
        this.senderOptions = senderOptions;
        this.receiverOptions = receiverOptions;
    }

    public AmqpProtonPythonClient(DeployableClient deployableClient, String destinationUrl, String destinationPort, String address, String queue, int messageCount, String username, String password) {
        String brokerUri = String.format("amqp://%s:%s", destinationUrl, destinationPort);
        String fqqn = String.format("%s::%s", address, queue);
        Map<String, String> senderOptions = new AmqpCliOptionsBuilder()
                .address(fqqn)
                .count(messageCount)
                .connUsername(username)
                .connPassword(password)
                .build();
        Map<String, String> receiverOptions = new AmqpCliOptionsBuilder()
                .address(fqqn)
                .count(messageCount)
                .connUsername(username)
                .connPassword(password)
                .build();
        this.deployableClient = deployableClient;
        this.brokerUri = brokerUri;
        this.senderOptions = senderOptions;
        this.receiverOptions = receiverOptions;
    }

    public AmqpProtonPythonClient(DeployableClient deployableClient, String brokerUri, String address, String queue, int messageCount, String saslMechanism, String keystore, String keystorePassword, String trustStore, String trustStorePassword) {
        LOGGER.info("[{}] Ignoring parameter 'saslMechanism'", deployableClient.getContainerName());
        LOGGER.info("[{}] Ignoring parameter 'trustStore'", deployableClient.getContainerName());
        LOGGER.info("[{}] Ignoring parameter 'trustStorePassword'", deployableClient.getContainerName());
        if (!address.equals(queue)) {
            throw new IllegalArgumentException("The 'address' and 'queue' arguments must be the same for this client");
        }
        Map<String, String> senderOptions = new AmqpCliOptionsBuilder()
                .address(address)
                .count(messageCount)
                .connSsl(true)
                .connSslCertificate(keystore)
                .connSslCertificatePassword(keystorePassword)
                .build();
        Map<String, String> receiverOptions = new AmqpCliOptionsBuilder()
                .address(address)
                .count(messageCount)
                .connSsl(true)
                .connSslCertificate(keystore)
                .connSslCertificatePassword(keystorePassword)
                .build();
        this.deployableClient = deployableClient;
        this.brokerUri = brokerUri;
        this.senderOptions = senderOptions;
        this.receiverOptions = receiverOptions;
    }

    public AmqpProtonPythonClient(DeployableClient deployableClient, String brokerUri, Map<String, String> senderOptions, Map<String, String> receiverOptions) {
        this.deployableClient = deployableClient;
        this.brokerUri = brokerUri;
        this.senderOptions = senderOptions;
        this.receiverOptions = receiverOptions;
    }

    public AmqpProtonPythonClient(DeployableClient deployableClient, String brokerUri, Map<String, String> senderOptions, Map<String, String> receiverOptions, boolean secured) {
        this.deployableClient = deployableClient;
        this.brokerUri = brokerUri;
        this.senderOptions = senderOptions;
        this.receiverOptions = receiverOptions;
    }

    @Override
    public int sendMessages() {
        String cmdOutput;
        String[] command = constructClientCommand(MessagingClient.SENDER, senderOptions);
        cmdOutput = (String) deployableClient.getExecutor().executeCommand(Constants.DURATION_3_MINUTES, command);
        LOGGER.debug("[{}][TX] \n{}", deployableClient.getContainerName(), cmdOutput);
        this.sentMessages = parseMessages(cmdOutput);
        return sentMessages.size();
    }

    private String[] constructClientCommand(String clientType, Map<String, String> clientOptions, Integer timeout) {
        String brokerUri = getBrokerUri(clientOptions);

        List<String> list = new ArrayList<>();
        list.add(getCliExecutableBase() + "-" + clientType);
        list.add(String.format("--broker-url=%s", brokerUri));
        list.add("--log-msgs=json");
        if (timeout != null) {
            list.add(String.format("--timeout=%d", timeout));
        }
        for (var opt : clientOptions.entrySet()) {
            if (Set.of("address", "conn-username", "conn-password").contains(opt.getKey())) {
                continue;
            }
            list.add(String.format("--%s=%s", opt.getKey(), opt.getValue()));
        }
        return list.toArray(String[]::new);
    }

    private String getBrokerUri(Map<String, String> clientOptions) {
        String username = clientOptions.getOrDefault("conn-username", null);
        String password = clientOptions.getOrDefault("conn-password", null);
        String creds = (username != null || password != null) ? String.format("%s:%s", username, password) : "";
        URI uri = URI.create(brokerUri);
        String amendedBrokerUri = String.format("%s://%s@%s/%s",
                uri.getScheme(), creds, uri.getAuthority(), clientOptions.get("address"));
        return amendedBrokerUri;
    }

    private String[] constructClientCommand(String clientType, Map<String, String> clientOptions) {
        return constructClientCommand(clientType, clientOptions, null);
    }

    @Override
    public int receiveMessages() {
        if (subscriberExecWatch != null) {
            // executed client on background
            return getSubscribedMessages();
        } else {
            // executed client on foreground
            String cmdOutput;
            String[] command = constructClientCommand(MessagingClient.RECEIVER, receiverOptions);
            cmdOutput = (String) deployableClient.getExecutor().executeCommand(Constants.DURATION_3_MINUTES, command);
            LOGGER.debug("[{}][RX] \n{}", deployableClient.getContainerName(), cmdOutput);
            this.receivedMessages = parseMessages(cmdOutput);
            return receivedMessages.size();
        }
    }

    private List<JSONObject> parseMessages(String output) {
        List<JSONObject> jsonMessages = new ArrayList<>();
        String[] lines = output.split("\n");
        if (lines.length == 1 && lines[0].isEmpty()) {
            LOGGER.debug("Parsed 0 messages");
            return jsonMessages;
        }
        for (String line : lines) {
            try {
                jsonMessages.add(new JSONObject(line));
            } catch (JSONException e) {
                // do we want to carry on with execution?
                LOGGER.error("Unable to parse {} ", output);
                throw new MessagingClientException("Unable to get messages \n" + output, e);
            }
        }
        return jsonMessages;
    }

    @Override
    public void subscribe() {
        String[] command;
        if (receiverOptions != null) {
            command = constructClientCommand(MessagingClient.RECEIVER, receiverOptions);
        } else {
            command = constructClientCommand(MessagingClient.RECEIVER, receiverOptions, 60);
        }
        subscriberExecWatch = deployableClient.getExecutor();
        subscriberExecWatch.execBackgroundCommand(command);
    }

    public int getSubscribedMessages() {
        String cmdOutput = subscriberExecWatch.getBackgroundCommandData(5);
        this.receivedMessages = parseMessages(cmdOutput);
        return receivedMessages.size();
    }

    @Override
    public Object getSentMessages() {
        return sentMessages;
    }

    @Override
    public Object getReceivedMessages() {
        return receivedMessages;
    }

    @Override
    public boolean compareMessages() {
        return compareMessages(this.sentMessages, this.receivedMessages);
    }
}
