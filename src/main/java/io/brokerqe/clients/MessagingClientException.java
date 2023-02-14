/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.clients;

public class MessagingClientException extends RuntimeException {
    public MessagingClientException(String exception) {
        super(exception);
    }

    public MessagingClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
