/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.helper.webconsole;

import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.container.ArtemisContainer;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;

import static org.assertj.core.api.Assertions.assertThat;

public class LoginPageHelper extends PageHelper {

    public static final By USERNAME_LOCATOR = By.id("username");
    public static final By PASSWORD_LOCATOR = By.id("password");
    public static final By LOGIN_BUTTON_LOCATOR = By.xpath("//button[@type='submit']");

    public static void login(RemoteWebDriver driver, ArtemisContainer artemisInstance) {
        WebElement username = driver.findElement(USERNAME_LOCATOR);
        username.sendKeys(ArtemisConstants.ADMIN_NAME);
        WebElement password = driver.findElement(PASSWORD_LOCATOR);
        password.sendKeys(ArtemisConstants.ADMIN_PASS);
        driver.findElement(LOGIN_BUTTON_LOCATOR).click();
        MainPageHelper.waitMainPageToBeVisible(driver);

        String currentLoggedInUrl = driver.getCurrentUrl();
        assertThat(currentLoggedInUrl).isEqualTo(artemisInstance.getLoggedInUrl());
    }

    public static void waitLoginPageToBeVisible(RemoteWebDriver driver) {
        waitForElementToBeVisible(driver, LoginPageHelper.USERNAME_LOCATOR, Constants.DURATION_5_SECONDS);
        waitForElementToBeVisible(driver, LoginPageHelper.PASSWORD_LOCATOR, Constants.DURATION_5_SECONDS);
        waitForElementToBeVisible(driver, LoginPageHelper.LOGIN_BUTTON_LOCATOR, Constants.DURATION_5_SECONDS);
    }

}
