/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.clients.bundled;

import io.brokerqe.claire.clients.Protocol;

import java.util.Locale;

public class BundledAmqpMessagingClient extends BundledMessagingClient {

    public BundledAmqpMessagingClient(BundledClientOptions options) {
        super(options);
    }

    @Override
    String getProtocol() {
        return Protocol.AMQP.name().toLowerCase(Locale.ROOT);
    }
}
