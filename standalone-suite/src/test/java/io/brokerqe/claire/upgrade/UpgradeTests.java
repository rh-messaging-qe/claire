/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.upgrade;

import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.container.ArtemisContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.provider.Arguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Stream;

public abstract class UpgradeTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpgradeTests.class);

    protected static int messagesSentInitials;
    protected static int messagesReceivePartial;
    protected static int messagesReceivedTotal = 0;
    protected static int upgradeCount;

    private static final String VERSION = "version";
    private static final String ARTEMIS_VERSION = "artemisVersion";
    private static final String ARTEMIS_ZIP_URL = "artemisZipUrl";

    @BeforeAll
    void beforeAllUpgradeTests() {
        LOGGER.info("[UpgradeTestPlan] {}", getEnvironment().getTestUpgradePlanContent());
    }

    Stream<? extends Arguments> getUpgradePlanArguments() {
        ArrayList<HashMap<String, String>> mapped = new Yaml().load(getEnvironment().getTestUpgradePlanContent());
        messagesSentInitials = 2000 * (mapped.size() - 1);
        messagesReceivePartial = messagesSentInitials / (mapped.size() - 1);
        upgradeCount = mapped.size();
        return  mapped.stream().map(line ->
                Arguments.of(line.get(VERSION), line.get(ARTEMIS_VERSION), line.get(ARTEMIS_ZIP_URL))
        );
    }

    ArtemisContainer performUpgradeProcedure(ArtemisContainer artemisUpgraded, String upgradeInstallDir) {
        // apache-artemis-2.33.0.redhat-00013/bin/artemis upgrade /tmp/upgrade-lala/
        String createCmd = upgradeInstallDir + "/bin/artemis upgrade " + artemisUpgraded.getInstanceDir();
        TestUtils.executeLocalCommand(createCmd);
        artemisUpgraded.withInstallDir(upgradeInstallDir, true);
        artemisUpgraded.setInstallDir(upgradeInstallDir);
        artemisUpgraded.restartWithStop(Duration.ofSeconds(45));
        // TODO implement waitFor() some message in artemis
        artemisUpgraded.ensureBrokerStarted(false);
        return artemisUpgraded;
    }

}
