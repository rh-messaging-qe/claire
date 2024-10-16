/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.webconsole;

import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.client.deployment.ArtemisDeployment;
import io.brokerqe.claire.helper.webconsole.LoginPageHelper;
import io.brokerqe.claire.helper.webconsole.MainPageHelper;
import io.brokerqe.claire.helper.webconsole.WebConsoleHelper;
import io.brokerqe.claire.container.ArtemisContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsoleTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleTests.class);

    private ArtemisContainer artemisInstance;

    @BeforeAll
    void setupEnv() {
        String artemisName = "artemis";
        LOGGER.info("Creating artemis instance: " + artemisName);
        artemisInstance = ArtemisDeployment.createArtemis(artemisName);
    }

    @Test
    @Tag(Constants.TAG_SMOKE)
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
    }

}
