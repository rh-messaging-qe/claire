/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.clients;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface MessagingClient {

    Logger LOGGER = LoggerFactory.getLogger(MessagingClient.class);

    final String CONSUMER = "consumer";
    final String PRODUCER = "producer";
    final String SENDER = "sender";
    final String RECEIVER = "receiver";

    int sendMessages();

    int receiveMessages();

    Object getMessages();

    boolean compareMessages();

    void subscribe();

}
