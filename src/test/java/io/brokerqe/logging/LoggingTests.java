/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.logging;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisAddress;
import io.brokerqe.AbstractSystemTests;
import io.brokerqe.ArtemisVersion;
import io.brokerqe.ResourceManager;
import io.brokerqe.TestUtils;
import io.brokerqe.junit.TestValidSince;
import io.brokerqe.smoke.SmokeTests;
import io.fabric8.kubernetes.api.model.Pod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@TestValidSince(ArtemisVersion.VERSION_2_28)
public class LoggingTests extends AbstractSystemTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(SmokeTests.class);
    private final String testNamespace = getRandomNamespaceName("log-tests", 6);
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

    @Test
    public void testOperatorLogLevelDebug() {
        testLogging(DEBUG.toLowerCase(Locale.ROOT));
    }
    @Test
    public void testOperatorLogLevelInfo() {
        testLogging(INFO.toLowerCase(Locale.ROOT));
    }

    @Test
    public void testOperatorLogLevelError() {
        testLogging(ERROR.toLowerCase(Locale.ROOT));
    }

    void testLogging(String logLevel) {
        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, "artemis-log");
        getClient().setOperatorLogLevel(operator, logLevel);

        LOGGER.info("[{}] Deploying wrongly defined ActiveMQArtemisAddress", testNamespace);
        ActiveMQArtemisAddress wrongAddress = ResourceManager.createArtemisAddress(testNamespace, "lala", "lala", "wrongRoutingType");
        LOGGER.info("[{}] Waiting for logs to show up");
        TestUtils.threadSleep(15000);
        Pod operatorPod = getClient().getFirstPodByPrefixName(testNamespace, operator.getOperatorName());
        String operatorLog = getClient().getLogsFromPod(testNamespace, operatorPod);
        logContainsLevel(operatorLog, logLevel);
        ResourceManager.deleteArtemis(testNamespace, broker);
        ResourceManager.deleteArtemisAddress(testNamespace, wrongAddress);
    }

    protected void logContainsLevel(String log, String level) {
        List<String> expectedLevels = new ArrayList<>();
        List<String> unexpectedLevels = new ArrayList<>();

        switch (level.toUpperCase(Locale.ROOT)) {
            case DEBUG -> expectedLevels.add(DEBUG);
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
