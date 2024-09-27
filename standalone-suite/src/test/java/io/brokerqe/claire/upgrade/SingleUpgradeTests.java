/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.upgrade;

import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.client.deployment.ArtemisDeployment;
import io.brokerqe.claire.clients.Protocol;
import io.brokerqe.claire.clients.bundled.BundledClientOptions;
import io.brokerqe.claire.container.ArtemisContainer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.aggregator.ArgumentsAccessor;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Tag(Constants.TAG_UPGRADE)
public class SingleUpgradeTests extends UpgradeTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(SingleUpgradeTests.class);

    private ArtemisContainer artemisUpgraded;

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
            artemisUpgraded = ArtemisDeployment.createArtemis(artemisUpgradedName, artemisVersion, artemisUpgradedData);

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
        ArtemisContainer artemisVersioned = ArtemisDeployment.createArtemis(artemisVersionedName, artemisVersion, artemisUpgradedData);

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
