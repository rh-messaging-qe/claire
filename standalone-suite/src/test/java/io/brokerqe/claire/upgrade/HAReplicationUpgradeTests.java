/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.upgrade;

import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.client.deployment.ArtemisDeployment;
import io.brokerqe.claire.clients.Protocol;
import io.brokerqe.claire.clients.bundled.ArtemisCommand;
import io.brokerqe.claire.clients.bundled.BundledArtemisClient;
import io.brokerqe.claire.clients.bundled.BundledClientOptions;
import io.brokerqe.claire.container.ArtemisContainer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.aggregator.ArgumentsAccessor;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(Constants.TAG_UPGRADE)
public class HAReplicationUpgradeTests extends UpgradeTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(HAReplicationUpgradeTests.class);
    private String primary = "primary";
    private String backup = "backup";
    private Map<String, ArrayList<ArtemisContainer>> artemises = new HashMap<>();

    String upgradeQueueName = "upgrade-queue";
    Map<String, String> artemisQueueStatOptions = new HashMap<>(Map.of(
            "maxColumnSize", "-1",
            "maxRows", "1000",
            "queueName", upgradeQueueName
    ));

    void initialDeployment(String version, String artemisVersion, String installDir, int haPairs) {
        artemises.put(primary, new ArrayList<>());
        artemises.put(backup, new ArrayList<>());

        for (int i = 0; i < haPairs; i++) {
            String artemisPrimaryName = primary + "-" + i;
            String artemisBackupName = backup + "-" + i;
            List<ArtemisContainer> tmpList = ArtemisDeployment.createArtemisHAPair(artemisPrimaryName, artemisBackupName, version, artemisVersion, installDir);
            artemises.get(primary).add(tmpList.get(0).isPrimary() ? tmpList.get(0) : tmpList.get(1));
            artemises.get(backup).add(tmpList.get(1).isBackup() ? tmpList.get(1) : tmpList.get(0));
        }
    }

    @ParameterizedTest
    @MethodSource("getUpgradePlanArguments")
    void microUpgradeHA(ArgumentsAccessor argumentsAccessor) {
        String version = argumentsAccessor.getString(0);
        String artemisVersion = argumentsAccessor.getString(1);
        String artemisZipUrl = argumentsAccessor.getString(2);

        int haPairs = 3;
        String installDir = ArtemisDeployment.downloadPrepareArtemisInstallDir(testInfo, artemisZipUrl, version, getTestConfigDir());

        if (argumentsAccessor.getInvocationIndex() <= 1) {
            LOGGER.info("[UPGRADE] Deploying initial broker pair(s)");
            initialDeployment(version, artemisVersion, installDir, haPairs);
            ArtemisContainer primary0 = artemises.get(primary).get(0);

            LOGGER.info("[UPGRADE] Sending initial messages {}", messagesSentInitials);
            BundledClientOptions initialSenderOptions = new BundledClientOptions()
                    .withDeployableClient(primary0.getDeployableClient())
                    .withDestinationAddress(upgradeQueueName)
                    .withDestinationQueue(upgradeQueueName)
                    .withDestinationPort(DEFAULT_ALL_PORT)
                    .withMessageCount(messagesSentInitials)
                    .withUsername(ArtemisConstants.ADMIN_NAME)
                    .withPassword(ArtemisConstants.ADMIN_PASS)
                    .withDestinationUrl(primary0.getBrokerUri(Protocol.CORE))
                    .withProtocol(Protocol.CORE)
                    .withTimeout(600);
            int sentInitial = sendMessages(initialSenderOptions);
            Assertions.assertEquals(sentInitial, messagesSentInitials);
        } else if (argumentsAccessor.getInvocationIndex() > 1) {
            // if is replication scenario -> upgrade backup, then primary
            for (int i = 0; i < haPairs; i++) {
                ArtemisContainer upgradeBackup = artemises.get(backup).get(i);
                ArtemisContainer upgradePrimary = artemises.get(primary).get(i);
                LOGGER.info("[UPGRADE] Going to upgrade pair #{}", i + 1);
                upgradeBackup = performUpgradeProcedure(upgradeBackup, installDir);
                assertVersionLogs(upgradeBackup, version, artemisVersion);

                upgradePrimary = performUpgradeProcedure(upgradePrimary, installDir);
                assertVersionLogs(upgradePrimary, version, artemisVersion);
            }
            ArtemisContainer randomPrimary = artemises.get(primary).get(argumentsAccessor.getInvocationIndex() % 3);

            LOGGER.info("[UPGRADE] Receive partial {} durable messages from {}", messagesReceivePartial, randomPrimary.getName());
            BundledClientOptions durableReceiverOptions = new BundledClientOptions()
                    .withDeployableClient(randomPrimary.getDeployableClient())
                    .withDestinationAddress(upgradeQueueName)
                    .withDestinationQueue(upgradeQueueName)
                    .withDestinationPort(DEFAULT_ALL_PORT)
                    .withMessageCount(messagesReceivePartial)
                    .withUsername(ArtemisConstants.ADMIN_NAME)
                    .withPassword(ArtemisConstants.ADMIN_PASS)
                    .withDestinationUrl(randomPrimary.getBrokerUri(Protocol.CORE))
                    .withProtocol(Protocol.CORE);
            messagesReceivedTotal += receiveMessages(durableReceiverOptions);
            LOGGER.info("[UPGRADE] Received so far {}", messagesReceivedTotal);

            if (argumentsAccessor.getInvocationIndex() == upgradeCount) {
                LOGGER.info("[UPGRADE] Check all received messages vs all sent");
                Assertions.assertEquals(messagesReceivedTotal, messagesSentInitials);
            }
        }

        LOGGER.info("[END-UPGRADE] Queue stats");
        BundledArtemisClient artemisClient = new BundledArtemisClient(artemises.get(primary).get(0).getDeployableClient(), ArtemisCommand.QUEUE_STAT, artemisQueueStatOptions);
//        BundledArtemisClient artemisBackupClient = new BundledArtemisClient(artemises.get(backup).get(0).getDeployableClient(), ArtemisCommand.QUEUE_STAT, artemisQueueStatOptions);
        artemisClient.executeCommand();
    }

}
