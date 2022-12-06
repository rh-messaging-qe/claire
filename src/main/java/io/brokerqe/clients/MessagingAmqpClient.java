/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.clients;

import org.apache.commons.lang.NotImplementedException;

public class MessagingAmqpClient implements MessagingClient {

    @Override
    public int sendMessages() {
        throw new NotImplementedException("containerized clients not implemented yet");
    }

    @Override
    public int receiveMessages() {
        throw new NotImplementedException("containerized clients not implemented yet");
    }

    @Override
    public Object getMessages() {
        throw new NotImplementedException("containerized clients not implemented yet");
    }

    @Override
    public boolean compareMessages() {
        throw new NotImplementedException("containerized clients not implemented yet");
    }
}
