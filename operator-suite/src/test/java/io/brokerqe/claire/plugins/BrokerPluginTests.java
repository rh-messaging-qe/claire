/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.plugins;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisAddress;
import io.amq.broker.v1beta1.ActiveMQArtemisBuilder;
import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.clients.ClientType;
import io.brokerqe.claire.junit.TestValidSince;
import io.fabric8.kubernetes.api.model.Pod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

@TestValidSince(ArtemisVersion.VERSION_2_33)
public class BrokerPluginTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(BrokerPluginTests.class);
    private final String testNamespace = getRandomNamespaceName("broker-plugin-tests", 3);

    @BeforeAll
    void setupClusterOperator() {
        setupDefaultClusterOperator(testNamespace);
    }

    @AfterAll
    void teardownClusterOperator() {
        teardownDefaultClusterOperator(testNamespace);
    }

    @AfterEach
    void cleanUp() {
        cleanResourcesAfterTest(testNamespace);
    }

    @Test
    void testLoggingActiveMQServerPlugin() {
        String brokerName = "brokerplugin";
        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
                .editOrNewMetadata()
                    .withName(brokerName)
                    .withNamespace(testNamespace)
                .endMetadata()
                .editOrNewSpec()
                    .editOrNewDeploymentPlan()
                        .withSize(1)
                    .endDeploymentPlan()
                    .withBrokerProperties(List.of("brokerPlugins.\"org.apache.activemq.artemis.core.server.plugin.impl.LoggingActiveMQServerPlugin.class\".init=LOG_CONSUMER_EVENTS=true,LOG_SENDING_EVENTS=true,LOG_DELIVERING_EVENTS=true"))
                    .addNewAcceptor()
                        .withExpose(true)
                        .withProtocols("amqp")
                        .withPort(5672)
                        .withSslEnabled(false)
                        .withNeedClientAuth(false)
                        .withName("amqp-acceptor")
                    .endAcceptor()
                .endSpec().build();
        broker = ResourceManager.createArtemis(testNamespace, broker);

        ActiveMQArtemisAddress testAddress = ResourceManager.createArtemisAddress(testNamespace, "orders", "orders");
        Pod brokerPod = getClient().listPodsByPrefixName(testNamespace, brokerName).get(0);
        LOGGER.info("[{}] Send & receive some messages", testNamespace);
        testMessaging(ClientType.BUNDLED_AMQP, testNamespace, brokerPod, testAddress, 2);
        testMessaging(ClientType.BUNDLED_CORE, testNamespace, brokerPod, testAddress, 2);

        LOGGER.info("[{}] Checking broker logs for LoggingActiveMQServerPlugin tracing of messages", testNamespace);
        String artemisLogs = getClient().getLogsFromPod(brokerPod);
        LOGGER.info("[{}] Ensure artemis pod logs contains org.apache.activemq.artemis.core.server.plugin.impl messages", testNamespace);

        assertThat(artemisLogs, allOf(
                containsString("CoreMessage"),
                containsString("AMQPStandardMessage"),
                containsString("AMQPStandardMessage"),
                containsString("org.apache.activemq.artemis.core.server.plugin.impl")
                )
        );
        ResourceManager.deleteArtemis(testNamespace, broker);
    }
}
