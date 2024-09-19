/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.upgrade;

import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.client.deployment.ArtemisDeployment;
import io.brokerqe.claire.clients.Protocol;
import io.brokerqe.claire.clients.bundled.BundledClientOptions;
import io.brokerqe.claire.container.ArtemisContainer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.aggregator.ArgumentsAccessor;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

public class SingleUpgradeTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(SingleUpgradeTests.class);
    private static int messagesReceivedTotal = 0;
    private static int messagesSentInitials;
    private static int messagesReceivePartial;
    private static int upgradeCount;

    private ArtemisContainer artemisUpgraded;

    Stream<? extends Arguments> getUpgradePlanArguments() {
        ArrayList<HashMap<String, String>> mapped = new Yaml().load(getEnvironment().getTestUpgradePlanContent());
        messagesSentInitials = 2000 * (mapped.size() - 1);
        messagesReceivePartial = messagesSentInitials / (mapped.size() - 1);
        upgradeCount = mapped.size();
        return  mapped.stream().map(line ->
                Arguments.of(line.get("version"), line.get("artemis_version"), line.get("artemis_zip_url"))
        );
    }

    @BeforeAll
    void setupEnvironment() {
        LOGGER.info("[UpgradeTestPlan] {}", getEnvironment().getTestUpgradePlanContent());
    }

    ArtemisContainer createArtemis(String name, String artemisVersion, Map<String, String> artemisData) {
        String yacfgArtemisProfile = "claire-default-profile-" + artemisVersion + ".yaml.jinja2";
        LOGGER.info("[UPGRADE] Creating artemis instance: {} with profile {}", name, yacfgArtemisProfile);
        return getArtemisInstance(name, null, new ArrayList<>(List.of("profile=" + yacfgArtemisProfile)), artemisData);
    }

    ArtemisContainer performUpgradeProcedure(ArtemisContainer artemisUpgraded, String upgradeInstallDir) {
        // apache-artemis-2.33.0.redhat-00013/bin/artemis upgrade /tmp/upgrade-lala/
        String createCmd = upgradeInstallDir + "/bin/artemis upgrade " + artemisUpgraded.getInstanceDir();
        TestUtils.executeLocalCommand(createCmd);
        artemisUpgraded.withInstallDir(upgradeInstallDir, true);
        artemisUpgraded.setInstallDir(upgradeInstallDir);
        artemisUpgraded.restartWithStop(Duration.ofSeconds(10));
        return artemisUpgraded;
    }

    void assertVersionLogs(ArtemisContainer artemis, String version, String artemisVersion) {
        // Red Hat AMQ Broker 7.11.7.GA
        // AMQ101000: Starting ActiveMQ Artemis Server version 2.28.0.redhat-00022
        String brokerVersionOldString = ArtemisConstants.getArtemisVersionString(version);
        String brokerVersionNewString = ArtemisConstants.getArtemisVersionStringOld(version);

        LOGGER.info("[UPGRADE][{}] Checking for correct versions in logs {}, {}", artemis.getName(), version, artemisVersion);
        assertThat(artemis.getLogs(), anyOf(containsString(brokerVersionOldString), containsString(brokerVersionNewString)));
        assertThat(artemis.getLogs(), containsString(ArtemisConstants.getArtemisStartingServerVersionString(artemisVersion)));
    }

    @ParameterizedTest
    @MethodSource("getUpgradePlanArguments")
    void microUpgrade(ArgumentsAccessor argumentsAccessor) {
        String version = argumentsAccessor.getString(0);
        String artemisVersion = argumentsAccessor.getString(1);
        String artemisZipUrl = argumentsAccessor.getString(2);

        String artemisUpgradedName = "brk-static-upgrade";
        String artemisVersionedName = "brk-version-" + version.replaceAll("\\.", "-");
        String artemisUpgradedInstanceDir = getTestConfigDir() + Constants.FILE_SEPARATOR + artemisUpgradedName;
        String upgradeQueueName = "upgrade-queue";

        String installDir = ArtemisDeployment.downloadPrepareArtemisInstallDir(testInfo, artemisZipUrl, version, getTestConfigDir());

        Map<String, String> artemisUpgradedData = ArtemisDeployment.createArtemisInstanceFromInstallDir(installDir, artemisUpgradedName, artemisUpgradedInstanceDir);

        if (argumentsAccessor.getInvocationIndex() > 1) {
            LOGGER.info("[UPGRADE] Receive partial durable messages {}", messagesReceivePartial);
            artemisUpgraded = performUpgradeProcedure(artemisUpgraded, installDir);
            BundledClientOptions durableReceiverOptions = new BundledClientOptions()
                    .withDeployableClient(artemisUpgraded.getDeployableClient())
                    .withDestinationAddress(upgradeQueueName)
                    .withDestinationQueue(upgradeQueueName)
                    .withDestinationPort(DEFAULT_ALL_PORT)
                    .withMessageCount(messagesReceivePartial)
                    .withUsername(ArtemisConstants.ADMIN_NAME)
                    .withPassword(ArtemisConstants.ADMIN_PASS)
                    .withDestinationUrl(artemisUpgraded.getBrokerUri(Protocol.CORE))
                    .withProtocol(Protocol.CORE);
            messagesReceivedTotal += receiveMessages(durableReceiverOptions);
            LOGGER.info("[UPGRADE] Received so far {}", messagesReceivedTotal);

            if (argumentsAccessor.getInvocationIndex() == upgradeCount) {
                LOGGER.info("[UPGRADE] Check all received messages vs all sent");
                Assertions.assertEquals(messagesReceivedTotal, messagesSentInitials);
            }

        } else {
            LOGGER.info("[UPGRADE] Deploying initial broker {}", artemisUpgradedName);
            artemisUpgraded = createArtemis(artemisUpgradedName, artemisVersion, artemisUpgradedData);

            LOGGER.info("[UPGRADE] Sending initial messages {}", messagesSentInitials);
            BundledClientOptions initialSenderOptions = new BundledClientOptions()
                    .withDeployableClient(artemisUpgraded.getDeployableClient())
                    .withDestinationAddress(upgradeQueueName)
                    .withDestinationQueue(upgradeQueueName)
                    .withDestinationPort(DEFAULT_ALL_PORT)
                    .withMessageCount(messagesSentInitials)
                    .withUsername(ArtemisConstants.ADMIN_NAME)
                    .withPassword(ArtemisConstants.ADMIN_PASS)
                    .withDestinationUrl(artemisUpgraded.getBrokerUri(Protocol.CORE))
                    .withProtocol(Protocol.CORE);
            int sentInitial = sendMessages(initialSenderOptions);
            Assertions.assertEquals(sentInitial, messagesSentInitials);
        }

        LOGGER.info("[UPGRADE] Deploying versioned broker {}", artemisUpgradedName);
        ArtemisContainer artemisVersioned = createArtemis(artemisVersionedName, artemisVersion, artemisUpgradedData);

        assertVersionLogs(artemisUpgraded, version, artemisVersion);
        assertVersionLogs(artemisVersioned, version, artemisVersion);

        LOGGER.info("[UPGRADE] Test send-receive versioned broker {}", artemisVersionedName);
        int msgsExpected = 100;
        String addressName = "versioned-queue";
        BundledClientOptions senderOptions = new BundledClientOptions()
            .withDeployableClient(artemisVersioned.getDeployableClient())
            .withDestinationPort(DEFAULT_ALL_PORT)
            .withDestinationAddress(addressName)
            .withDestinationQueue(addressName)
            .withMessageCount(msgsExpected)
            .withUsername(ArtemisConstants.ADMIN_NAME)
            .withPassword(ArtemisConstants.ADMIN_PASS)
            .withDestinationUrl(artemisVersioned.getName())
            .withProtocol(Protocol.CORE);
        sendReceiveDurableMsgQueue(artemisVersioned, senderOptions, senderOptions);

        LOGGER.info("[UPGRADE] Stopping versioned broker {}", artemisVersionedName);
        artemisVersioned.stop();
    }

}
