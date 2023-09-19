/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire;

import io.brokerqe.claire.client.JmsClient;
import io.brokerqe.claire.client.container.SystemTestCppClientContainer;
import io.brokerqe.claire.client.container.SystemTestJavaClientsContainer;
import io.brokerqe.claire.client.container.SystemTestProtonDotnetClientContainer;
import io.brokerqe.claire.client.container.SystemTestProtonPythonClientContainer;
import io.brokerqe.claire.client.container.SystemTestRheaClientContainer;
import io.brokerqe.claire.container.AbstractGenericContainer;
import io.brokerqe.claire.container.ArtemisContainer;
import io.brokerqe.claire.container.NfsServerContainer;
import io.brokerqe.claire.container.ToxiProxyContainer;
import io.brokerqe.claire.container.WebDriverContainer;
import io.brokerqe.claire.container.YacfgArtemisContainer;
import io.brokerqe.claire.container.ZookeeperContainer;
import io.brokerqe.claire.exception.ClaireRuntimeException;
import io.brokerqe.claire.helper.ContainerHelper;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;

import jakarta.jms.ConnectionFactory;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({"checkstyle:ClassDataAbstractionCoupling", "checkstyle:ClassFanOutComplexity"})
public final class ResourceManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceManager.class);

    private static final Map<String, AbstractGenericContainer> CONTAINERS = new LinkedHashMap<>();
    private static final ExecutorService EXECUTOR_SERVICE = new ThreadPoolExecutor(2, 10,
            Constants.DURATION_10_SECONDS, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    private static final Map<String, JmsClient> CLIENTS = new LinkedHashMap<>();
    private static Network defaultNetwork;

    private ResourceManager() {
        super();
    }

    public static Map<String, AbstractGenericContainer> getContainers() {
        return CONTAINERS;
    }

    public static void stopAllContainers() {
        LOGGER.debug("Stopping all remaining containers");
        if (CONTAINERS.size() > 0) {
            ArrayList<AbstractGenericContainer> reverseOrderArray = new ArrayList<>(CONTAINERS.values());
            Collections.reverse(reverseOrderArray);
            ContainerHelper.stopContainers(reverseOrderArray.toArray(new AbstractGenericContainer[0]));
            CONTAINERS.clear();
        }
    }

    public static Network getDefaultNetwork() {
        if (defaultNetwork == null) {
            defaultNetwork = Network.newNetwork();
        }
        return defaultNetwork;
    }

    public static NfsServerContainer getNfsServerContainerInstance(String name) {
        return getContainerInstance(NfsServerContainer.class, name);
    }

    public static ToxiProxyContainer getToxiProxyContainerInstance(String name) {
        return getContainerInstance(ToxiProxyContainer.class, name);
    }

    public static ArtemisContainer getArtemisContainerInstance(String name) {
        return getContainerInstance(ArtemisContainer.class, name);
    }

    public static ZookeeperContainer getZookeeperContainerInstance(String name) {
        return getContainerInstance(ZookeeperContainer.class, name);
    }

    public static YacfgArtemisContainer getYacfgArtemisContainerInstance(String name) {
        return getContainerInstance(YacfgArtemisContainer.class, name);
    }

    public static SystemTestJavaClientsContainer getSystemTestJavaClientsContainerInstance(String name) {
        return getContainerInstance(SystemTestJavaClientsContainer.class, name);
    }

    public static SystemTestProtonDotnetClientContainer getSystemTestProtonDotnetClientContainerInstance(String name) {
        return getContainerInstance(SystemTestProtonDotnetClientContainer.class, name);
    }

    public static SystemTestCppClientContainer getSystemTestCppClientContainerInstance(String name) {
        return getContainerInstance(SystemTestCppClientContainer.class, name);
    }

    public static SystemTestProtonPythonClientContainer getSystemTestProtonPythonClientContainerInstance(String name) {
        return getContainerInstance(SystemTestProtonPythonClientContainer.class, name);
    }

    public static SystemTestRheaClientContainer getSystemTestRheaClientContainerInstance(String name) {
        return getContainerInstance(SystemTestRheaClientContainer.class, name);
    }

    public static RemoteWebDriver getChromeRemoteDriver(String name) {
        return getChromeRemoteDriver(name, false);
    }

    public static RemoteWebDriver getChromeRemoteDriver(String name, boolean secured) {
        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.addArguments("--no-sandbox", "--disable-dev-shm-usage", "--headless");
//        chromeOptions.setCapability(CapabilityType.ACCEPT_SSL_CERTS, true);
        chromeOptions.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);
        WebDriverContainer webDriverContainer = getWebDriverContainerInstance(name, chromeOptions);

        return getRemoteWebDriver(webDriverContainer);
    }

    public static RemoteWebDriver getFirefoxRemoteDriver(String name) {
        WebDriverContainer webDriverContainer = getWebDriverContainerInstance(name, new FirefoxOptions());
        return getRemoteWebDriver(webDriverContainer);
    }

    public static JmsClient getJmsClient(String id, ConnectionFactory connectionFactory) {
        JmsClient jmsClient = new JmsClient(id, connectionFactory);
        CLIENTS.put(id, jmsClient);
        return jmsClient;
    }

    public static void disconnectAllClients() {
        if (CLIENTS.size() > 0) {
            LOGGER.info("Stopping any remaining clients");
            CLIENTS.values().forEach(JmsClient::disconnect);
            CLIENTS.clear();
        }
    }

    public static ExecutorService getExecutorService() {
        return EXECUTOR_SERVICE;
    }

    private static RemoteWebDriver getRemoteWebDriver(WebDriverContainer webDriverContainer) {
        RemoteWebDriver driver = webDriverContainer.getDriver();
        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(Constants.DURATION_30_SECONDS));
        driver.manage().window().maximize();
        return driver;
    }

    private static WebDriverContainer getWebDriverContainerInstance(String name, Capabilities capabilities) {
        WebDriverContainer webDriverContainer = getContainerInstance(WebDriverContainer.class, name);
        webDriverContainer.setCapabilities(capabilities);
        webDriverContainer.start();
        return webDriverContainer;
    }

    private static <T extends AbstractGenericContainer> T getContainerInstance(Class<T> clazz, String name) {
        name = name + "-" + TestUtils.generateRandomName();
        LOGGER.trace("Adding container to ResourceManager map: {}", name);
        if (CONTAINERS.containsKey(name)) {
            throw new ClaireRuntimeException("Error: Container name already exists. Container name must be unique.");
        }
        try {
            T newContainer = clazz.getDeclaredConstructor(String.class).newInstance(name);
            CONTAINERS.put(name, newContainer);
            return newContainer;
        } catch (InstantiationException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new ClaireRuntimeException(e.getMessage(), e);
        }
    }

}
