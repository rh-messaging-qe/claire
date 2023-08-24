/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.upgrade;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisAddress;
import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.junit.DisableOnNoUpgradePlan;
import io.brokerqe.claire.operator.ArtemisCloudClusterOperatorOlm;
import io.brokerqe.claire.operator.ArtemisFileProvider;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.aggregator.ArgumentsAccessor;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;

@DisableOnNoUpgradePlan
public class UpgradeOlmTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpgradeOlmTests.class);
    private final String testNamespace = getRandomNamespaceName("upgrade-tests", 3);

    private ArtemisCloudClusterOperatorOlm upgradeOlmOperator;
    private Pod operatorPod;
    private List<Pod> brokerUpgradePods;
    private ActiveMQArtemis brokerUpgrade;
    private StatefulSet brokerUpgradeStatefulSet;

    static Stream<? extends Arguments> getUpgradePlanArguments() {
        ArrayList<HashMap<String, String>> mapped = new Yaml().load(ResourceManager.getEnvironment().getTestUpgradePlanContent());
        return  mapped.stream().map(line ->
            Arguments.of(line.get("version"), line.get("channel"), line.get("indexImageBundle"))
        );
    }

    @BeforeAll
    void setupTestEnvironment() {
        LOGGER.info("[{}] [UpgradeTestPlan] {}", testNamespace, testEnvironmentOperator.getTestUpgradePlanContent());
        getClient().createNamespace(testNamespace, true);
    }

    @AfterAll
    void teardownClusterOperator() {
        // TODO: Teardown all deployed resources if needed!
//        teardownDefaultClusterOperator(testNamespace);
        getClient().deleteNamespace(testNamespace);
    }

    ArtemisCloudClusterOperatorOlm setupClusterOperator(String channel, String indexImageBundle) {
        if (upgradeOlmOperator == null) {
            upgradeOlmOperator = ResourceManager.deployArtemisClusterOperatorOlm(testNamespace, List.of(testNamespace), channel, indexImageBundle);
        } else {
            upgradeOlmOperator = upgradeOlmOperator.upgradeClusterOperator(channel, indexImageBundle);
        }
        return upgradeOlmOperator;
    }

    @ParameterizedTest
    @MethodSource("getUpgradePlanArguments")
    void microIncrementalTest(ArgumentsAccessor argumentsAccessor) {
        String version = argumentsAccessor.getString(0);
        String channel = argumentsAccessor.getString(1);
        String iib = argumentsAccessor.getString(2);
        String brokerName = "artemis-" + version.replaceAll("\\.", "-");
        String brokerUpgradableName = "artemis-upgrade";
        int brokerUpgradableCount = 3;
        long brokerReloadDuration = Constants.DURATION_3_MINUTES + Constants.DURATION_1_MINUTE * brokerUpgradableCount;

        boolean newClusterOperator = upgradeOlmOperator == null;

        LOGGER.info("[{}] [Deploying] {} {} {}", testNamespace, version, channel, iib);
        upgradeOlmOperator = setupClusterOperator(channel, iib);

        if (newClusterOperator) {
            brokerUpgrade = ResourceManager.createArtemis(testNamespace, brokerUpgradableName, brokerUpgradableCount, true, true);
            brokerUpgradeStatefulSet = getClient().getStatefulSet(testNamespace, brokerUpgradableName);
            brokerUpgradePods = getClient().listPodsByPrefixName(testNamespace, brokerUpgradableName);
            operatorPod = getClient().getFirstPodByPrefixName(testNamespace, upgradeOlmOperator.getOperatorName());

            ActiveMQArtemisAddress myAddress = ResourceManager.createArtemisAddress(testNamespace, ArtemisFileProvider.getAddressQueueExampleFile());
        } else {
            LOGGER.info("[{}] Reloading ClusterOperator and Broker {} ", testNamespace, brokerUpgradableName);
            operatorPod = getClient().waitForPodReload(testNamespace, operatorPod, upgradeOlmOperator.getOperatorName(), Constants.DURATION_5_MINUTES);
            ResourceManager.waitForBrokerDeployment(testNamespace, brokerUpgrade, true, brokerReloadDuration, brokerUpgradeStatefulSet);
            brokerUpgradeStatefulSet = getClient().getStatefulSet(testNamespace, brokerUpgradableName);

            for (Pod brokerUpgradePod : brokerUpgradePods) {
                getClient().waitForPodReload(testNamespace, brokerUpgradePod, brokerUpgradePod.getMetadata().getName(), Constants.DURATION_5_MINUTES);
            }
        }

        // There is a bug in older version of ArtemisOperator, where it spawned new Operator which failed to synchronize resources.
        // After ~12 seconds it restarts itself. Whole upgrade procedure works fine.
        operatorPod = getClient().getFirstPodByPrefixName(testNamespace, upgradeOlmOperator.getOperatorName());
        brokerUpgradePods = getClient().listPodsByPrefixName(testNamespace, brokerUpgradableName);

        LOGGER.info("[{}] Check expected version {} in {} logs", testNamespace, version, operatorPod.getMetadata().getName());
        // Version of the operator: 7.10.1
        String operatorVersionString = "Version of the operator: " + version;
        String operatorLogs = getClient().getLogsFromPod(operatorPod);
        assertThat(operatorLogs, containsString(operatorVersionString));

        // Red Hat AMQ 7.10.1.GA
        String brokerVersionOldString = "Red Hat AMQ " + version + ".GA";
        String brokerVersionNewString = "Red Hat AMQ Broker " + version + ".GA";
        for (Pod brokerUpgradePod : brokerUpgradePods) {
            LOGGER.info("[{}] Check expected version {} in {} pod logs", testNamespace, version, brokerUpgradePod.getMetadata().getName());
            String brokerLogs = getClient().getLogsFromPod(brokerUpgradePod);
            assertThat(brokerLogs, anyOf(containsString(brokerVersionOldString), containsString(brokerVersionNewString)));
        }

        LOGGER.info("[{}] Deploy new broker and check expected version {} in logs", testNamespace, version);
        ActiveMQArtemis brokerVersion = ResourceManager.createArtemis(testNamespace, brokerName);
        Pod brokerVersionPod = getClient().getFirstPodByPrefixName(testNamespace, brokerName);
        String brokerVersionLogs = getClient().getLogsFromPod(brokerVersionPod);
        assertThat(brokerVersionLogs, anyOf(containsString(brokerVersionOldString), containsString(brokerVersionNewString)));
        ResourceManager.deleteArtemis(testNamespace, brokerVersion);
    }

}
