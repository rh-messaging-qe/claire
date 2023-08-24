/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.clients.container;


import io.brokerqe.claire.clients.DeployableClient;

import java.util.Map;

public class MqttV5Client extends MqttClient {

    public MqttV5Client(DeployableClient deployableClient, String destinationUrl, String destinationPort, String address, String queue, int messageCount) {
        super(deployableClient, destinationUrl, destinationPort, address, queue, messageCount);
    }

    public MqttV5Client(DeployableClient deployableClient, String destinationUrl, String destinationPort, String address, String queue, int messageCount, String username, String password) {
        super(deployableClient, destinationUrl, destinationPort, address, queue, messageCount, username, password);
    }

    public MqttV5Client(DeployableClient deployableClient, String brokerUri, String address, String queue, int messageCount, String saslMechanism, String keystore, String keystorePassword, String trustStore, String trustStorePassword) {
        super(deployableClient, brokerUri, address, queue, messageCount, saslMechanism, keystore, keystorePassword, trustStore, trustStorePassword);
    }

    public MqttV5Client(DeployableClient deployableClient, String brokerUri, Map<String, String> senderOptions, Map<String, String> receiverOptions) {
        super(deployableClient, brokerUri, senderOptions, receiverOptions);
    }

    public MqttV5Client(DeployableClient deployableClient, String brokerUri, Map<String, String> senderOptions, Map<String, String> receiverOptions, boolean secured) {
        super(deployableClient, brokerUri, senderOptions, receiverOptions, secured);
    }

    public MqttV5Client(DeployableClient deployableClient, String destinationUrl, String destinationPort, Map<String, String> testOptions) {
        super(deployableClient, destinationUrl, destinationPort, testOptions);
    }

    String getProtocolVersion() {
        return "5";
    }

}
