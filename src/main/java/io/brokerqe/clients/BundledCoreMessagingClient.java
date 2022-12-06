/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.clients;

import io.fabric8.kubernetes.api.model.Pod;

public class BundledCoreMessagingClient extends BundledMessagingClient {

    public BundledCoreMessagingClient(Pod sourcePod, String destinationUrl, String destinationPort, String destinationAddress, String destinationQueue, int messageCount) {
        super(sourcePod, destinationUrl, destinationPort, destinationAddress, destinationQueue, messageCount);
    }

    public String getProtocol() {
        return "core";
    }
}
