/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.clients.bundled;

public class BundledCoreMessagingClient extends BundledMessagingClient {

    public BundledCoreMessagingClient(BundledClientOptions options) {
        super(options);
    }

    public String getProtocol() {
        return "core";
    }
}
