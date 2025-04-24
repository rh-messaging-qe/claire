/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.scalability;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.KubernetesPlatform;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.junit.DisabledTestPlatform;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScalabilityTests extends AbstractSystemTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScalabilityTests.class);
    private final String testNamespace = getRandomNamespaceName("scalability-tests", 3);

    @BeforeAll
    void setupClusterOperator() {
        setupDefaultClusterOperator(testNamespace);
    }

    @AfterAll
    void teardownClusterOperator() {
        teardownDefaultClusterOperator(testNamespace);
    }

    @Test
    @DisabledTestPlatform(platforms = { KubernetesPlatform.MICROSHIFT })
    void simpleScalabilityTest() {
        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, "scale-artemis", 1, true, false, true, false);

        LOGGER.info("[{}] ScaleUp from 1 to 3", testNamespace);
        broker = doArtemisScale(testNamespace, broker, 1, 3);
        LOGGER.info("[{}] ScaleUp from 3 to 10", testNamespace);
        broker = doArtemisScale(testNamespace, broker, 3, 10);
        LOGGER.info("[{}] ScaleUp from 10 to 14", testNamespace);
        broker = doArtemisScale(testNamespace, broker, 10, 14);

        LOGGER.info("[{}] ScaleDown from 14 to 5", testNamespace);
        broker = doArtemisScale(testNamespace, broker, 14, 5);
        LOGGER.info("[{}] ScaleDown from 5 to 1", testNamespace);
        broker = doArtemisScale(testNamespace, broker, 5, 1);

        ResourceManager.deleteArtemis(testNamespace, broker, true, Constants.DURATION_2_MINUTES);
    }

}
