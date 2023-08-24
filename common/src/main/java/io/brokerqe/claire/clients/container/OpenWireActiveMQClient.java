/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.clients.container;

import io.brokerqe.claire.clients.DeployableClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;


public class OpenWireActiveMQClient extends BaseJMSClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenWireActiveMQClient.class);

    String getCliExecutableBase() {
        return "cli-activemq";
    }

    public OpenWireActiveMQClient(DeployableClient deployableClient, String destinationUrl, String destinationPort, String address, String queue, int messageCount) {
        super(deployableClient, destinationUrl, destinationPort, address, queue, messageCount);
    }

    public OpenWireActiveMQClient(DeployableClient deployableClient, String destinationUrl, String destinationPort, String address, String queue, int messageCount, String username, String password) {
        super(deployableClient, destinationUrl, destinationPort, address, queue, messageCount, username, password);
    }

    public OpenWireActiveMQClient(DeployableClient deployableClient, String brokerUri, String address, String queue, int messageCount, String saslMechanism, String keystore, String keystorePassword, String trustStore, String trustStorePassword) {
        super(deployableClient, brokerUri, address, queue, messageCount, saslMechanism, keystore, keystorePassword, trustStore, trustStorePassword);
    }

    public OpenWireActiveMQClient(DeployableClient deployableClient, String brokerUri, Map<String, String> senderOptions, Map<String, String> receiverOptions) {
        super(deployableClient, brokerUri, senderOptions, receiverOptions);
    }

    public OpenWireActiveMQClient(DeployableClient deployableClient, String brokerUri, Map<String, String> senderOptions, Map<String, String> receiverOptions, boolean secured) {
        super(deployableClient, brokerUri, senderOptions, receiverOptions, secured);
    }

}
