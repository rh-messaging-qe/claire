/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.configuration;

import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.junit.TestValidSince;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClientTimeoutException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag(Constants.TAG_OPERATOR)
@TestValidSince(ArtemisVersion.VERSION_2_33)
@Timeout(value = 5, unit = TimeUnit.MINUTES)
public class OperatorConfigurationTests extends AbstractSystemTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(OperatorConfigurationTests.class);
    private final String testNamespace = getRandomNamespaceName("opr-cfg-tests", 3);

    private static final int DEFAULT_LEASE_DURATION = 15;
    private static final int DEFAULT_RENEW_DEADLINE = 10;
    private static final int DEFAULT_RETRY_PERIOD = 2;

    @BeforeEach
    void setupClusterOperator() {
        setupDefaultClusterOperator(testNamespace);
    }

    @AfterEach
    void teardownClusterOperator() {
        teardownDefaultClusterOperator(testNamespace);
    }

    @Test
    void testOperatorValidLeaseDuration() {
        Pod operatorPod = getClient().getFirstPodByPrefixName(testNamespace, operator.getOperatorName());
        String operatorLog = getClient().getLogsFromPod(operatorPod);
        assertThat(operatorLog).contains("\"LeaseDuration\": \"" + DEFAULT_LEASE_DURATION + "s\"");

        int newLeaseDuration = 20;
        operator.setOperatorLeaseDuration(newLeaseDuration, true);
        operatorPod = getClient().getFirstPodByPrefixName(testNamespace, operator.getOperatorName());
        operatorLog = getClient().getLogsFromPod(operatorPod);
        assertThat(operatorLog).contains("\"LeaseDuration\": \"" + newLeaseDuration + "s\"");
        operator.setOperatorLeaseDuration(DEFAULT_LEASE_DURATION, true);
    }

    @Test
    void testOperatorInvalidLeaseDuration() {
        Pod operatorPod = getClient().getFirstPodByPrefixName(testNamespace, operator.getOperatorName());
        String operatorLog = getClient().getLogsFromPod(operatorPod);
        assertThat(operatorLog).contains("\"LeaseDuration\": \"" + DEFAULT_LEASE_DURATION + "s\"");
        assertThat(operatorLog).contains("\"RenewDeadline\": \"" + DEFAULT_RENEW_DEADLINE + "s\"");

        int newLeaseDuration = DEFAULT_RENEW_DEADLINE; // Lease duration must be higher than renew-deadline
        Assertions.assertThatExceptionOfType(KubernetesClientTimeoutException.class).isThrownBy(() -> {
            operator.setOperatorLeaseDuration(newLeaseDuration, false);
            operator.isOperatorReady(Constants.DURATION_10_SECONDS);
        });
        operator.setOperatorLeaseDuration(DEFAULT_LEASE_DURATION, true);
    }

    @Test
    void testOperatorValidRetryPeriod() {
        Pod operatorPod = getClient().getFirstPodByPrefixName(testNamespace, operator.getOperatorName());
        String operatorLog = getClient().getLogsFromPod(operatorPod);
        assertThat(operatorLog).contains("\"RetryPeriod\": \"" + DEFAULT_RETRY_PERIOD + "s\"");

        int newRetryPeriod = 5;
        operator.setOperatorRetryPeriodDuration(newRetryPeriod, true);
        operatorPod = getClient().getFirstPodByPrefixName(testNamespace, operator.getOperatorName());
        operatorLog = getClient().getLogsFromPod(operatorPod);
        assertThat(operatorLog).contains("\"RetryPeriod\": \"" + newRetryPeriod + "s\"");
        operator.setOperatorRetryPeriodDuration(DEFAULT_RETRY_PERIOD, true);
    }

    @Test
    void testOperatorInvalidRetryPeriod() {
        Pod operatorPod = getClient().getFirstPodByPrefixName(testNamespace, operator.getOperatorName());
        String operatorLog = getClient().getLogsFromPod(operatorPod);
        assertThat(operatorLog).contains("\"RetryPeriod\": \"" + DEFAULT_RETRY_PERIOD + "s\"");

        int newRetryPeriod = 0;
        Assertions.assertThatExceptionOfType(KubernetesClientTimeoutException.class).isThrownBy(() -> {
            operator.setOperatorRetryPeriodDuration(newRetryPeriod, false);
            operator.isOperatorReady(Constants.DURATION_10_SECONDS);
        });
        operator.setOperatorRetryPeriodDuration(DEFAULT_RETRY_PERIOD, true);
    }

    @Test
    void testOperatorValidRenewDeadline() {
        Pod operatorPod = getClient().getFirstPodByPrefixName(testNamespace, operator.getOperatorName());
        String operatorLog = getClient().getLogsFromPod(operatorPod);
        assertThat(operatorLog).contains("\"RenewDeadline\": \"" + DEFAULT_RENEW_DEADLINE + "s\"");

        int newRenewDeadline = 3;
        operator.setOperatorRenewDeadlineDuration(newRenewDeadline, true);
        operatorPod = getClient().getFirstPodByPrefixName(testNamespace, operator.getOperatorName());
        operatorLog = getClient().getLogsFromPod(operatorPod);
        assertThat(operatorLog).contains("\"RenewDeadline\": \"" + newRenewDeadline + "s\"");
        operator.setOperatorRenewDeadlineDuration(DEFAULT_RENEW_DEADLINE, true);
    }

    @Test
    void testOperatorInvalidRenewDeadline() {
        Pod operatorPod = getClient().getFirstPodByPrefixName(testNamespace, operator.getOperatorName());
        String operatorLog = getClient().getLogsFromPod(operatorPod);
        assertThat(operatorLog).contains("\"RenewDeadline\": \"" + DEFAULT_RENEW_DEADLINE + "s\"");
        assertThat(operatorLog).contains("\"RetryPeriod\": \"" + DEFAULT_RETRY_PERIOD + "s\"");

        int newRenewDeadline = DEFAULT_RETRY_PERIOD; // needs to be higher than retry-period
        // "Wait 30 seconds and check logs & status of resource");
        Assertions.assertThatExceptionOfType(KubernetesClientTimeoutException.class).isThrownBy(() -> {
            operator.setOperatorRenewDeadlineDuration(newRenewDeadline, false);
            operator.isOperatorReady(Constants.DURATION_10_SECONDS);
        });

        operator.setOperatorRenewDeadlineDuration(DEFAULT_RENEW_DEADLINE, true);
    }
}
