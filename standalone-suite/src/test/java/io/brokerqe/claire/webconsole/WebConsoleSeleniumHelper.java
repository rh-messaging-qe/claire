/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.webconsole;

import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.container.ArtemisContainer;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Do not use this class or Selenium for new Test for Webconsole.
 * Use Playwright instead! See Hawtio4Tests class.
 */
@Deprecated
public class WebConsoleSeleniumHelper extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebConsoleSeleniumHelper.class);

    private static By usernameLocator;
    private static By passwordLocator;
    private static By loginButtonLocator;
    public static By logoutLocator;
    public static By logoutButton = By.xpath("//span[text()='Log out']");
    private static By userDropdownMenu;


    public static void load(RemoteWebDriver driver, ArtemisContainer artemis) {
        if (usernameLocator == null && passwordLocator == null) {
            setupLocators(artemis);
        }

        String consoleUrl = artemis.getConsoleUrl();
        driver.get(consoleUrl);
        waitLoginPageToBeVisible(driver);

        String expectedLoginUrl = artemis.getLoginUrl();
        String currentLoginUrl = driver.getCurrentUrl();
        assertThat(currentLoginUrl).isEqualTo(expectedLoginUrl);
    }

    private static void setupLocators(ArtemisContainer artemisContainer) {
        if (artemisContainer.getArtemisConfigData().getArtemisTestVersion().getVersionNumber() >= ArtemisVersion.VERSION_2_40.getVersionNumber()) {
            // 7.13>=
            usernameLocator = By.id("pf-login-username-id");
            passwordLocator = By.id("pf-login-password-id");
            loginButtonLocator = By.xpath("//button[text()='Log in']");
            userDropdownMenu = By.cssSelector("span.pf-v5-c-menu-toggle__toggle-icon svg");
            logoutLocator = By.xpath("//span[text()='Log out']");
        } else {
            // <7.13
            usernameLocator = By.id("username");
            passwordLocator = By.id("password");
            loginButtonLocator = By.xpath("//button[@type='submit']");
            userDropdownMenu = By.id("userDropdownMenu");
            logoutLocator = By.cssSelector("a[ng-focus='authService.logout()']");
        }
    }


    public static void logout(RemoteWebDriver driver, ArtemisContainer artemisInstance) {
        driver.navigate().refresh(); // closes dropdown menu if was opened - default state
        WebElement userDropdownMenuWebElement = driver.findElement(userDropdownMenu);
        userDropdownMenuWebElement.click();

        WebElement logoutWebElement = driver.findElement(WebConsoleSeleniumHelper.logoutLocator);
        logoutWebElement.click();
        WebConsoleSeleniumHelper.waitLoginPageToBeVisible(driver);

        String currentAfterLogoutUrl = driver.getCurrentUrl();
        assertThat(currentAfterLogoutUrl).isEqualTo(artemisInstance.getLoginUrl());
    }

    public static void waitMainPageToBeVisible(RemoteWebDriver driver) {
        waitForElementToBeVisible(driver, userDropdownMenu, Constants.DURATION_5_SECONDS);
        // TODO: really needed?
//        waitForElementToBeVisible(driver, By.xpath("//a[contains(text(),'Status')]"), Constants.DURATION_5_SECONDS);
    }

    public static void waitForElementToBeVisible(WebDriver driver, By elementLocator, long timeout) {
        LOGGER.debug("Waiting for {} to be visible", elementLocator.toString());
        WebDriverWait loadWebDriverWait = new WebDriverWait(
                driver, Duration.ofMillis(timeout));
        loadWebDriverWait.until(ExpectedConditions.visibilityOfElementLocated(elementLocator));
    }


    public static void login(RemoteWebDriver driver, ArtemisContainer artemisInstance) {
        WebElement username = driver.findElement(usernameLocator);
        username.sendKeys(ArtemisConstants.ADMIN_NAME);
        WebElement password = driver.findElement(passwordLocator);
        password.sendKeys(ArtemisConstants.ADMIN_PASS);
        driver.findElement(loginButtonLocator).click();
        WebConsoleSeleniumHelper.waitMainPageToBeVisible(driver);

        String currentLoggedInUrl = driver.getCurrentUrl();
        assertThat(currentLoggedInUrl).isEqualTo(artemisInstance.getLoggedInUrl());
    }

    public static void waitLoginPageToBeVisible(RemoteWebDriver driver) {
        waitForElementToBeVisible(driver, usernameLocator, Constants.DURATION_5_SECONDS);
        waitForElementToBeVisible(driver, passwordLocator, Constants.DURATION_5_SECONDS);
        waitForElementToBeVisible(driver, loginButtonLocator, Constants.DURATION_5_SECONDS);
    }

}
