/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.smoke;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.operator.ArtemisFileProvider;
import io.brokerqe.claire.exception.WaitException;
import io.fabric8.kubernetes.api.model.Pod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ClusteredOperatorSmokeTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClusteredOperatorSmokeTests.class);
    private final String testNamespace = getRandomNamespaceName("cluster-tests", 3);
    private final String testNamespaceA = testNamespace + "a";
    private final String testNamespaceB = testNamespace + "b";

    @BeforeAll
    void setupClusterOperator() {
        getClient().createNamespace(testNamespace, true);
        getClient().createNamespace(testNamespaceA, true);
        getClient().createNamespace(testNamespaceB, true);
        LOGGER.info("[{}] Creating new namespace {}", testNamespace, testNamespace);
        // Operator will watch namespaces testNamespace and testNamespaceA
        operator = ResourceManager.deployArtemisClusterOperatorClustered(testNamespace, List.of(testNamespace, testNamespaceA));
    }

    @AfterAll
    void teardownClusterOperator() {
        if (ResourceManager.isClusterOperatorManaged()) {
            ResourceManager.undeployArtemisClusterOperator(operator);
        }
        ResourceManager.undeployAllClientsContainers();

        getClient().deleteNamespace(testNamespace);
        getClient().deleteNamespace(testNamespaceA);
        getClient().deleteNamespace(testNamespaceB);
    }

    @Test
    @Tag(Constants.TAG_OPERATOR)
    @Tag(Constants.TAG_SMOKE)
    void simpleBrokerClusteredDeploymentTest() {
        // testNamespace & testNamespaceA should work, testNamespaceB should fail
        LOGGER.info("[{}] Expecting PASS: Deploy broker in namespace {}", testNamespace, testNamespaceA);
        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, ArtemisFileProvider.getArtemisSingleExampleFile());
        String brokerName = broker.getMetadata().getName();
        LOGGER.info("[{}] Check if broker pod with name {} is present.", testNamespace, brokerName);
        List<Pod> brokerPods = getClient().listPodsByPrefixName(testNamespace, brokerName);
        assertThat(brokerPods.size(), is(1));

        ResourceManager.deleteArtemis(testNamespace, broker);
        brokerPods = getClient().listPodsByPrefixName(testNamespace, brokerName);
        assertThat(brokerPods.size(), is(0));

        // testNamespaceA - should work
        LOGGER.info("[{}] Expecting PASS: Deploy broker in namespace {}", testNamespace, testNamespaceA);
        ActiveMQArtemis brokerA = ResourceManager.createArtemis(testNamespaceA, ArtemisFileProvider.getArtemisSingleExampleFile());
        String brokerNameA = brokerA.getMetadata().getName();
        LOGGER.info("[{}] Check if broker pod with name {} is present.", testNamespaceA, brokerNameA);
        brokerPods = getClient().listPodsByPrefixName(testNamespaceA, brokerNameA);
        assertThat(brokerPods.size(), is(1));
        ResourceManager.deleteArtemis(testNamespaceA, brokerA);
        brokerPods = getClient().listPodsByPrefixName(testNamespaceA, brokerName);
        assertThat(brokerPods.size(), is(0));

        // testNamespaceB - should fail
        LOGGER.info("[{}] Expecting FAIL: deploy broker in namespace {}", testNamespace, testNamespaceB);
        ActiveMQArtemis brokerB = ResourceManager.createArtemis(testNamespaceB, ArtemisFileProvider.getArtemisSingleExampleFile(), false);
        assertThrows(WaitException.class, () -> ResourceManager.waitForBrokerDeployment(testNamespaceB, brokerB, false, Constants.DURATION_30_SECONDS));
        assertNull(getClient().getStatefulSet(testNamespaceB, brokerName + "-ss"));
        ResourceManager.deleteArtemis(testNamespaceB, brokerB);
    }
}
