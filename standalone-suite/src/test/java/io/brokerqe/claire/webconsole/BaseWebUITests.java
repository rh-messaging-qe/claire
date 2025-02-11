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
import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.ArtemisVersion;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

enum ArtemisTabs {
    Status,
    Connections,
    Sesssions,
    Producers,
    Consumers,
    Addresses,
    Queues,
    BrokerDiagram
}

enum OperationFilter {
    Equals,
    Contains,
    Does_Not_Contain,
    Greater_Than,
    Less_Than
}

@TestValidSince(ArtemisVersion.VERSION_2_39)
public class BaseWebUITests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseWebUITests.class);

    static Playwright playwright;
    static Browser browser;
    static BrowserContext context;
    static Page initialPage;
    static Locator.ClickOptions clicker;
    static Locator.FillOptions filler;

    @AfterAll
    static void closeBrowser() {
        context.close();
        browser.close();
        playwright.close();
    }

    static void launchBrowser() {
        playwright = Playwright.create();
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions();
        if (ResourceManager.getEnvironment().isPlaywrightDebug()) {
            options = new BrowserType.LaunchOptions()
                    .setHeadless(false)
                    .setDownloadsPath(Paths.get(ResourceManager.getEnvironment().getTmpDirLocation()));
        }
        browser = playwright.chromium().launch(options);
        clicker = new Locator.ClickOptions().setTimeout(5000);
        filler = new Locator.FillOptions().setTimeout(5000);
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
        initialPage = context.newPage();
    }

    void loginToArtemis(ArtemisContainer artemisContainer, String loginUrl, String username, String password) {
        LOGGER.info("Logging into {}", loginUrl);
        initialPage.navigate(loginUrl);
        initialPage.getByText("Username").fill(username);
        initialPage.getByText("Password").fill(password);
        initialPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Log in")).click();
    }

    Page loginToArtemisConnector(ArtemisContainer artemisContainer, String loginUrl, int webPort, String username, String password) {
        LOGGER.info("Logging into {}", loginUrl);
        initialPage.navigate(loginUrl);

        LOGGER.info("Create new remote connection");
        initialPage.getByText("Add connection").click();

        initialPage.getByText("Name").fill(artemisContainer.getName());
        initialPage.getByText("HTTPS").click();
        initialPage.getByText("Port").fill(String.valueOf(webPort));
        initialPage.getByText("Path").fill("/console/jolokia");
        initialPage.getByText("Test connection").click();
        initialPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Add").setExact(true)).click();

        // Get page after a specific action (e.g. clicking a link)
        Page artemisPage = context.waitForPage(() -> {
            initialPage.locator("[rowId='connection " + artemisContainer.getName() + "']").getByText("Connect").click();
        });
        artemisPage.getByText("Username").fill(username);
        artemisPage.getByText("Password").fill(password);
        artemisPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Log in")).click();
        LOGGER.info("Logging into artemis broker");
        artemisPage.waitForLoadState();
        TestUtils.threadSleep(Constants.DURATION_10_SECONDS);
        return artemisPage;
    }

    void navigateHome(Page page) {
        page.keyboard().press("Escape");
        page.getByRole(AriaRole.IMG, new Page.GetByRoleOptions().setName("Artemis Console")).click(clicker);
    }

    Page setTab(Page artemisPage, ArtemisTabs tabName) {
        switch (tabName) {
            // BUGGED!
            case Status -> artemisPage.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName("Connections")).nth(0).click(clicker);
            case Connections -> artemisPage.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName("Connections")).nth(1).click(clicker);
            case Sesssions -> artemisPage.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName("Sessions")).click(clicker);
            case Producers -> artemisPage.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName("Producers")).click(clicker);
            case Consumers -> artemisPage.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName("Consumers")).click(clicker);
            case Addresses -> artemisPage.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName("Addresses")).click(clicker);
            case Queues -> artemisPage.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName("Queues")).click(clicker);
            case BrokerDiagram -> artemisPage.getByText("Broker Diagram").click(clicker);
        }
        TestUtils.threadSleep(Constants.DURATION_500_MILLISECONDS);
        return artemisPage;
    }

    void checkVersions(Page artemisPage) {
        LOGGER.info("Checking internal versions");
        artemisPage.locator("header").getByRole(AriaRole.BUTTON).nth(1).click(clicker);
        artemisPage.getByText("About").click(clicker);
        String[] versionsStr = artemisPage.locator("#hawtio-about-product-info").allInnerTexts().get(0).split("\n");
        artemisPage.getByLabel("Close Dialog").click();

        Map<String, String> libVersionMap = new HashMap<>();
        for (int i = 1; i < versionsStr.length; i = i + 2) {
            libVersionMap.put(versionsStr[i], versionsStr[i + 1]);
        }
        LOGGER.info("{}", libVersionMap);
        assertThat(libVersionMap.keySet(), allOf(
                hasItem("Artemis Console"),
                hasItem("Hawtio React")));
    }

    protected List<Map<String, String>> getTableData(Page page) {
        List<Locator> rows = page.getByRole(AriaRole.GRID, new Page.GetByRoleOptions().setName(
                Pattern.compile("Data Table|Column Management Table"))).getByRole(AriaRole.ROW).all();
        List<Map<String, String>> tableData = new ArrayList<>();
        List<String> header = new ArrayList<>();

        // row 1+ has gridcell. row0 is header - have to cope it differently
        for (int i = 0; i < rows.size(); i++) {
            if (i == 0) {
                for (String headerItem : rows.get(i).getByRole(AriaRole.COLUMNHEADER).allTextContents()) {
                    header.add(headerItem);
                }
            } else {
                Map<String, String> tableLine = new HashMap<>();
                List<Locator> cells = rows.get(i).getByRole(AriaRole.GRIDCELL).all();
                for (int j = 0; j < cells.size() - 1; j++) {
                    if (j > header.size() - 1) {
                        LOGGER.error("Unable to insert this item! Unknown header! {} {} ", header, cells.get(j).textContent());
                    }
                    tableLine.put(header.get(j), cells.get(j).textContent());
                }
                tableData.add(tableLine);
            }
        }
        return tableData;
    }

    protected void checkAddressesPresence(Page artemisPage, int expectedAddressCount, String addressPrefix) {
        LOGGER.info("Checking addresses");
        setTab(artemisPage, ArtemisTabs.Addresses);
        filterBy(artemisPage, "Name", OperationFilter.Contains, addressPrefix, null);

        List<Map<String, String>> addressesData = getTableData(artemisPage);
        LOGGER.info("Found following addresses\n{}", addressesData);

        // construct address names only
        List<String> allAddresses = new ArrayList<>();
        addressesData.forEach(item -> allAddresses.add(item.get("Name")));

        assertThat(addressesData.size(), equalTo(expectedAddressCount));
        IntStream.range(0, expectedAddressCount).forEach(n -> {
            String addressName = addressPrefix + n;
            assertThat(allAddresses, hasItem(addressName));
            }
        );
    }

    protected void checkQueuesPresence(Page artemisPage, int expectedQueueCount, String queuePrefix) {
        LOGGER.info("Checking queues");
        setTab(artemisPage, ArtemisTabs.Queues);
        filterBy(artemisPage, "Name", OperationFilter.Contains, queuePrefix, "Name");

        List<Map<String, String>> queuesData = getTableData(artemisPage);
        LOGGER.info("Found following queues\n{}", queuesData);

        List<String> allQueues = new ArrayList<>();
        queuesData.forEach(item -> allQueues.add(item.get("Name")));

        assertThat(queuesData.size(), equalTo(expectedQueueCount));
        IntStream.range(0, expectedQueueCount).forEach(n -> {
                String addressName = queuePrefix + n;
                assertThat(allQueues, hasItem(addressName));
            }
        );
    }

    protected void checkQueueStats(Page page, String queueName, Map<String, String> expectedParameters) {
        LOGGER.info("Checking stats of queue: {}", queueName);
        setTab(page, ArtemisTabs.Queues);
        filterBy(page, "Name", OperationFilter.Equals, queueName, "Name");

        configureColumns(page, List.of("Total Messages Added", "Total Messages Acked", "Delivering Count"), true);
        List<Map<String, String>> queuesData = getTableData(page);
        LOGGER.debug("Found following queues\n{}", queuesData);
        assertThat(queuesData.size(), equalTo(1));

        for (String key : expectedParameters.keySet()) {
            String queueValue = queuesData.get(0).get(key);
            LOGGER.debug("[{}] '{}':{}", expectedParameters.get(key).equals(queueValue), key, queueValue);
            assertThat(queueValue, equalTo(expectedParameters.get(key)));
        }
    }

    protected void configureColumns(Page page, List<String> configureColumns, boolean showColumns) {
        LOGGER.info("Show columns [{}]: {} ", showColumns, configureColumns);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Manage Columns")).click();
        TestUtils.threadSleep(Constants.DURATION_500_MILLISECONDS);

        for (String enableColumn : configureColumns) {
            Locator column = page.getByLabel("Manage Columns").getByText(enableColumn);
            if (!column.isChecked() && showColumns || //enable
                    column.isChecked() && !showColumns) { // disable
                column.click();
            }
        }
        page.getByText("Save").click(clicker);
    }

    protected void clickBrokerOperations(Page artemisPage) {
        setTab(artemisPage, ArtemisTabs.Status);
        artemisPage.locator("div.pf-m-2-col path").click();
        artemisPage.getByText("Operations").click();
        playwright.selectors().setTestIdAttribute("aria-labelledby");
        TestUtils.threadSleep(Constants.DURATION_1_SECOND);
    }

    protected void createOperationMany(Page page, String addressPrefix, String queuePrefix, int count) {
        for (int i = 0; i < count; i++) {
            createOperation(page, addressPrefix + i, queuePrefix + i);
            TestUtils.threadSleep(Constants.DURATION_1_SECOND);
        }
    }

    protected void createOperation(Page artemisPage, String address, String queue) {
        LOGGER.info("Creating durable anycast address: {} queue: {}", address, queue);
        clickBrokerOperations(artemisPage);

        Locator loc = artemisPage.locator("[id='operation-execute-createQueue(java.lang.String,java.lang.String,boolean,java.lang.String)']");
        artemisPage.getByTestId("operation createQueue(java.lang.String,java.lang.String,boolean,java.lang.String) ex-toggle1").click();
        TestUtils.threadSleep(Constants.DURATION_2_SECONDS);

        try {
            // address
            artemisPage.locator("[id='operation-createQueue(java.lang.String,java.lang.String,boolean,java.lang.String)-arg-form-input-address-0']").fill(address, filler);
        } catch (Exception e) {
            // try it again
            LOGGER.warn("Trying to click again to fill address");
            artemisPage.getByTestId("operation createQueue(java.lang.String,java.lang.String,boolean,java.lang.String) ex-toggle1").click();
            TestUtils.threadSleep(Constants.DURATION_2_SECONDS);
            artemisPage.locator("[id='operation-createQueue(java.lang.String,java.lang.String,boolean,java.lang.String)-arg-form-input-address-0']").fill(address);
        }
        // queue name
        artemisPage.locator("[id='operation-createQueue(java.lang.String,java.lang.String,boolean,java.lang.String)-arg-form-input-name-1']").fill(queue);
        // durable
        artemisPage.locator("[id='operation-createQueue(java.lang.String,java.lang.String,boolean,java.lang.String)-arg-form-input-durable-2']").setChecked(true);
        // routingType
        artemisPage.locator("[id='operation-createQueue(java.lang.String,java.lang.String,boolean,java.lang.String)-arg-form-input-routingType-3']").fill("ANYCAST");
        loc.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Execute")).click();
        TestUtils.threadSleep(Constants.DURATION_2_SECONDS);
        artemisPage.locator("footer").getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Close")).click();
    }

    protected void deleteAddressOperationMany(Page page, String addressPrefix, int count) {
        for (int i = 0; i < count; i++) {
            deleteAddressOperation(page, addressPrefix + i);
            TestUtils.threadSleep(Constants.DURATION_2_SECONDS);
        }
    }

    protected void deleteAddressOperation(Page artemisPage, String address) {
        LOGGER.info("Deleting address: {}", address);
        clickBrokerOperations(artemisPage);
        TestUtils.threadSleep(Constants.DURATION_500_MILLISECONDS);
        Locator loc = artemisPage.locator("[id='operation-execute-deleteAddress(java.lang.String,boolean)']");
        artemisPage.getByTestId("operation deleteAddress(java.lang.String,boolean) ex-toggle1").click();
        TestUtils.threadSleep(Constants.DURATION_1_SECOND);
        try {
            // name
            artemisPage.locator("[id='operation-deleteAddress(java.lang.String,boolean)-arg-form-input-name-0']").fill(address, filler);
        } catch (Exception e) {
            // try it again
            LOGGER.warn("Trying to click again to fill address");
            artemisPage.getByTestId("operation deleteAddress(java.lang.String,boolean) ex-toggle1").click();
            TestUtils.threadSleep(Constants.DURATION_1_SECOND);
            artemisPage.locator("[id='operation-deleteAddress(java.lang.String,boolean)-arg-form-input-name-0']").fill(address);
        }
        // force
        artemisPage.locator("[id='operation-deleteAddress(java.lang.String,boolean)-arg-form-input-force-1']").setChecked(true);
        loc.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Execute")).click();

        artemisPage.locator("footer").getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Close")).click();
    }

    protected void filterBy(Page artemisPage, String predicateFilterName, OperationFilter operation, String objectName, String sortBy) {
        String operationName = operation.name().replaceAll("_", " ");
        Pattern objectFilterPattern = Pattern.compile("^Name$|^ID$");
        Pattern operationFilterPattern = Pattern.compile("^Equals$|^Contains$|^Does Not Contain$|^Greater Than$|^Less Than$");
        artemisPage.locator("div").filter(new Locator.FilterOptions().setHasText(objectFilterPattern)).click(clicker);

        // Filter by Name
        artemisPage.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(predicateFilterName)).click(clicker);

        // Filter by operation
        artemisPage.locator("div").filter(new Locator.FilterOptions().setHasText(operationFilterPattern)).click(clicker);
        artemisPage.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(operationName)).click(clicker);

        artemisPage.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions()).fill(objectName);
        artemisPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Search")).click(clicker);

        // sorting
        if (sortBy != null) {
            artemisPage.getByLabel("Options menu").click(clicker);
            artemisPage.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(sortBy)).click(clicker); // sort by QueueCount
            artemisPage.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName("Ascending")).click(clicker); // sort by QueueCount
            artemisPage.getByLabel("Options menu").click(clicker);
        }
        TestUtils.threadSleep(Constants.DURATION_2_SECONDS);
    }

    protected void sendMessageQueue(Page artemisPage, String queueName, String msgContent, int count) {
        setTab(artemisPage, ArtemisTabs.Queues);
        filterBy(artemisPage, "Name", OperationFilter.Equals, queueName, null);
        artemisPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Kebab toggle")).click(clicker);
        artemisPage.getByText("Send Message").click(clicker);
        TestUtils.threadSleep(Constants.DURATION_500_MILLISECONDS);

        artemisPage.locator("button").filter(new Locator.FilterOptions().setHasText(Pattern.compile("^xml$|^plaintext$"))).click(clicker);
        artemisPage.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName("plaintext")).click();
        artemisPage.getByText("Add Headers").click(clicker);

        IntStream.range(0, count).forEach(n -> {
            artemisPage.getByLabel("name-input-0").fill("header-option-1", filler);
            artemisPage.getByLabel("value-input-0").fill(String.valueOf(n), filler);

            String msgContentTmp = msgContent + n;
            artemisPage.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Editor content")).fill("");
            artemisPage.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Editor content")).fill(msgContentTmp, filler);
            artemisPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send")).click(clicker);
        });
        artemisPage.getByText("Cancel").click(clicker);
    }

    protected List<Map<String, String>> browseMessages(Page page, String queueName) {
        return browseMessages(page, queueName, null, null, null);
    }

    protected List<Map<String, String>> browseMessages(Page artemisPage, String queueName, String expMsgContent, Map<String, String> expHeaders, Map<String, String> expProperties) {
        setTab(artemisPage, ArtemisTabs.Queues);
        filterBy(artemisPage, "Name", OperationFilter.Equals, queueName, null);
        artemisPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Kebab toggle")).click(clicker);
        artemisPage.getByText("Browse Messages").click(clicker);
        TestUtils.threadSleep(Constants.DURATION_500_MILLISECONDS);
        List<Map<String, String>> browsedMessages = getTableData(artemisPage);
        int browsedMessagesSize = browsedMessages.size();
        LOGGER.debug("Browsed messages: \n{}", browsedMessages);

        browsedMessages.forEach(messageMap -> {
            String messageId = messageMap.get("Message ID");
            Map<String, String> headers = new HashMap<>();
            Map<String, String> properties = new HashMap<>();

            artemisPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(messageId)).click(clicker);
            String messageContent = artemisPage.locator("#body").inputValue();

            Locator headersGrid = artemisPage.getByRole(AriaRole.GRID, new Page.GetByRoleOptions().setName("Headers Table"));
            for (String line : headersGrid.locator("tbody").innerText().split("\n")) {
                String[] split = line.split("\\s+");
                if (split.length >= 2) {
                    headers.put(split[0], split[1]);
                } else {
                    headers.put(split[0], "");
                }
            }

            Locator propertiesGrid = artemisPage.getByRole(AriaRole.GRID, new Page.GetByRoleOptions().setName("Properties Table"));
            for (String line : propertiesGrid.locator("tbody").innerText().split("\n")) {
                String[] split = line.split("\\s+");
                properties.put(split[0], split[1]);
            }

            LOGGER.info("\n[Msg:{}] Content: {}\nheaders:{}\nproperties:{}", messageId, messageContent, headers, properties);
            if (expMsgContent != null) {
                assertThat(messageContent, containsString(expMsgContent));
            }
            if (expHeaders != null) {
                assertThat(headers.get("address"), equalTo(expHeaders.get("address")));
            }
            if (expProperties != null) {
                for (String expKey : expProperties.keySet()) {
                    assertThat(properties.keySet(), hasItem(expKey));
                    assertThat(Integer.parseInt(properties.get(expKey)), allOf(greaterThanOrEqualTo(0), lessThanOrEqualTo(browsedMessagesSize - 1)));
                }
            }
            artemisPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Browse")).click(clicker);
        });
        return browsedMessages;
    }

    protected void executeJolokiaCommand(ArtemisContainer artemis, String jolokiaUrlCommand, String parameters) {
        String command = String.format("curl -H \"Origin:http://%s:%d\" -u %s:%s '%s/%s'", artemis.getName(), ArtemisConstants.DEFAULT_WEB_CONSOLE_PORT,
                ArtemisConstants.ADMIN_NAME, ArtemisConstants.ADMIN_PASS, jolokiaUrlCommand, parameters);
        artemis.executeCommand("sh", "-c", command);
    }

    protected String getJolokiaUrlCommand(Page artemisPage, String getTestIdLocator) {
        Locator li = artemisPage.getByRole(AriaRole.LIST).getByTestId(getTestIdLocator);
        li.getByRole(AriaRole.BUTTON).nth(1).click(clicker);
        TestUtils.threadSleep(Constants.DURATION_1_SECOND);
        li.getByText("Copy Jolokia URL").click(clicker);

        String jolokiaUrlCommand = (String) artemisPage.evaluate("() => navigator.clipboard.readText()");
        LOGGER.debug("Got Jolokia URL:\n{}", jolokiaUrlCommand);
        artemisPage.keyboard().press("Escape");
        jolokiaUrlCommand = workaroundJolokiaCmd(jolokiaUrlCommand);
        return jolokiaUrlCommand;
    }

    private String workaroundJolokiaCmd(String jolokia) {
        return jolokia.replaceFirst("proxy/http/.*/console/", "").replaceFirst("8080", String.valueOf(ArtemisConstants.DEFAULT_WEB_CONSOLE_PORT));
    }
}
