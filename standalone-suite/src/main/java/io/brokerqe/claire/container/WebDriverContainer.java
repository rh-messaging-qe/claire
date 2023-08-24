/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.container;

import io.brokerqe.claire.ResourceManager;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testcontainers.containers.BrowserWebDriverContainer;

public class WebDriverContainer extends AbstractGenericContainer {

    private final BrowserWebDriverContainer<?> browserWebDriver;
    private Capabilities capabilities;

    public WebDriverContainer(String name) {
        super(name, null);
        browserWebDriver = new BrowserWebDriverContainer<>();
        browserWebDriver.withNetwork(ResourceManager.getDefaultNetwork());
        container = browserWebDriver;
        this.name = name;
        this.type = ContainerType.WEBDRIVER;
    }

    public void setCapabilities(Capabilities capabilities) {
        this.capabilities = capabilities;
        browserWebDriver.withCapabilities(capabilities);
    }

    public RemoteWebDriver getDriver() {
        return new RemoteWebDriver(browserWebDriver.getSeleniumAddress(), capabilities);
    }
}
