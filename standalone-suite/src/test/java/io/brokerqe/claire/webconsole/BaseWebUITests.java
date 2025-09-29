/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.webconsole;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.TimeoutError;
import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.CommandResult;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.EnvironmentStandalone;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.container.ArtemisContainer;
import io.brokerqe.claire.junit.TestValidSince;
import org.junit.jupiter.api.AfterAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.Arrays;


@TestValidSince(ArtemisVersion.VERSION_2_39)
public class BaseWebUITests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseWebUITests.class);

    static Playwright playwright;
    static Browser browser;
    static BrowserContext context;
    static Page artemisPage;

    protected ArtemisContainer artemisContainer;

    @AfterAll
    static void closeBrowser() {
        context.close();
        browser.close();
        playwright.close();
    }

    public ArtemisContainer getArtemisContainer() {
        return artemisContainer;
    }

    public void setArtemisContainer(ArtemisContainer artemis) {
        artemisContainer = artemis;
    }

    static void launchBrowser() {
        playwright = Playwright.create();
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(true);
        if (ResourceManager.getEnvironment().isPlaywrightDebug()) {
            options = new BrowserType.LaunchOptions()
                    .setHeadless(false)
                    .setDownloadsPath(Paths.get(ResourceManager.getEnvironment().getTmpDirLocation()));
        }
        browser = playwright.chromium().launch(options);
        Locator.ClickOptions clicker = new Locator.ClickOptions().setTimeout(5000);
        Locator.FillOptions filler = new Locator.FillOptions().setTimeout(5000);
        WebconsoleCommon.setPlaywright(playwright);
        WebconsoleCommon.setClicker(clicker);
        WebconsoleCommon.setFiller(filler);
        createContextAndPage();
    }

    static void createContextAndPage() {
        Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                .setHttpCredentials(ArtemisConstants.ADMIN_NAME, ArtemisConstants.ADMIN_PASS)
                .setIgnoreHTTPSErrors(true);

        if (EnvironmentStandalone.get().isPlaywrightDebug()) {
            LOGGER.info("[TEST] Storing web ui testing video into {}", EnvironmentStandalone.get().getLogsDirLocation() + "/playwright-videos/");
            contextOptions.setRecordVideoDir(Paths.get(EnvironmentStandalone.get().getLogsDirLocation() + "/playwright-videos/"));
        }
        context = browser.newContext(contextOptions);
        context.grantPermissions(Arrays.asList("clipboard-read", "clipboard-write"));
        artemisPage = context.newPage();
    }

    void loginToArtemis(String loginUrl, String username, String password) {
        LOGGER.info("Logging into {}", loginUrl);
        artemisPage.navigate(loginUrl);
        artemisPage.getByText("Username", new Page.GetByTextOptions().setExact(true)).fill(username);
        artemisPage.getByText("Password").fill(password);
        artemisPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Log in")).click(WebconsoleCommon.getClicker());
        LOGGER.info("Logging into artemis broker");
        artemisPage.waitForLoadState();
        TestUtils.threadSleep(Constants.DURATION_2_SECONDS);
    }

    /**
     * This method is used only when using jolokia connector. By default we don't want to use it.
     */
    Page loginToArtemisConnector(ArtemisContainer artemisContainer, String loginUrl, int webPort, String username, String password) {
        LOGGER.info("Logging into {}", loginUrl);
        Page initialPage = context.newPage();
        initialPage.navigate(loginUrl);

        LOGGER.info("Create new remote connection");
        initialPage.getByText("Add connection").click(WebconsoleCommon.getClicker());

        initialPage.getByText("Name").fill(artemisContainer.getName());
        initialPage.getByText("HTTPS").click(WebconsoleCommon.getClicker());
        initialPage.getByText("Port").fill(String.valueOf(webPort));
        initialPage.getByText("Path").fill("/console/jolokia");
        initialPage.getByText("Test connection").click(WebconsoleCommon.getClicker());
        initialPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Add").setExact(true)).click(WebconsoleCommon.getClicker());

        // Get page after a specific action (e.g. clicking a link)
        artemisPage = context.waitForPage(() -> {
            initialPage.locator("[rowId='connection " + artemisContainer.getName() + "']").getByText("Connect").click(WebconsoleCommon.getClicker());
        });
        artemisPage.getByText("Username").fill(username);
        artemisPage.getByText("Password").fill(password);
        artemisPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Log in")).click(WebconsoleCommon.getClicker());
        LOGGER.info("Logging into artemis broker");
        artemisPage.waitForLoadState();
        TestUtils.threadSleep(Constants.DURATION_10_SECONDS);
        return artemisPage;
    }


    protected void executeJolokiaCommandLocally(ArtemisContainer artemis, String jolokiaUrlCommand, String parameters) {
        jolokiaUrlCommand = jolokiaUrlCommand.replace(
                String.valueOf(artemis.getGenericContainer().getMappedPort(ArtemisConstants.DEFAULT_WEB_CONSOLE_PORT)),
                String.valueOf(ArtemisConstants.DEFAULT_WEB_CONSOLE_PORT));
        String command = String.format("curl -H \"Origin:http://localhost:%d\" -u %s:%s '%s/%s'", ArtemisConstants.DEFAULT_WEB_CONSOLE_PORT,
                ArtemisConstants.ADMIN_NAME, ArtemisConstants.ADMIN_PASS, jolokiaUrlCommand, parameters);
        CommandResult cr = artemis.executeCommand("sh", "-lc", command);
        LOGGER.debug("ECODE={}\nSTD={}\nSTE={}", cr.exitCode, cr.stdout, cr.stderr);
    }

    protected String getJolokiaUrlCommand(Page artemisPage, String operationText) {
        artemisPage.getByRole(AriaRole.LISTITEM).filter(new Locator.FilterOptions().setHasText(operationText))
                .getByLabel("", new Locator.GetByLabelOptions().setExact(true)).click(WebconsoleCommon.getClicker());
        Locator listItem = artemisPage.locator("li:has-text('" + operationText + "')");
        listItem.locator("button.pf-v5-c-menu-toggle.pf-m-plain").click(WebconsoleCommon.getClicker());
        listItem.getByText("Copy Jolokia URL").click(WebconsoleCommon.getClicker());
        String jolokiaUrlCommand;
        try {
            jolokiaUrlCommand = (String) artemisPage.evaluate("() => navigator.clipboard.readText()");
        } catch (TimeoutError te) {
//        You can copy the URL manually: http://172.19.0.2:8161/console/jolokia/exec/org.apache.activemq.artemis:broker=!"artemis-mauricio!"/clearAuthorizationCache()
            String insecureCopy = "You can copy the URL manually: ";
            jolokiaUrlCommand = artemisPage.getByText(insecureCopy).allTextContents().get(0).substring(insecureCopy.length());
        }
        LOGGER.debug("Got Jolokia URL:\n{}", jolokiaUrlCommand);
        artemisPage.keyboard().press("Escape");
        // used only with connector
        jolokiaUrlCommand = workaroundJolokiaCmd(jolokiaUrlCommand);
        return jolokiaUrlCommand;
    }

    private String workaroundJolokiaCmd(String jolokia) {
        return jolokia.replaceFirst("proxy/http/.*/console/", "").replaceFirst("8080", String.valueOf(ArtemisConstants.DEFAULT_WEB_CONSOLE_PORT));
    }
}
