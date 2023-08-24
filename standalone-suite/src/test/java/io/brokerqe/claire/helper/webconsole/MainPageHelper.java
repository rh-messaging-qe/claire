/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.helper.webconsole;

import io.brokerqe.claire.Constants;
import io.brokerqe.claire.container.ArtemisContainer;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

import static org.assertj.core.api.Assertions.assertThat;

public class MainPageHelper extends PageHelper {

    public static final By USER_DROP_DOWN_MENU_LOCATOR = By.id("userDropdownMenu");
    public static final By LOGOUT_LOCATOR = By.cssSelector("a[ng-focus='authService.logout()']");

    public static void logout(RemoteWebDriver driver, ArtemisContainer artemisInstance) {
        WebElement logoutWebElement = driver.findElement(LOGOUT_LOCATOR);
        WebElement userDropdownMenuWebElement = driver.findElement(USER_DROP_DOWN_MENU_LOCATOR);

        if (!logoutWebElement.isDisplayed()) {
            userDropdownMenuWebElement.click();
        }
        logoutWebElement.click();
        LoginPageHelper.waitLoginPageToBeVisible(driver);

        String currentAfterLogoutUrl = driver.getCurrentUrl();
        assertThat(currentAfterLogoutUrl).isEqualTo(artemisInstance.getLoginUrl());
    }

    public static void waitMainPageToBeVisible(RemoteWebDriver driver) {
        waitForElementToBeVisible(driver, MainPageHelper.USER_DROP_DOWN_MENU_LOCATOR, Constants.DURATION_5_SECONDS);
        // TODO: really needed?
        waitForElementToBeVisible(driver, By.xpath("//a[contains(text(),'Status')]"), Constants.DURATION_5_SECONDS);
    }

}
