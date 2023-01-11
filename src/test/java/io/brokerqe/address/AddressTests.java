/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.address;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisAddress;
import io.amq.broker.v1beta1.activemqartemisspec.Acceptors;
import io.brokerqe.AbstractSystemTests;
import io.brokerqe.ResourceManager;
import io.brokerqe.configuration.BrokerConfigurationTests;
import io.brokerqe.operator.ArtemisFileProvider;
import io.fabric8.kubernetes.api.model.Pod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class AddressTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(BrokerConfigurationTests.class);
    private final String testNamespace = getRandomNamespaceName("broker-config-tests", 6);

    @BeforeAll
    void setupClusterOperator() {
        getClient().createNamespace(testNamespace, true);
        LOGGER.info("[{}] Creating new namespace to {}", testNamespace, testNamespace);
        operator = getClient().deployClusterOperator(testNamespace);
    }

    @AfterAll
    void teardownClusterOperator() {
        getClient().undeployClusterOperator(ResourceManager.getArtemisClusterOperator(testNamespace));
        if (!ResourceManager.isClusterOperatorManaged()) {
            LOGGER.info("[{}] Deleting namespace to {}", testNamespace, testNamespace);
            getClient().deleteNamespace(testNamespace);
        }
        ResourceManager.undeployAllClientsContainers();
    }

    @Test
    @Disabled
    void persistAddressAfterCoBrokerRestart() {
        ActiveMQArtemis broker = createArtemis(testNamespace, ArtemisFileProvider.getArtemisSingleExampleFile(), true);
        Acceptors amqpAcceptors = createAcceptor("amqp-owire-acceptor", "amqp,openwire");
        broker = addAcceptors(testNamespace, List.of(amqpAcceptors), broker);

        ActiveMQArtemisAddress myAddress = createArtemisAddress(testNamespace, ArtemisFileProvider.getAddressQueueExampleFile());
        // sending & receiving messages
        String brokerName = broker.getMetadata().getName();
        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, brokerName);
    }
}
