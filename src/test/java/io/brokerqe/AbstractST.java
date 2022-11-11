/*
 * Copyright Broker-QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe;

import io.brokerqe.separator.TestSeparator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AbstractST implements TestSeparator {
    protected static final String KAFKA_NAMESPACE = "strimzi-kafka";
    protected static final String STRIMZI_NAMESPACE = "strimzi-operator";
    protected static final String CLIENTS_NAMESPACE = "strimzi-clients";
    protected static final String MONITORING_NAMESPACE = "strimzi-monitoring";
    protected static final String TWITTER_NAMESPACE = "strimzi-kafka";
    protected static final String DRAIN_CLEANER_NAMESPACE = "strimzi-drain-cleaner";

    private KubeClient client;

    public KubeClient getClient() {
        return this.client;
    }

    @BeforeAll
    void setupClient() {
        client = new KubeClient("default");
    }
}
