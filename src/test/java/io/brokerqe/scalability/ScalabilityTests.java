/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.scalability;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.brokerqe.AbstractSystemTests;
import io.brokerqe.Constants;
import io.brokerqe.ResourceManager;
import io.fabric8.kubernetes.api.model.Pod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void simpleScalabilityTest() {
        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, "scale-artemis", 1, true, false, true);

        LOGGER.info("[{}] ScaleUp from 1 to 3", testNamespace);
        broker = doArtemisScale(broker, 1, 3);
        LOGGER.info("[{}] ScaleUp from 3 to 10", testNamespace);
        broker = doArtemisScale(broker, 3, 10);
        LOGGER.info("[{}] ScaleUp from 10 to 16", testNamespace);
        broker = doArtemisScale(broker, 10, 16);

        LOGGER.info("[{}] ScaleDown from 16 to 5", testNamespace);
        broker = doArtemisScale(broker, 16, 5);
        LOGGER.info("[{}] ScaleDown from 5 to 1", testNamespace);
        broker = doArtemisScale(broker, 5, 1);

        ResourceManager.deleteArtemis(testNamespace, broker, true, Constants.DURATION_2_MINUTES);
    }

    private ActiveMQArtemis doArtemisScale(ActiveMQArtemis broker, int previousSize, int newSize) {
        broker.getSpec().getDeploymentPlan().setSize(newSize);
        broker = ResourceManager.getArtemisClient().inNamespace(testNamespace).resource(broker).createOrReplace();
        long waitTime = Math.abs(previousSize - newSize) * Constants.DURATION_2_MINUTES;

        if (previousSize > newSize) {
            waitForScaleDownDrainer(testNamespace, operator.getOperatorName(),
                    broker.getMetadata().getName(), waitTime, previousSize - newSize);
        } else {
            ResourceManager.waitForBrokerDeployment(testNamespace, broker, true, waitTime);
        }
        List<Pod> brokers = getClient().listPodsByPrefixName(testNamespace, broker.getMetadata().getName());
        assertEquals(brokers.size(), newSize);
        return broker;
    }
}
