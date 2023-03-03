/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.specific;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.brokerqe.AbstractSystemTests;
import io.brokerqe.ArtemisVersion;
import io.brokerqe.Constants;
import io.brokerqe.ResourceManager;
import io.brokerqe.junit.TestValidSince;
import io.brokerqe.operator.ArtemisFileProvider;
import io.brokerqe.smoke.SmokeTests;
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

public class SpecificTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(SmokeTests.class);
    private final String testNamespace = getRandomNamespaceName("specific-tests", 6);

    @BeforeAll
    void setupClusterOperator() {
        setupDefaultClusterOperator(testNamespace);
    }

    @AfterAll
    void teardownClusterOperator() {
        teardownDefaultClusterOperator(testNamespace);
    }

    @Test
    @TestValidSince(ArtemisVersion.VERSION_2_28)
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
