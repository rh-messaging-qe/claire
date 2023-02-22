/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.clients;

import io.fabric8.kubernetes.api.model.Pod;

public class BundledAmqpMessagingClient extends BundledMessagingClient {

    public BundledAmqpMessagingClient(Pod sourcePod, String destinationUrl, String destinationPort, String destinationAddress, String destinationQueue, int messageCount) {
        super(sourcePod, destinationUrl, destinationPort, destinationAddress, destinationQueue, messageCount);
    }

    public BundledAmqpMessagingClient(Pod sourcePod, String destinationUrl, String destinationPort, String destinationAddress,
                                      String destinationQueue, int messageCount, String username, String password) {
        super(sourcePod, destinationUrl, destinationPort, destinationAddress, destinationQueue, messageCount, username, password);
    }

    @Override
    String getProtocol() {
        return "amqp";
    }
}
