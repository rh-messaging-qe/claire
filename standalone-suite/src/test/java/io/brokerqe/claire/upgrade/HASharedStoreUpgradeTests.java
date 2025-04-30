/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.upgrade;

import io.brokerqe.claire.Constants;
import io.brokerqe.claire.client.deployment.ArtemisConfigData;
import io.brokerqe.claire.client.deployment.ArtemisDeployment;
import io.brokerqe.claire.container.ArtemisContainer;
import io.brokerqe.claire.helper.ToolDeployer;
import org.junit.jupiter.api.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Tag(Constants.TAG_UPGRADE)
public class HASharedStoreUpgradeTests extends HAUpgradeTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(HASharedStoreUpgradeTests.class);

    @Override
    Logger getLogger() {
        return LOGGER;
    }

    int getHaPairs() {
        return 1;
    }

    void initialDeployment(ArtemisConfigData defaultVersionACD, String artemisVersion, int haPairs) {
        artemises.put(PRIMARY, new ArrayList<>());
        artemises.put(BACKUP, new ArrayList<>());

        ArtemisConfigData artemisConfigData = ToolDeployer.setupNfsShares();
        artemisConfigData.withPrimaryTuneFile("primary-tune.yaml.jinja2")
                .withBackupTuneFile("backup-tune.yaml.jinja2")
                .withYacfgOptions(List.of("--opt", "journal_base_data_dir=" + artemisConfigData.getNfsMountDir()))
                .withArtemisVersionString(artemisVersion)
                .withStartTimeout(Duration.ofMinutes(2))
                .withIsSharedStore(true)
                .withInstanceBinDir(defaultVersionACD.getInstanceBinDir())
                .withInstallDir(defaultVersionACD.getInstallDir());

        for (int i = 0; i < haPairs; i++) {
            String artemisPrimaryName = PRIMARY + "-" + i;
            String artemisBackupName = BACKUP + "-" + i;

            List<ArtemisContainer> tmpList = ArtemisDeployment.createArtemisHAPair(artemisPrimaryName, artemisBackupName, artemisConfigData);
            artemises.get(PRIMARY).add(tmpList.get(0).isPrimary() ? tmpList.get(0) : tmpList.get(1));
            artemises.get(BACKUP).add(tmpList.get(1).isBackup() ? tmpList.get(1) : tmpList.get(0));
        }
    }

}
