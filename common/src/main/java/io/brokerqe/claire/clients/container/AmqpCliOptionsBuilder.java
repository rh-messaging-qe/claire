/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.clients.container;

import java.util.HashMap;
import java.util.Map;

public class AmqpCliOptionsBuilder {
    private Map<String, String> map = new HashMap<>();

    public AmqpCliOptionsBuilder connUsername(String username) {
        map.put("conn-username", username);
        return this;
    }

    public AmqpCliOptionsBuilder connPassword(String password) {
        map.put("conn-password", password);
        return this;
    }

    public AmqpCliOptionsBuilder address(String address) {
        map.put("address", address);
        return this;
    }

    public AmqpCliOptionsBuilder count(int count) {
        map.put("count", String.valueOf(count));
        return this;
    }

    public AmqpCliOptionsBuilder connSsl(boolean ssl) {
        map.put("conn-ssl", String.valueOf(ssl));
        return this;
    }

    public AmqpCliOptionsBuilder connSslCertificate(String certificate) {
        map.put("conn-ssl-certificate", certificate);
        return this;
    }

    public AmqpCliOptionsBuilder connSslCertificatePassword(String certificatePassword) {
        map.put("conn-ssl-certificate-password", certificatePassword);
        return this;
    }

    /// commit, rollback
    public AmqpCliOptionsBuilder txEndloopAction(String txEndloopAction) {
        map.put("tx-endloop-action", txEndloopAction);
        return this;
    }

    public AmqpCliOptionsBuilder txSize(String txSize) {
        map.put("tx-size", String.valueOf(txSize));
        return this;
    }

    /// commit, rollback
    public AmqpCliOptionsBuilder txAction(String txAction) {
        map.put("tx-action", txAction);
        return this;
    }

    public AmqpCliOptionsBuilder timeout(int timeout) {
        map.put("timeout", String.valueOf(timeout));
        return this;
    }

    public AmqpCliOptionsBuilder msgDurable(boolean msgDurable) {
        map.put("msg-durable", String.valueOf(msgDurable));
        return this;
    }

    public AmqpCliOptionsBuilder msgProperty(String key, String value) {
        map.put("msg-property", String.format("%s=%s", key, value));
        return this;
    }

    public AmqpCliOptionsBuilder msgCorrelationId(String correlationId) {
        map.put("msg-correlation-id", correlationId);
        return this;
    }

    public Map<String, String> build() {
        return new HashMap<>(map);
    }
}
