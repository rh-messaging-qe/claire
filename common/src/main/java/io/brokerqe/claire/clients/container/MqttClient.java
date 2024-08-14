/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.clients.container;

import io.brokerqe.claire.Constants;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.clients.DeployableClient;
import io.brokerqe.claire.clients.MessagingClient;
import io.brokerqe.claire.exception.ClaireRuntimeException;
import io.brokerqe.claire.executor.Executor;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public abstract class MqttClient extends SystemtestClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttClient.class);
    protected Map<String, String> testOptions = null;
    private String destinationUrl;
    private String destinationPort;
    private String destinationAddress;
    private String destinationQueue;
    String clientDestination;
    private String username;
    private String password;
    private int messageCount;
    private String keystorePassword;
    private String trustStore;
    private String trustStorePassword;
    private String keystore;
    private String saslMechanism;
    private boolean secured;
    private Executor subscriberExecutor;
    private List<JSONObject> sentMessages;
    private List<JSONObject> receivedMessages;
    private final String identifierSender = TestUtils.generateRandomName();
    private final String identifierReceiver = identifierSender + "receiver";
    private String randomContent = null;

    String getCliExecutableBase() {
        return "cli-hivemq-mqtt";
    }

    abstract String getProtocolVersion();

    public MqttClient(DeployableClient deployableClient, String destinationUrl, String destinationPort, String address, String queue, int messageCount) {
        this.deployableClient = deployableClient;
        this.destinationUrl = destinationUrl;
        this.destinationPort = destinationPort;
        this.destinationAddress = address;
        this.destinationQueue = queue;
        this.messageCount = messageCount;

    }

    public MqttClient(DeployableClient deployableClient, String destinationUrl, String destinationPort, String address, String queue, int messageCount, String username, String password) {
        this.deployableClient = deployableClient;
        this.destinationUrl = destinationUrl;
        this.destinationPort = destinationPort;
        this.destinationAddress = address;
        this.destinationQueue = queue;
        this.messageCount = messageCount;
        this.username = username;
        this.password = password;
    }

    public MqttClient(DeployableClient deployableClient, String brokerUri, String address, String queue, int messageCount, String saslMechanism, String keystore, String keystorePassword, String trustStore, String trustStorePassword) {
        this.deployableClient = deployableClient;
        assignBrokerUri(brokerUri);
        this.destinationAddress = address;
        this.destinationQueue = queue;
        this.messageCount = messageCount;
        this.saslMechanism = saslMechanism;
        this.keystore = keystore;
        this.keystorePassword = keystorePassword;
        this.trustStore = trustStore;
        this.trustStorePassword = trustStorePassword;
    }

    public MqttClient(DeployableClient deployableClient, String brokerUri, Map<String, String> senderOptions, Map<String, String> receiverOptions) {
        this.deployableClient = deployableClient;
        assignBrokerUri(brokerUri);
        this.senderOptions = senderOptions;
        this.receiverOptions = receiverOptions;
    }

    public MqttClient(DeployableClient deployableClient, String brokerUri, Map<String, String> senderOptions, Map<String, String> receiverOptions, boolean secured) {
        LOGGER.info("[{}] Creating client to {}", deployableClient.getContainerName(), brokerUri);
        this.secured = secured;
        this.deployableClient = deployableClient;
        assignBrokerUri(brokerUri);
        this.senderOptions = senderOptions;
        this.receiverOptions = receiverOptions;
    }

    public MqttClient(DeployableClient deployableClient, String destinationUrl, String destinationPort, Map<String, String> testOptions) {
        this.deployableClient = deployableClient;
        this.destinationUrl = destinationUrl;
        this.destinationPort = destinationPort;
        this.testOptions = testOptions;
    }

    private void assignBrokerUri(String brokerUri) {
        String[] uris = brokerUri.split(":");
        this.destinationUrl = uris[0];
        this.destinationPort = uris[1];
    }

    @Override
    public int sendMessages() {
        String cmdOutput;
        String[] command = constructClientCommand(SENDER);
        cmdOutput = (String) deployableClient.getExecutor().executeCommand(Constants.DURATION_3_MINUTES, command);
        LOGGER.debug("[{}][TX] \n{}", deployableClient.getContainerName(), cmdOutput);
        this.sentMessages = parseMessages(cmdOutput, SENDER);
        return sentMessages.size();
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
            String[] command = constructClientCommand(MessagingClient.RECEIVER);
            cmdOutput = (String) deployableClient.getExecutor().executeCommand(duration, command);
            LOGGER.debug("[{}][RX] \n{}", deployableClient.getContainerName(), cmdOutput);
            this.receivedMessages = parseMessages(cmdOutput, RECEIVER);
            return receivedMessages.size();
        }
    }

    @Override
    public void subscribe() {
        String[] command;
        if (receiverOptions != null) {
            command = constructClientCommandOptions(MessagingClient.RECEIVER);
        } else {
            command = constructClientCommand(MessagingClient.RECEIVER, 60);
        }
        subscriberExecutor = deployableClient.getExecutor();
        subscriberExecutor.execBackgroundCommand(command);
        LOGGER.debug("[{}][SUBSCRIBE] Sleeping for while to ensure subscriber is connected before moving forward", deployableClient.getContainerName());
        TestUtils.threadSleep(Constants.DURATION_5_SECONDS);
    }

    @Override
    public void unsubscribe() {
        String[] command = {"sh", "-c",
                String.format("for proc in /proc/[0-9]*/cmdline; " +
                        "do echo $(basename $(dirname $proc)) $(cat $proc | tr \"\\0\" \" \"); done | " +
                        "grep %s | grep -v grep | cut -d ' ' -f 1 | xargs kill", identifierReceiver)};
        deployableClient.getExecutor().executeCommand(command);
        LOGGER.debug("[{}][UNSUBSCRIBE] MQTT Client with {}", deployableClient.getContainerName(), identifierReceiver);
    }

    public String testBroker() {
        String cmdOutput;
        String[] command = constructClientCommand("test");
        cmdOutput = (String) deployableClient.getExecutor().executeCommand(Constants.DURATION_5_MINUTES, command);
        LOGGER.debug("[{}][TEST] \n{}", deployableClient.getContainerName(), cmdOutput);
        return cmdOutput;
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
        if (sentMessages == null || receivedMessages == null) {
            return false;
        }
        if (sentMessages.size() != receivedMessages.size()) {
            LOGGER.warn("[{}] Sent {} and received {} messages are not same!", deployableClient.getContainerName(), sentMessages.size(), receivedMessages.size());
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
                }).toList();
                for (JSONObject message : sentMessages) {
                    if (!receivedIds.contains(message.get("id"))) {
                        LOGGER.warn("[{}] Unable to find/compare messageId {}", deployableClient.getContainerName(), message);
                        return false;
                    }
                }
            } catch (JSONException e) {
                LOGGER.error("[{}] Unable to parse/compare messages! {}", deployableClient.getContainerName(), e.getMessage());
            }
            LOGGER.debug("[{}] All messages are same. Good.", deployableClient.getContainerName());
            return true;
        }
    }

    public int getSubscribedMessages() {
        String cmdOutput = subscriberExecutor.getBackgroundCommandData(5);
        this.receivedMessages = parseMessages(cmdOutput, RECEIVER);
        return receivedMessages.size();
    }

    private String[] constructClientCommand(String clientType) {
        if (senderOptions != null || receiverOptions != null || testOptions != null) {
            return constructClientCommandOptions(clientType);
        } else {
            return constructClientCommand(clientType, -2);
        }
    }

    private String[] constructClientCommandOptions(String clientType) {
        Map<String, String> options;
        StringBuilder commandBuild = new StringBuilder(getCliExecutableBase());
        commandBuild.append("-");
        // we need to use debug mode, to be able to parse sent/received message
        if (clientType.equals(SENDER)) {
            options = senderOptions;
            options.put("identifier", identifierSender);
            commandBuild.append("publish -d --userProperty id=" + TestUtils.getRandomString(6) + "");
        } else if (clientType.equals(RECEIVER)) {
            options = receiverOptions;
            options.put("identifier", identifierReceiver);
            commandBuild.append("subscribe -d");
        } else if (clientType.equals("test")) {
            options = testOptions;
            commandBuild.append("test");
        } else {
            throw new ClaireRuntimeException("Unknown action for clientOptions");
        }

        commandBuild.append(" --host ").append(destinationUrl);
        commandBuild.append(" --port ").append(destinationPort);
        for (Map.Entry<String, String> entry : options.entrySet()) {
            if (entry.getValue() == null || entry.getValue().equals("")) {
                commandBuild.append(" --").append(entry.getKey());
            } else {
                commandBuild.append(" --").append(entry.getKey()).append("=").append(entry.getValue());
            }
        }
        return commandBuild.toString().split(" ");
    }

    private String[] constructClientCommand(String clientType, int timeout) {
        // cli-hivemq-mqtt-publish -t test  -h 10.128.3.9 -p 1883 --mqttVersion=5 -d -i=tralal --qos=2 -m <random:text>
        // cli-hivemq-mqtt-subscribe -t test  -h 10.128.3.9 -p 1883 --mqttVersion=5 -d -i=tralal --qos=2
        String command;
        String client;
        String identifier;
        if (!destinationAddress.equals(destinationQueue)) {
            LOGGER.warn("[{}] MQTT client does not support FQQN (!?) Using provided address instead {}", deployableClient.getContainerName(), destinationAddress);
        }
        clientDestination = destinationAddress;
        if (clientType.equals(SENDER)) {
            client = getCliExecutableBase() + "-publish";
            randomContent = TestUtils.getRandomString(26);
            identifier = identifierSender;
        } else if (clientType.equals(RECEIVER)) {
            client = getCliExecutableBase() + "-subscribe";
            identifier = identifierReceiver;
        } else {
            throw new ClaireRuntimeException("Unsupported client type!" + clientType);
        }
//        if (secured) {
//            // java -Djavax.net.ssl.keyStore="/etc/ssl-stores/$CLIENT_KEYSTORE" -Djavax.net.ssl.keyStorePassword="$PASSWORD" -Djavax.net.ssl.trustStore="/etc/ssl-stores/$CLIENT_TRUSTSTORE" -Djavax.net.ssl.trustStorePassword="$PASSWORD"
//            // -jar /client_executable/cli-qpid-jms.jar sender --broker-uri $broker_address:443 --log-msgs json --conn-auth-mechanisms PLAIN --conn-username admin --conn-password admin --address "MyQueue0" --count 1 --msg-content "content" --conn-ssl-verify-host true
//            String javaPrefix = String.format("java -Djavax.net.ssl.keyStore=%s " +
//                            "-Djavax.net.ssl.keyStorePassword=%s " +
//                            "-Djavax.net.ssl.trustStore=%s " +
//                            "-Djavax.net.ssl.trustStorePassword=%s -jar",
//                    keystore, keystorePassword, trustStore, trustStorePassword);
////            https://github.com/rh-messaging/cli-java/issues/139
////            String tlsOptions = String.format("--conn-ssl-keystore-location %s --conn-ssl-keystore-password %s --conn-ssl-truststore-location %s --conn-ssl-truststore-password %s", keystore, keystorePassword, trustStore, trustStorePassword);
//            command = String.format("%s /opt/cli-java/%s.jar %s --broker-uri amqps://%s:443%s --address %s --log-msgs json --conn-ssl-verify-host true ",
//                    javaPrefix, clientBase, clientType, brokerUri, clientOptions, clientDestination);
//        } else {
        command = String.format("%s -d --host %s --port %s --topic %s --identifier %s", client, destinationUrl, destinationPort, clientDestination, identifier);
//        }
        // TODO: make it configurable from outside
        if (clientType.equals(SENDER)) {
            command += " --message " + randomContent;
            command += " --userProperty id=" + TestUtils.getRandomString(6);
        }

        if (username != null) {
            command += " --user " + username;
        }
        if (password != null) {
            command += " --password " + password;
        }
        return command.split(" ");
    }

    private List<JSONObject> parseMessages(String output, String clientType) {
        if (output == null) {
            throw new ClaireRuntimeException("Provided unexpected empty/null command output!");
        }
        List<JSONObject> jsonMessages = new ArrayList<>();
        List<String> lines = List.of(output.replaceAll("\\s+", " ").split("Client "));
        Map<String, String> data = new HashMap<>();
        for (String line : lines) {
            if (clientType.equals(SENDER) && line.contains("sending PUBLISH") || clientType.equals(RECEIVER) && line.contains("received PUBLISH")) {
                String workLine = line.substring(line.indexOf("MqttPublish{") + "MqttPublish{".length(), line.length() - 1);
                if (workLine.contains("userProperties=[(")) {
                    String userProps = workLine.substring(workLine.indexOf("userProperties=[") + "userProperties=[".length(), workLine.lastIndexOf(")]") + 1);
                    for (String prop : userProps.split("\\),")) {
                        prop = prop.replace("(", "");
                        prop = prop.replace(")", "");
                        String[] val = prop.split(",");
                        data.put(val[0].trim(), val[1].trim());
                    }
                    workLine = workLine.replace(", userProperties=[" + userProps + "]", "");
                }
                for (String item : workLine.split(",")) {
                    String[] splitted = item.split("=");
                    data.put(splitted[0].trim(), splitted[1].trim());
                }
                if (line.contains("content")) {
                    String content = line.substring(line.indexOf("('") + 2, line.indexOf("')"));
                    data.put("content", content);
                }
                jsonMessages.add(new JSONObject(data));
                break;
            }
        }
        return jsonMessages;
    }

}
