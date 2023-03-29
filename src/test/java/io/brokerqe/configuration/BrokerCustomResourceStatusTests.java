/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.configuration;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisBuilder;
import io.amq.broker.v1beta1.ActiveMQArtemisSpec;
import io.amq.broker.v1beta1.activemqartemisstatus.Conditions;
import io.brokerqe.AbstractSystemTests;
import io.brokerqe.Constants;
import io.brokerqe.ResourceManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class BrokerCustomResourceStatusTests extends AbstractSystemTests  {
    private static final Logger LOGGER = LoggerFactory.getLogger(BrokerConfigurationTests.class);

    private final String testNamespace = getRandomNamespaceName("crstatus-tests", 6);
    private static final String CONFIG_BROKER_NAME = "broker-status";

    @BeforeAll
    void setupClusterOperator() {
        setupDefaultClusterOperator(testNamespace);
    }

    @AfterAll
    void teardownClusterOperator() {
        teardownDefaultClusterOperator(testNamespace);
    }
    
    @Test
    void testBrokerPropertiesApplication() {
        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
            .editOrNewMetadata()
                .withName(CONFIG_BROKER_NAME)
                .withNamespace(testNamespace)
            .endMetadata()
            .build();
        // We don't wait for deployment since we wait for Status update instead in next call.
        broker = ResourceManager.createArtemis(testNamespace, broker, true);
        ResourceManager.waitForArtemisResourceStatusUpdate(broker, testNamespace, Constants.CONDITION_TYPE_DEPLOYED, Constants.CONDITION_REASON_ALL_PODS_READY, Constants.DURATION_5_MINUTES, false);
        LOGGER.info("[{}] ActiveMQArtemis Status CR: {}", testNamespace, broker.getMetadata().getName());
        broker = ResourceManager.getArtemisClient().inNamespace(testNamespace).resource(broker).get();
        LOGGER.debug("[{}] ActiveMQArtemis CR status update received:\n{}", testNamespace, broker.getStatus());
        assertThat("Conditions in CR Status were null", broker.getStatus().getConditions(), is(notNullValue()));
        assertThat("Conditions in CR Status were empty", broker.getStatus().getConditions().size(), greaterThan(0));
        for (Conditions condition : broker.getStatus().getConditions()) {
            // assert that all conditions have "True" status
            assertThat(String.format("Condition %s did not reach its expected status, reason: %s, message: %s", condition.getType(), condition.getReason(), condition.getMessage()),
                condition.getStatus().getValue(), is(equalTo(Constants.CONDITION_TRUE)));
        }
        ResourceManager.deleteArtemis(testNamespace, broker);
    }


    private void testBrokerProperty(ActiveMQArtemis broker, String brokerProperty, String conditionReason, String conditionType, String failureMessage) {
        ActiveMQArtemisSpec spec = new ActiveMQArtemisSpec();
        spec.setBrokerProperties(List.of(brokerProperty));

        LOGGER.info("[{}] Added broker property: {}", testNamespace, brokerProperty);
        broker.setSpec(spec);
        ResourceManager.getArtemisClient().inNamespace(testNamespace).resource(broker).createOrReplace();

        LOGGER.info("[{}] Waiting for broker to receive status update: {}", testNamespace, broker.getMetadata().getName());
        ResourceManager.waitForArtemisResourceStatusUpdate(broker, testNamespace, conditionType, conditionReason, Constants.DURATION_5_MINUTES);
        broker = ResourceManager.getArtemisClient().inNamespace(testNamespace).resource(broker).get();

        LOGGER.debug("[{}] ActiveMQArtemis CR status update received:\n{}", testNamespace, broker.getStatus());
        assertThat("Conditions in CR Status were null", broker.getStatus().getConditions(), is(notNullValue()));
        assertThat("Conditions in CR Status were empty", broker.getStatus().getConditions().size(), greaterThan(0));
        for (Conditions condition : broker.getStatus().getConditions()) {
            if (condition.getType().equals(conditionType)) {
                assertThat(failureMessage, condition.getReason(), is(equalTo(conditionReason)));
            }
        }
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    @Test
    void testBrokerPropertiesWrongProperty() {
        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, CONFIG_BROKER_NAME);
        testBrokerProperty(broker, "emptyProperty=emptyValue",
            Constants.CONDITION_REASON_APPLIED_WITH_ERROR,
            Constants.CONDITION_TYPE_BROKER_PROPERTIES_APPLIED,
            "Broker properties didn't error out as expected");
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    @Test
    void testBrokerPropertyInvalidValue() {
        ActiveMQArtemis broker =  ResourceManager.createArtemis(testNamespace, CONFIG_BROKER_NAME);
        testBrokerProperty(broker, "networkCheckPeriod=somestring",
            Constants.CONDITION_REASON_APPLIED_WITH_ERROR,
            Constants.CONDITION_TYPE_BROKER_PROPERTIES_APPLIED,
            "Broker properties didn't error out as expected");
        ResourceManager.deleteArtemis(testNamespace, broker);
    }
    
    @Test
    void testBrokerPropertiesValidProperty() {
        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, CONFIG_BROKER_NAME);
        testBrokerProperty(broker, "networkCheckPeriod=20000",
            Constants.CONDITION_REASON_APPLIED,
            Constants.CONDITION_TYPE_BROKER_PROPERTIES_APPLIED,
            "Broker property was not applied as expected");
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    @Test
    void testBrokerCRValidityTest() {
        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, CONFIG_BROKER_NAME);
        ResourceManager.waitForArtemisResourceStatusUpdate(broker, testNamespace,
            Constants.CONDITION_TYPE_VALID,
            Constants.CONDITION_REASON_VALIDATION,
            Constants.DURATION_5_MINUTES, false);
        broker = ResourceManager.getArtemisClient().inNamespace(testNamespace).resource(broker).get();
        LOGGER.info("Checking for the expected conditions on Broker CR status");
        assertThat("Default status fields didn't contain what was expected", broker.getStatus().getConditions(), containsInAnyOrder(
            hasProperty("type", is(Constants.CONDITION_TYPE_VALID)),
            hasProperty("type", is(Constants.CONDITION_TYPE_BROKER_PROPERTIES_APPLIED)),
            hasProperty("type", is(Constants.CONDITION_TYPE_READY)),
            hasProperty("type", is(Constants.CONDITION_TYPE_DEPLOYED))
        ));
        ResourceManager.deleteArtemis(testNamespace, broker);
    }
}
