/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.logging.general;

import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.client.deployment.ArtemisDeployment;
import io.brokerqe.claire.container.ArtemisContainer;
import io.brokerqe.claire.junit.TestValidSince;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class LogAccessTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(io.brokerqe.claire.logging.general.LogAccessTests.class);
    private ArtemisContainer artemis;
    private final String artemisBinDir = ArtemisConstants.INSTANCE_BIN_DIR;
    private final String username = ArtemisConstants.ADMIN_NAME;
    private final String password = ArtemisConstants.ADMIN_PASS;
    private final String oldContainerUsername = ArtemisConstants.ARTEMIS_STRING;
    private final String newContainerUsername = "Biggus";

    @BeforeAll
    void setupEnv() {
        String artemisName = "artemis";
        LOGGER.info("Creating artemis instance: " + artemisName);
        artemis = ArtemisDeployment.createArtemis(artemisName);
    }

    private String[] constructArtemisCommand(String artemisCommand, String withContainerUser) {
        String cmd = String.format("%s/artemis %s --user %s --password %s", artemisBinDir, artemisCommand, username, password);
        return new String[]{"sudo", "-E", "-H", "-u", withContainerUser, "/bin/bash", "-ic", cmd};
    }

    private void doTestArtemisCommands(String withContainerUser) {
        String[] commandTypes = {"producer", "queue stat"};
        String[] expectedLogs = {
            "Producer ActiveMQQueue[TEST], thread=0 Produced: 1000 messages",
            "NAME", "ADDRESS"
        };

        for (int i = 0; i < commandTypes.length; i++) {
            String commandType = commandTypes[i];

            LOGGER.info(String.format("Run artemis %s command with user %s", commandType, withContainerUser));
            String[] commandFull = constructArtemisCommand(commandType, withContainerUser);
            String logOutput = artemis.getExecutor().executeCommand(Constants.DURATION_3_MINUTES, commandFull).stdout;

            LOGGER.debug(commandType + " log: " + logOutput);
            LOGGER.info(String.format("Ensure artemis %s logs have no errors and contains valid information", commandType));

            assertThat(logOutput).doesNotContain(ArtemisConstants.LOG_ERROR);
            assertThat(logOutput).contains(expectedLogs[i]);
        }
    }

    // ENTMQBR-8907
    @Test
    @TestValidSince(ArtemisVersion.VERSION_2_37)
    void nonOwnerArtemisCommandTest() {
        LOGGER.info(String.format("Test artemis commands with owner user: %s", oldContainerUsername));
        doTestArtemisCommands(oldContainerUsername);

        LOGGER.info(String.format("Add new container user: %s", newContainerUsername));
        artemis.getExecutor().executeCommand(Constants.DURATION_2_SECONDS, "useradd", newContainerUsername);

        LOGGER.info(String.format("Ensure user %s was successfully created", newContainerUsername));
        String[] userInfoCmd = {"getent", "passwd", newContainerUsername};
        String userInfo = artemis.getExecutor().executeCommand(Constants.DURATION_2_SECONDS, userInfoCmd).stdout;
        assertThat(userInfo).contains(newContainerUsername);

        LOGGER.info(String.format("Test artemis commands with new user: %s", newContainerUsername));
        doTestArtemisCommands(newContainerUsername);
    }

}
