/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.upgrade;

import io.brokerqe.claire.Constants;
import io.brokerqe.claire.client.deployment.ArtemisConfigData;
import io.brokerqe.claire.client.deployment.ArtemisDeployment;
import io.brokerqe.claire.clients.bundled.ArtemisCommand;
import io.brokerqe.claire.clients.bundled.BundledArtemisClient;
import io.brokerqe.claire.container.ArtemisContainer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.aggregator.ArgumentsAccessor;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Tag(Constants.TAG_UPGRADE)
public abstract class HAUpgradeTests extends UpgradeTests {

    protected static final String PRIMARY = "primary";
    protected static final String BACKUP = "backup";
    protected Map<String, ArrayList<ArtemisContainer>> artemises = new HashMap<>();

    abstract Logger getLogger();
    abstract int getHaPairs();
    abstract void initialDeployment(ArtemisConfigData defaultArtemisConfigData, String artemisVersion, int haPairs);

    @ParameterizedTest
    @MethodSource("getUpgradePlanArguments")
    void microUpgradeHA(ArgumentsAccessor argumentsAccessor) {
        String version = argumentsAccessor.getString(0);
        String artemisVersion = argumentsAccessor.getString(1);
        String artemisZipUrl = argumentsAccessor.getString(2);

        int haPairs = getHaPairs();
        String installDir = ArtemisDeployment.downloadPrepareArtemisInstallDir(testInfo, artemisZipUrl, version, getTestConfigDir());
        String defaultVersionInstance = "artemis-default-instance-" + version;
        String defaultInstancePath = Paths.get(getTestConfigDir(), defaultVersionInstance).toString();
        ArtemisConfigData artemisConfigDataVersioned = ArtemisDeployment.createArtemisInstanceFromInstallDir(installDir, defaultVersionInstance, defaultInstancePath);

        if (argumentsAccessor.getInvocationIndex() <= 1) {
            getLogger().info("[UPGRADE] Deploying initial broker pair(s)");
            initialDeployment(artemisConfigDataVersioned, artemisVersion, haPairs);
            ArtemisContainer primary0 = artemises.get(PRIMARY).get(0);
            preUpgradeProcedure(primary0);

        } else if (argumentsAccessor.getInvocationIndex() > 1) {
            // if is replication scenario -> upgrade backup, then primary
            for (int i = 0; i < haPairs; i++) {
                ArtemisContainer upgradeBackup = artemises.get(BACKUP).get(i);
                ArtemisContainer upgradePrimary = artemises.get(PRIMARY).get(i);
                getLogger().info("[UPGRADE -> {}] Going to upgrade pair #{}", version, i + 1);
                upgradeBackup = performUpgradeProcedure(upgradeBackup, installDir, true);
                assertVersionLogs(upgradeBackup, version, artemisVersion);

                upgradePrimary = performUpgradeProcedure(upgradePrimary, installDir, true);
                assertVersionLogs(upgradePrimary, version, artemisVersion);

                upgradeBackup.ensureBrokerIsBackup();
                upgradePrimary.ensureBrokerIsActive();
            }

            int haCounter = argumentsAccessor.getInvocationIndex() % haPairs;
            ArtemisContainer randomPrimary = artemises.get(PRIMARY).get(haCounter);
            postUpgradeProcedure(randomPrimary);
            postUpgradeProcedureTest(artemises.get(PRIMARY).get(haCounter), artemises.get(BACKUP).get(haCounter));

            if (argumentsAccessor.getInvocationIndex() == upgradeCount) {
                getLogger().info("[UPGRADE] Check all received messages vs all sent");
                Assertions.assertEquals(messagesReceivedTotal, messagesSentInitials);
            }
        }

        getLogger().info("[END-UPGRADE] Queue stats");
        BundledArtemisClient artemisClient = new BundledArtemisClient(artemises.get(PRIMARY).get(0).getDeployableClient(), ArtemisCommand.QUEUE_STAT, artemisQueueStatOptions);
//        BundledArtemisClient artemisBackupClient = new BundledArtemisClient(artemises.get(backup).get(0).getDeployableClient(), ArtemisCommand.QUEUE_STAT, artemisQueueStatOptions);
        artemisClient.executeCommand();
    }

    void postUpgradeProcedureTest(ArtemisContainer artemisPrimary, ArtemisContainer artemisBackup) {
        produceAndConsumeOnPrimaryAndOnBackupTest(ArtemisContainer.ArtemisProcessControllerAction.STOP, artemisPrimary, artemisBackup);
    }

}
