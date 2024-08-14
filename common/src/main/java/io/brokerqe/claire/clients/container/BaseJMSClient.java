/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.clients.container;

import io.brokerqe.claire.Constants;
import io.brokerqe.claire.clients.DeployableClient;
import io.brokerqe.claire.clients.MessagingClient;
import io.brokerqe.claire.clients.MessagingClientException;
import io.brokerqe.claire.exception.ClaireRuntimeException;
import io.brokerqe.claire.executor.Executor;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public abstract class BaseJMSClient extends SystemtestClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseJMSClient.class);
    private String keystorePassword;
    private String trustStore;
    private String trustStorePassword;
    private String keystore;
    private String brokerUri;
    private String destinationUrl;
    private String destinationPort;
    private String destinationAddress;
    private String destinationQueue;
    private String saslMechanism;
    String clientDestination;
    private int messageCount;
    private boolean secured;
    private Executor subscriberExecWatch;
    private List<JSONObject> sentMessages;
    private List<JSONObject> receivedMessages;
    private String username;
    private String password;

    abstract String getCliExecutableBase();

    public BaseJMSClient(DeployableClient deployableClient, String destinationUrl, String destinationPort, String address, String queue, int messageCount) {
        this(deployableClient, destinationUrl, destinationPort, address, queue, messageCount, null, null);
    }

    public BaseJMSClient(DeployableClient deployableClient, String destinationUrl, String destinationPort, String address, String queue, int messageCount, String username, String password) {
        this.secured = false;
        this.deployableClient = deployableClient;
        this.destinationUrl = destinationUrl;
        this.destinationPort = destinationPort;
        this.destinationAddress = address;
        this.destinationQueue = queue;
        this.messageCount = messageCount;
        this.username = username;
        this.password = password;
    }

    public BaseJMSClient(DeployableClient deployableClient, String brokerUri, String address, String queue, int messageCount, String saslMechanism,
                         String keystore, String keystorePassword, String trustStore, String trustStorePassword) {
        this.secured = true;
        this.deployableClient = deployableClient;
        this.brokerUri = brokerUri;
        this.destinationAddress = address;
        this.destinationQueue = queue;
        this.messageCount = messageCount;
        this.saslMechanism = saslMechanism;
        this.keystore = keystore;
        this.keystorePassword = keystorePassword;
        this.trustStore = trustStore;
        this.trustStorePassword = trustStorePassword;
    }

    public BaseJMSClient(DeployableClient deployableClient, String brokerUri, Map<String, String> senderOptions, Map<String, String> receiverOptions) {
        this(deployableClient, brokerUri, senderOptions, receiverOptions, false);
    }
    public BaseJMSClient(DeployableClient deployableClient, String brokerUri, Map<String, String> senderOptions, Map<String, String> receiverOptions, boolean secured) {
        LOGGER.info("[{}] Creating client to {}", deployableClient.getContainerName(), brokerUri);
        this.secured = secured;
        this.deployableClient = deployableClient;
        this.brokerUri = brokerUri;
        this.senderOptions = senderOptions;
        this.receiverOptions = receiverOptions;
    }

    @Override
    public int sendMessages() {
        String cmdOutput;
        String[] command = constructClientCommand(MessagingClient.SENDER);
        try {
            cmdOutput = (String) deployableClient.getExecutor().executeCommand(Constants.DURATION_3_MINUTES, command);
            LOGGER.debug("[{}][TX] \n{}", deployableClient.getContainerName(), cmdOutput);
            this.sentMessages = parseMessages(cmdOutput);
            return sentMessages.size();
        } catch (ClaireRuntimeException e) {
            throw new MessagingClientException(e.getMessage(), e);
        }
    }

    @Override
    public int receiveMessages() {
        return receiveMessages(Constants.DURATION_3_MINUTES);
    }

    public int receiveMessages(long duration) {
        if (subscriberExecWatch != null) {
            // executed client on background
            return getSubscribedMessages();
        } else {
            // executed client on foreground
            String cmdOutput;
            String[] command = constructClientCommand(MessagingClient.RECEIVER);
            try {
                cmdOutput = (String) deployableClient.getExecutor().executeCommand(duration, command);
                LOGGER.debug("[{}][RX] \n{}", deployableClient.getContainerName(), cmdOutput);
                this.receivedMessages = parseMessages(cmdOutput);
                return receivedMessages.size();
            } catch (ClaireRuntimeException e) {
                throw new MessagingClientException(e.getMessage(), e);
            }
        }
    }

    private List<JSONObject> parseMessages(String output) {
        List<JSONObject> jsonMessages = new ArrayList<>();
        String[] lines = output.split("\n");
        if (lines.length == 1 && lines[0].isEmpty()) {
            LOGGER.debug("[{}] Parsed 0 messages", deployableClient.getContainerName());
            return jsonMessages;
        }
        for (String line: lines) {
            try {
                jsonMessages.add(new JSONObject(line));
            } catch (JSONException e) {
                // do we want to carry on with execution?
                LOGGER.error("[{}] Unable to parse {} ", deployableClient.getContainerName(), output);
                throw new MessagingClientException("Unable to get messages \n" + output, e);
            }
        }
        return jsonMessages;
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

    @Override
    public boolean compareMessages(Object sentMessages, Object receivedMessages) {
        return super.compareMessages(sentMessages, receivedMessages);
    }

    @Override
    public void subscribe() {
        String[] command;
        if (receiverOptions != null) {
            command = constructClientCommandOptions(MessagingClient.RECEIVER);
        } else {
            command = constructClientCommand(MessagingClient.RECEIVER, 60);
        }
        subscriberExecWatch = deployableClient.getExecutor();
        subscriberExecWatch.execBackgroundCommand(command);
    }

    public int getSubscribedMessages() {
        String cmdOutput = subscriberExecWatch.getBackgroundCommandData(5);
        this.receivedMessages = parseMessages(cmdOutput);
        LOGGER.debug("[{}][RX] \n{}", deployableClient.getContainerName(), cmdOutput);
        return receivedMessages.size();
    }

    private String[] constructClientCommand(String clientType) {
        if (senderOptions != null || receiverOptions != null) {
            return constructClientCommandOptions(clientType);
        } else {
            return constructClientCommand(clientType, -2);
        }
    }

    private String[] constructClientCommandOptions(String clientType) {
        Map<String, String> options;
        StringBuilder commandBuild = new StringBuilder(getCliExecutableBase());
        commandBuild.append("-");
        if (clientType.equals(SENDER)) {
            options = senderOptions;
            commandBuild.append(SENDER);
        } else {
            options = receiverOptions;
            commandBuild.append(RECEIVER);
        }

        // TODO: if secured need to use brokerUri or with custom clientOptions
        // TODO secured: "%s /main/cli-qpid.jar %s
        // not secured: cli-qpid-%s
        commandBuild.append(" --broker=").append(brokerUri);
        commandBuild.append(" --log-msgs=").append("json");
        for (Map.Entry<String, String> entry : options.entrySet()) {
            commandBuild.append(" --").append(entry.getKey()).append("=").append(entry.getValue());
        }
        return commandBuild.toString().split(" ");
    }

    private String[] constructClientCommand(String clientType, int timeout) {
        // cli-qpid-sender --broker 172.30.177.210:5672 --address myAddress0::myQueue0 --count 10 --log-msgs dict
        // cli-qpid-receiver --broker 172.30.177.210:5672 --address myAddress0::myQueue0 --count 10 --log-msgs dict
        String command;
        String clientBase = getCliExecutableBase();
        String clientOptions = "";
        if (destinationAddress.equals(destinationQueue)) {
            clientDestination = destinationAddress;
        } else {
            clientDestination = String.format("%s::%s", destinationAddress, destinationQueue);
        }

        if (saslMechanism != null) {
            clientOptions += "?amqp.saslMechanisms=" + saslMechanism;
        }

        if (secured) {
            // java -Djavax.net.ssl.keyStore="/etc/ssl-stores/$CLIENT_KEYSTORE" -Djavax.net.ssl.keyStorePassword="$PASSWORD" -Djavax.net.ssl.trustStore="/etc/ssl-stores/$CLIENT_TRUSTSTORE" -Djavax.net.ssl.trustStorePassword="$PASSWORD"
            // -jar /client_executable/cli-qpid-jms.jar sender --broker-uri $broker_address:443 --log-msgs json --conn-auth-mechanisms PLAIN --conn-username admin --conn-password admin --address "MyQueue0" --count 1 --msg-content "content" --conn-ssl-verify-host true
            String javaPrefix = String.format("java -Djavax.net.ssl.keyStore=%s " +
                            "-Djavax.net.ssl.keyStorePassword=%s " +
                            "-Djavax.net.ssl.trustStore=%s " +
                            "-Djavax.net.ssl.trustStorePassword=%s -jar",
                    keystore, keystorePassword, trustStore, trustStorePassword);
//            https://github.com/rh-messaging/cli-java/issues/139
//            String tlsOptions = String.format("--conn-ssl-keystore-location %s --conn-ssl-keystore-password %s --conn-ssl-truststore-location %s --conn-ssl-truststore-password %s", keystore, keystorePassword, trustStore, trustStorePassword);
            command = String.format("%s /opt/cli-java/%s.jar %s --broker-uri amqps://%s:443%s --address %s --log-msgs json --conn-ssl-verify-host true ",
                    javaPrefix, clientBase, clientType, brokerUri, clientOptions, clientDestination);
        } else {
            command = String.format("%s-%s --broker %s:%s%s --address %s --log-msgs json",
                    clientBase, clientType, destinationUrl, destinationPort, clientOptions, clientDestination);
        }

        if (messageCount != -2) {
            command += " --count " + messageCount;
        }
        if (timeout != -2) {
            command += " --timeout " + timeout;
        }
        if (username != null) {
            command += " --conn-username " + username;
        }
        if (password != null) {
            command += " --conn-password " + password;
        }
        return command.split(" ");
    }

}
