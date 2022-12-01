/*
 * Copyright Strimzi and Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe;

import java.time.Duration;

public interface Constants {

    String CRD_ACTIVEMQ_ARTEMIS_GROUP = "broker.amq.io";
    String CRD_ACTIVEMQ_ARTEMIS = "activemqartemises" + "." + CRD_ACTIVEMQ_ARTEMIS_GROUP;
    String CRD_ACTIVEMQ_ARTEMIS_ADDRESS = "activemqartemisaddresses" + "." + CRD_ACTIVEMQ_ARTEMIS_GROUP;
    String CRD_ACTIVEMQ_ARTEMIS_SECURITY = "activemqartemissecurities" + "." + CRD_ACTIVEMQ_ARTEMIS_GROUP;
    String CRD_ACTIVEMQ_ARTEMIS_SCALEDOWN = "activemqartemisscaledowns" + "." + CRD_ACTIVEMQ_ARTEMIS_GROUP;

    String WATCH_ALL_NAMESPACES = "*";
    String ARTEMIS_BROKER_STATEFUL_SET_NAME = "artemis-broker-ss";

    long DURATION_5_SECONDS = Duration.ofSeconds(5).toMillis();
    long DURATION_3_MINUTES = Duration.ofMinutes(3).toMillis();
}
