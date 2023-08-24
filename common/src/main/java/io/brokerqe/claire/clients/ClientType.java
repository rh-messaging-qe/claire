/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.clients;

public enum ClientType {
    BUNDLED_CORE,
    BUNDLED_AMQP,
    ST_AMQP_QPID_JMS,
    ST_AMQP_PROTON_DOTNET,
    ST_AMQP_PROTON_CPP,
    ST_AMQP_PROTON_PYTHON,
    ST_AMQP_RHEA,
    ST_MQTT_PAHO,
    ST_MQTT_V3,
    ST_MQTT_V5
}
