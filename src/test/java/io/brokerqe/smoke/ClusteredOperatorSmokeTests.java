/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.smoke;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.brokerqe.AbstractSystemTests;
import io.brokerqe.ResourceManager;
import io.brokerqe.WaitException;
import io.brokerqe.operator.ArtemisFileProvider;
import io.fabric8.kubernetes.api.model.Pod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ClusteredOperatorSmokeTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(SmokeTests.class);
    private final String testNamespace = getRandomNamespaceName("cluster-test", 4);
    private final String testNamespaceA = getRandomNamespaceName("cluster-testa", 4);
    private final String testNamespaceB = getRandomNamespaceName("cluster-testb", 4);

    @BeforeAll
    void setupClusterOperator() {
        getClient().createNamespace(testNamespace, true);
        getClient().createNamespace(testNamespaceA, true);
        getClient().createNamespace(testNamespaceB, true);
        LOGGER.info("[{}] Creating new namespace to {}", testNamespace, testNamespace);
        // Operator will watch namespaces testNamespace and testNamespaceA
        operator = getClient().deployClusterOperator(testNamespace, List.of(testNamespace, testNamespaceA));
    }

    @AfterAll
    void teardownClusterOperator() {
        getClient().undeployClusterOperator(ResourceManager.getArtemisClusterOperator(testNamespace));
        if (!ResourceManager.isClusterOperatorManaged()) {
            LOGGER.info("[{}] Deleting namespace to {}", testNamespace, testNamespace);
            getClient().deleteNamespace(testNamespace);
            getClient().deleteNamespace(testNamespaceA);
            getClient().deleteNamespace(testNamespaceB);
        }

        ResourceManager.undeployAllClientsContainers();
    }

    @Test
    void simpleBrokerClusteredDeploymentTest() {
        // testNamespace & testNamespaceA should work, testNamespaceB should fail
        LOGGER.info("[{}] Expecting PASS: Deploy broker in namespace {}", testNamespace, testNamespaceA);
        ActiveMQArtemis broker = createArtemis(testNamespace, ArtemisFileProvider.getArtemisSingleExampleFile());
        String brokerName = broker.getMetadata().getName();
        LOGGER.info("[{}] Check if broker pod with name {} is present.", testNamespace, brokerName);
        List<Pod> brokerPods = getClient().listPodsByPrefixInName(testNamespace, brokerName);
        assertThat(brokerPods.size(), is(1));

        deleteArtemis(testNamespace, broker);
        brokerPods = getClient().listPodsByPrefixInName(testNamespace, brokerName);
        assertThat(brokerPods.size(), is(0));

        // testNamespaceA - should work
        LOGGER.info("[{}] Expecting PASS: Deploy broker in namespace {}", testNamespace, testNamespaceA);
        ActiveMQArtemis brokerA = createArtemis(testNamespaceA, ArtemisFileProvider.getArtemisSingleExampleFile());
        String brokerNameA = brokerA.getMetadata().getName();
        LOGGER.info("[{}] Check if broker pod with name {} is present.", testNamespaceA, brokerNameA);
        brokerPods = getClient().listPodsByPrefixInName(testNamespaceA, brokerNameA);
        assertThat(brokerPods.size(), is(1));
        deleteArtemis(testNamespaceA, brokerA);
        brokerPods = getClient().listPodsByPrefixInName(testNamespaceA, brokerName);
        assertThat(brokerPods.size(), is(0));

        // testNamespaceB - should fail
        LOGGER.info("[{}] Expecting FAIL: deploy broker in namespace {}", testNamespace, testNamespaceB);
        assertThrows(WaitException.class, () -> createArtemis(testNamespaceB, ArtemisFileProvider.getArtemisSingleExampleFile()));
        assertNull(getClient().getStatefulSet(testNamespaceB, brokerName + "-ss"));
    }
}
