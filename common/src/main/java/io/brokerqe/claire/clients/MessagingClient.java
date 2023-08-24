/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.clients;


public interface MessagingClient {

    String CONSUMER = "consumer";
    String PRODUCER = "producer";
    String SENDER = "sender";
    String RECEIVER = "receiver";

    int sendMessages();
    int receiveMessages();
    void subscribe();
    Object getSentMessages();
    Object getReceivedMessages();
    boolean compareMessages();
    boolean compareMessages(Object sentMessages, Object receivedMessages);

}
