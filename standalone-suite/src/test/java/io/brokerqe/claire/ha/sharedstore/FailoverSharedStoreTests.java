/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.ha.sharedstore;

import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.client.deployment.ArtemisConfigData;
import io.brokerqe.claire.client.deployment.ArtemisDeployment;
import io.brokerqe.claire.container.ArtemisContainer;
import io.brokerqe.claire.helper.ToolDeployer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

public class FailoverSharedStoreTests extends AbstractSystemTests {

    private ArtemisContainer artemisPrimary;
    private ArtemisContainer artemisBackup;

    @BeforeAll
    void setupEnv() {
        String artemisPrimaryName = "artemisPrimary";
        String artemisBackupName = "artemisBackup";
        ArtemisConfigData artemisConfigData = ToolDeployer.setupNfsShares();

        artemisConfigData.withPrimaryTuneFile("primary-tune.yaml.jinja2")
                .withBackupTuneFile("backup-tune.yaml.jinja2")
                .withIsSharedStore(true)
                .withYacfgOptions(List.of("--opt", "journal_base_data_dir=" + artemisConfigData.getNfsMountDir()));
        List<ArtemisContainer> tmpList = ArtemisDeployment.createArtemisHAPair(artemisPrimaryName, artemisBackupName, artemisConfigData);
        artemisPrimary = tmpList.get(0);
        artemisBackup = tmpList.get(1);
    }

    @ParameterizedTest(name = "{index} => stopAction=''{0}''")
    @EnumSource(value = ArtemisContainer.ArtemisProcessControllerAction.class, names = {"STOP", "FORCE_STOP"})
    void testProduceAndConsumeOnPrimaryAndOnBackupTest(ArtemisContainer.ArtemisProcessControllerAction stopAction) {
        produceAndConsumeOnPrimaryAndOnBackupTest(stopAction, artemisPrimary, artemisBackup);
    }
}
