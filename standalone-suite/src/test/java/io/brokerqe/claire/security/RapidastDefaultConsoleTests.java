/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.security;

import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.client.deployment.ArtemisDeployment;
import io.brokerqe.claire.container.ArtemisContainer;
import io.brokerqe.claire.container.RapidastContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;


@Tag(Constants.TAG_RAPIDAST)
public class RapidastDefaultConsoleTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(RapidastDefaultConsoleTests.class);

    protected String consoleURL;
    protected String artemisFullName;


    protected String getScanName() {
        return "default-spider";
    }

    @BeforeAll
    void setupEnv() {
        String artemisName = "artemis";
        LOGGER.info("Creating artemis instance: " + artemisName);
        ArtemisContainer artemis = ArtemisDeployment.getArtemisInstance(artemisName);
        consoleURL = artemis.getConsoleUrl();
        artemisFullName = artemis.getName();
    }

    @AfterAll
    public void tearDownEnv() {
        ResourceManager.stopAllContainers();
    }

    @Test
    @Tag(Constants.TAG_RAPIDAST)
    void rapidastConsoleTest() {
        LOGGER.info("[RAPIDAST] {}, Spider method: {}", consoleURL, getScanName());

        String rapidastName = "rapidast-" + artemisFullName;
        LOGGER.info("Creating rapidast container: " + rapidastName);
        RapidastContainer rapidast = new RapidastContainer(rapidastName, consoleURL, getScanName(), 1000);

        LOGGER.info("Starting rapidast container: " + rapidastName);
        rapidast.start();

        LOGGER.info("Ensuring results from scanner are in results directory");
        boolean resultsDirExists = TestUtils.directoryExists(RapidastContainer.RESULTS_DIR + "/" + getScanName());
        assertThat(resultsDirExists).isTrue();
    }

}
