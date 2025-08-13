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
import java.util.Arrays;

@UsePlaywright
public class BaseWebUITests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseWebUITests.class);
    static Playwright playwright;
    static Browser browser;
    static BrowserContext context;
    static Page page;
    static Locator.ClickOptions clicker;
    static Locator.FillOptions filler;

    String loginUrl = getClient().getKubernetesClient().getMasterUrl().getHost().replace("api", "https://console-openshift-console.apps") + "/auth/login";
    String dashboardsUrl = getClient().getKubernetesClient().getMasterUrl().getHost().replace("api", "https://console-openshift-console.apps") + "/dashboards";
    String[] kubeCredentials = ResourceManager.getEnvironment().getKubeCredentials();
    String customUserAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.3";
    String firefoxUserAgent = "Mozilla/5.0 (X11; Linux x86_64; rv:141.0) Gecko/20100101 Firefox/141.0";

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(false);
        if (ResourceManager.getEnvironment().isPlaywrightDebug()) {
            options = new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setArgs(Arrays.asList(
                            "--no-sandbox",
                            "--disable-dev-shm-usage",
                            "--disable-gpu",
                            "--disable-blink-features=AutomationControlled",
                            "--window-size=1920,1080"
                    ))
                    .setSlowMo(100)
                    .setDownloadsPath(Paths.get(ResourceManager.getEnvironment().getTmpDirLocation()));
        }
        browser = playwright.chromium().launch(options);
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
                .setUserAgent(firefoxUserAgent)
                .setViewportSize(1920, 1080)
                .setIgnoreHTTPSErrors(true)
                .setJavaScriptEnabled(true);

        if (Environment.get().isPlaywrightDebug()) {
            LOGGER.warn("[TEST] Storing web ui testing video into {}", Environment.get().getLogsDirLocation() + "/playwright-videos/");
            contextOptions.setRecordVideoDir(Paths.get(Environment.get().getLogsDirLocation() + "/playwright-videos/"));
        }
        context = browser.newContext(contextOptions);
        page = context.newPage();
        loginToOcp(loginUrl, kubeCredentials[0], kubeCredentials[1]);
        page.waitForLoadState();
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    void loginToOcp(String dashboardsUrl, String username, String password) {
        LOGGER.info("Logging into {}", dashboardsUrl);
        page.navigate(dashboardsUrl);
        page.waitForLoadState();
        page.getByText("htpasswd").click(clicker);
        page.getByText("Username").fill(username);
        page.getByText("Password").fill(password);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Log in")).click();

        LOGGER.info("Logged in! Waiting for dashboards to load");
        page.waitForURL("**/dashboards**", new Page.WaitForURLOptions().setTimeout(30000));

        // Double-check login success
        String currentUrl = page.url();
        if (currentUrl.contains("/auth/error")) {
            throw new RuntimeException("Login failed: " + currentUrl);
        }
        LOGGER.info("Logged in! Current URL: {}", currentUrl);
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
