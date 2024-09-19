/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.clients.bundled;

import io.brokerqe.claire.clients.Protocol;

import java.util.Locale;

public class BundledCoreMessagingClient extends BundledMessagingClient {

    public BundledCoreMessagingClient(BundledClientOptions options) {
        super(options);
    }

    public String getProtocol() {
        return Protocol.CORE.name().toLowerCase(Locale.ROOT);
    }
}
