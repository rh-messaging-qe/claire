/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.spp;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.junit.UsePlaywright;
import com.microsoft.playwright.options.AriaRole;
import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.Environment;
import io.brokerqe.claire.ResourceManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;

@UsePlaywright
public class BaseWebUITests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseWebUITests.class);
    static Playwright playwright;
    static Browser browser;
    static BrowserContext context;
    static Page page;
    static Locator.ClickOptions clicker;
    static Locator.FillOptions filler;

    String dashboardsUrl = getClient().getKubernetesClient().getMasterUrl().getHost().replace("api", "https://console-openshift-console.apps") + "/dashboards";
    String[] kubeCredentials = ResourceManager.getEnvironment().getKubeCredentials();

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions();
        if (ResourceManager.getEnvironment().isPlaywrightDebug()) {
            options = new BrowserType.LaunchOptions()
                    .setHeadless(false)
                    .setDownloadsPath(Paths.get(ResourceManager.getEnvironment().getTmpDirLocation()));
        }
        browser = playwright.chromium().launch(options);
        clicker = new Locator.ClickOptions().setTimeout(10000);
        filler = new Locator.FillOptions().setTimeout(5000);
    }

    @AfterAll
    static void closeBrowser() {
        playwright.close();
    }

    @BeforeEach
    void createContextAndPage() {
        String[] credentials = ResourceManager.getEnvironment().getKubeCredentials();
        Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                .setHttpCredentials(credentials[0], credentials[1])
                .setIgnoreHTTPSErrors(true);

        // TODO: enable video if needed
        if (Environment.get().isPlaywrightDebug()) {
            LOGGER.warn("[TEST] Storing web ui testing video into {}", Environment.get().getLogsDirLocation() + "/playwright-videos/");
            contextOptions.setRecordVideoDir(Paths.get(Environment.get().getLogsDirLocation() + "/playwright-videos/"));
        }
        context = browser.newContext(contextOptions);
        page = context.newPage();
        loginToOcp(dashboardsUrl, kubeCredentials[0], kubeCredentials[1]);
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    void loginToOcp(String dashboardsUrl, String username, String password) {
        LOGGER.info("Logging into {}", dashboardsUrl);
        page.navigate(dashboardsUrl);
        page.getByText("htpasswd").click();
        page.getByText("Username").fill(username);
        page.getByText("Password").fill(password);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Log in")).click();
        page.waitForLoadState();
    }

    void makeScreenshot(String testName) {
        page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get(testName + ".png")));
    }

    void downloadFile(Page page, String linkName) {
        LOGGER.info("Download certificate to /tmp folder");
        Download download = page.waitForDownload(() -> {
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(linkName)).click();
        });
        download.saveAs(Paths.get(Constants.TMP_DIR_SYSTEM, download.suggestedFilename()));
    }

}
