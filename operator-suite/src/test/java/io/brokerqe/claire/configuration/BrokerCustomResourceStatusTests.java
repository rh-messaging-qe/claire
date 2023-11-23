/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.configuration;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisBuilder;
import io.amq.broker.v1beta1.ActiveMQArtemisSpec;
import io.amq.broker.v1beta1.activemqartemisstatus.Conditions;
import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.junit.TestValidSince;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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

@TestValidSince(ArtemisVersion.VERSION_2_28)
public class BrokerCustomResourceStatusTests extends AbstractSystemTests  {
    private static final Logger LOGGER = LoggerFactory.getLogger(BrokerConfigurationTests.class);

    private final String testNamespace = getRandomNamespaceName("crstatus-tests", 3);
    private static final String CONFIG_BROKER_NAME = "broker-status";

    @BeforeAll
    void setupClusterOperator() {
        setupDefaultClusterOperator(testNamespace);
    }

    @AfterAll
    void teardownClusterOperator() {
        teardownDefaultClusterOperator(testNamespace);
    }

    @AfterEach
    void init() {
        cleanResourcesAfterTest(testNamespace);
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
        broker = ResourceManager.createArtemis(testNamespace, broker, false);
        ResourceManager.waitForArtemisStatusUpdate(testNamespace, broker, ArtemisConstants.CONDITION_TYPE_DEPLOYED, ArtemisConstants.CONDITION_REASON_ALL_PODS_READY, Constants.DURATION_5_MINUTES, false);
        LOGGER.info("[{}] ActiveMQArtemis Status CR: {}", testNamespace, broker.getMetadata().getName());
        broker = ResourceManager.getArtemisClient().inNamespace(testNamespace).resource(broker).get();
        LOGGER.debug("[{}] ActiveMQArtemis CR status update received:\n{}", testNamespace, broker.getStatus());
        assertThat("Conditions in CR Status were null", broker.getStatus().getConditions(), is(notNullValue()));
        assertThat("Conditions in CR Status were empty", broker.getStatus().getConditions().size(), greaterThan(0));
        for (Conditions condition : broker.getStatus().getConditions()) {
            // assert that all conditions have "True" status
            assertThat(String.format("Condition %s did not reach its expected status, reason: %s, message: %s", condition.getType(), condition.getReason(), condition.getMessage()),
                condition.getStatus().getValue(), is(equalTo(ArtemisConstants.CONDITION_TRUE)));
        }
        ResourceManager.deleteArtemis(testNamespace, broker);
    }


    private void checkBrokerProperty(ActiveMQArtemis broker, String brokerProperty, String conditionReason, String conditionType, String failureMessage) {
        ActiveMQArtemisSpec spec = new ActiveMQArtemisSpec();
        spec.setBrokerProperties(List.of(brokerProperty));

        LOGGER.info("[{}] Added broker property: {}", testNamespace, brokerProperty);
        broker.setSpec(spec);
        ResourceManager.getArtemisClient().inNamespace(testNamespace).resource(broker).createOrReplace();
        // execution is too fast, give it some time for change to take effect on kubernetes
        TestUtils.threadSleep(Constants.DURATION_5_SECONDS);

        LOGGER.info("[{}] Waiting for broker to receive status update: {}", testNamespace, broker.getMetadata().getName());
        ResourceManager.waitForArtemisStatusUpdate(testNamespace, broker, conditionType, conditionReason, Constants.DURATION_5_MINUTES);
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
        checkBrokerProperty(broker, "emptyProperty=emptyValue",
            ArtemisConstants.CONDITION_REASON_APPLIED_WITH_ERROR,
            ArtemisConstants.CONDITION_TYPE_BROKER_PROPERTIES_APPLIED,
            "Broker properties didn't error out as expected");
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    @Test
    void testBrokerPropertyInvalidValue() {
        ActiveMQArtemis broker =  ResourceManager.createArtemis(testNamespace, CONFIG_BROKER_NAME);
        checkBrokerProperty(broker, "networkCheckPeriod=somestring",
            ArtemisConstants.CONDITION_REASON_APPLIED_WITH_ERROR,
            ArtemisConstants.CONDITION_TYPE_BROKER_PROPERTIES_APPLIED,
            "Broker properties didn't error out as expected");
        ResourceManager.deleteArtemis(testNamespace, broker);
    }
    
    @Test
    void testBrokerPropertiesValidProperty() {
        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, CONFIG_BROKER_NAME);
        checkBrokerProperty(broker, "networkCheckPeriod=20000",
            ArtemisConstants.CONDITION_REASON_APPLIED,
            ArtemisConstants.CONDITION_TYPE_BROKER_PROPERTIES_APPLIED,
            "Broker property was not applied as expected");
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    @Test
    void testBrokerCRValidityTest() {
        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, CONFIG_BROKER_NAME);
        ResourceManager.waitForArtemisStatusUpdate(testNamespace, broker,
            ArtemisConstants.CONDITION_TYPE_VALID,
            ArtemisConstants.CONDITION_REASON_VALIDATION,
            Constants.DURATION_5_MINUTES, false);
        broker = ResourceManager.getArtemisClient().inNamespace(testNamespace).resource(broker).get();
        LOGGER.info("Checking for the expected conditions on Broker CR status");
        assertThat("Default status fields didn't contain what was expected", broker.getStatus().getConditions(), containsInAnyOrder(
            hasProperty("type", is(ArtemisConstants.CONDITION_TYPE_VALID)),
            hasProperty("type", is(ArtemisConstants.CONDITION_TYPE_BROKER_PROPERTIES_APPLIED)),
            hasProperty("type", is(ArtemisConstants.CONDITION_TYPE_READY)),
            hasProperty("type", is(ArtemisConstants.CONDITION_TYPE_DEPLOYED))
        ));
        ResourceManager.deleteArtemis(testNamespace, broker);
    }
}
