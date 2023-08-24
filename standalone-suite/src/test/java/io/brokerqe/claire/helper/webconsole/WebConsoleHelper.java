/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.helper.webconsole;

import io.brokerqe.claire.container.ArtemisContainer;
import org.openqa.selenium.remote.RemoteWebDriver;

import static org.assertj.core.api.Assertions.assertThat;

public class WebConsoleHelper extends PageHelper {

    public static void load(RemoteWebDriver driver, ArtemisContainer artemis) {
        String consoleUrl = artemis.getConsoleUrl();
        driver.get(consoleUrl);
        LoginPageHelper.waitLoginPageToBeVisible(driver);

        String expectedLoginUrl = artemis.getLoginUrl();
        String currentLoginUrl = driver.getCurrentUrl();
        assertThat(currentLoginUrl).isEqualTo(expectedLoginUrl);
    }

}
