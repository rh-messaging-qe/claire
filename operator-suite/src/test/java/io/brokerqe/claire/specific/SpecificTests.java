/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.specific;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.junit.TestValidSince;
import io.brokerqe.claire.operator.ArtemisFileProvider;
import io.brokerqe.claire.smoke.SmokeTests;
import io.fabric8.kubernetes.api.model.DeletionPropagation;
import io.fabric8.kubernetes.api.model.Pod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@TestValidSince(ArtemisVersion.VERSION_2_28)
public class SpecificTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(SmokeTests.class);
    private final String testNamespace = getRandomNamespaceName("specific-tests", 3);

    @BeforeAll
    void setupClusterOperator() {
        setupDefaultClusterOperator(testNamespace);
    }

    @AfterAll
    void teardownClusterOperator() {
        teardownDefaultClusterOperator(testNamespace);
    }

    @Test
    void defaultSingleBrokerDeploymentTest() {
        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, ArtemisFileProvider.getArtemisSingleExampleFile(), true);
        String brokerName = broker.getMetadata().getName();
        LOGGER.info("[{}] Check if broker pod with name {} is present.", testNamespace, brokerName);
        List<Pod> brokerPods = getClient().listPodsByPrefixName(testNamespace, brokerName);
        assertThat(brokerPods.size(), is(1));
        ResourceManager.getArtemisClient().inNamespace(testNamespace).resource(broker).withPropagationPolicy(DeletionPropagation.FOREGROUND).delete();
        assertDoesNotThrow(() -> ResourceManager.waitForBrokerDeletion(testNamespace, brokerName, Constants.DURATION_1_MINUTE));
        ResourceManager.deleteArtemis(testNamespace, broker);
    }
}
