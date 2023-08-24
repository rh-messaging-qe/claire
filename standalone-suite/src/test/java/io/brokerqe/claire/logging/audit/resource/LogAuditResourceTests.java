/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.logging.audit.resource;

import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.helper.webconsole.LoginPageHelper;
import io.brokerqe.claire.helper.webconsole.MainPageHelper;
import io.brokerqe.claire.helper.webconsole.WebConsoleHelper;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.container.ArtemisContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class LogAuditResourceTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(LogAuditResourceTests.class);

    private static final String LOG_PATTERN_FAILED_AUTH_304 = "AMQ601716: User .* failed authentication, reason: 304";

    private ArtemisContainer artemisInstance;

    @BeforeAll
    void setupEnv() {
        String artemisName = "artemis";
        LOGGER.info("Creating artemis instance: " + artemisName);
        String tuneFile = generateYacfgProfilesContainerTestDir("tune.yaml.jinja2");
        artemisInstance = getArtemisInstance(artemisName, tuneFile);
    }

    @Test
    void webConsoleLoginLogoutTest() {
        // TODO: add parameters to test different browsers
        // create a selenium remote web driver instance
        RemoteWebDriver driver = ResourceManager.getChromeRemoteDriver("chrome-browser");

        // load the console url (assertion is inside the method to be reused)
        WebConsoleHelper.load(driver, artemisInstance);

        // try to log in (assertion is inside the method to be reused)
        LoginPageHelper.login(driver, artemisInstance);

        // try to log out (assertion is inside the method to be reused)
        MainPageHelper.logout(driver, artemisInstance);

        // assert the log does not contain the pattern
        String logs = artemisInstance.getLogs();
        assertThat(logs).doesNotContainPattern(LOG_PATTERN_FAILED_AUTH_304);
    }

}
