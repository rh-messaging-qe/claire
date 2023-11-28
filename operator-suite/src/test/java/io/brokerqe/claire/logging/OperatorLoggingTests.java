/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.logging;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisAddress;
import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.junit.TestValidSince;
import io.brokerqe.claire.smoke.SmokeTests;
import io.fabric8.kubernetes.api.model.Pod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@Tag(Constants.TAG_OPERATOR)
@TestValidSince(ArtemisVersion.VERSION_2_28)
public class OperatorLoggingTests extends AbstractSystemTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(SmokeTests.class);
    private final String testNamespace = getRandomNamespaceName("log-tests", 3);
    final static String DEBUG = "DEBUG";
    final static String INFO = "INFO";
    final static String ERROR = "ERROR";

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
    void testOperatorLogLevelDebug() {
        testOperatorLogLevel(DEBUG);
    }

    @Test
    void testOperatorLogLevelInfo() {
        testOperatorLogLevel(INFO);
    }

    @Test
    void testOperatorLogLevelError() {
        testOperatorLogLevel(ERROR);
    }

    void testOperatorLogLevel(String logLevel) {
        getClient().setOperatorLogLevel(operator, logLevel.toLowerCase(Locale.ROOT));
        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, "artemis-log");

        LOGGER.info("[{}] Deploying wrongly defined ActiveMQArtemisAddress", testNamespace);
        ActiveMQArtemisAddress wrongAddress = ResourceManager.createArtemisAddress(testNamespace, "lala", "lala", "wrongRoutingType");
        TestUtils.waitFor(logLevel + " message to show up in logs", Constants.DURATION_5_SECONDS, Constants.DURATION_2_MINUTES, () -> {
            Pod pod = getClient().getFirstPodByPrefixName(testNamespace, operator.getOperatorName());
            String log = getClient().getLogsFromPod(pod);
            return log.contains(logLevel);
        });
        Pod operatorPod = getClient().getFirstPodByPrefixName(testNamespace, operator.getOperatorName());
        String operatorLog = getClient().getLogsFromPod(operatorPod);
        logContainsLevel(operatorLog, logLevel);
        ResourceManager.deleteArtemis(testNamespace, broker);
        ResourceManager.deleteArtemisAddress(testNamespace, wrongAddress);
    }

    protected void logContainsLevel(String log, String level) {
        List<String> expectedLevels = new ArrayList<>();
        List<String> unexpectedLevels = new ArrayList<>();

        switch (level.toUpperCase(Locale.ROOT)) {
            case DEBUG -> expectedLevels.addAll(List.of(DEBUG, INFO, ERROR));
            case INFO -> {
                expectedLevels.addAll(List.of(INFO, ERROR));
                unexpectedLevels.add(DEBUG);
            }
            case ERROR -> {
                expectedLevels.add(ERROR);
                unexpectedLevels.addAll(List.of(DEBUG, INFO));
            }
        }

        for (String expectedLevel : expectedLevels) {
            assertThat(log, containsString(expectedLevel));
        }
        for (String unexpectedLevel : unexpectedLevels) {
            assertThat(log, not(containsString(unexpectedLevel)));
        }
    }

}
