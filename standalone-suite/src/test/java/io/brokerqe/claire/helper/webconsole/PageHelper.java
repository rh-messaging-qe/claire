/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.helper.webconsole;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class PageHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(PageHelper.class);

    public static void waitForElementToBeVisible(WebDriver driver, By elementLocator, long timeout) {
        LOGGER.debug("Waiting for {} to be visible", elementLocator.toString());
        WebDriverWait loadWebDriverWait = new WebDriverWait(
                driver, Duration.ofMillis(timeout));
        loadWebDriverWait.until(ExpectedConditions.visibilityOfElementLocated(elementLocator));
    }

}
