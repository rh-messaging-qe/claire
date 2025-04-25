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

enum ArtemisMenu {
    Artemis,
    ArtemisJMX,
    JMX,
    Runtime
}

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
    static Page artemisPage;
    static Locator.ClickOptions clicker;
    static Locator.FillOptions filler;
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
        artemisPage = context.newPage();
    }

    void loginToArtemis(String loginUrl, String username, String password) {
        LOGGER.info("Logging into {}", loginUrl);
        artemisPage.navigate(loginUrl);
        artemisPage.getByText("Username", new Page.GetByTextOptions().setExact(true)).fill(username);
        artemisPage.getByText("Password").fill(password);
        artemisPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Log in")).click(clicker);
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
        initialPage.getByText("Add connection").click(clicker);

        initialPage.getByText("Name").fill(artemisContainer.getName());
        initialPage.getByText("HTTPS").click(clicker);
        initialPage.getByText("Port").fill(String.valueOf(webPort));
        initialPage.getByText("Path").fill("/console/jolokia");
        initialPage.getByText("Test connection").click(clicker);
        initialPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Add").setExact(true)).click(clicker);

        // Get page after a specific action (e.g. clicking a link)
        artemisPage = context.waitForPage(() -> {
            initialPage.locator("[rowId='connection " + artemisContainer.getName() + "']").getByText("Connect").click(clicker);
        });
        artemisPage.getByText("Username").fill(username);
        artemisPage.getByText("Password").fill(password);
        artemisPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Log in")).click(clicker);
        LOGGER.info("Logging into artemis broker");
        artemisPage.waitForLoadState();
        TestUtils.threadSleep(Constants.DURATION_10_SECONDS);
        return artemisPage;
    }

    void navigateHome(Page page) {
        page.keyboard().press("Escape");
        String homeButton;
        if (getEnvironment().isUpstreamArtemis()) {
            homeButton = "Artemis Console";
        } else {
            homeButton = "AMQ Broker Console";
        }
        page.getByRole(AriaRole.IMG, new Page.GetByRoleOptions().setName(homeButton)).click(clicker);
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
        TestUtils.threadSleep(Constants.DURATION_2_SECONDS);
        return artemisPage;
    }

    Page setMenu(Page artemisPage, ArtemisMenu artemisMenu) {
        switch (artemisMenu) {
            case Artemis -> artemisPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Artemis").setExact(true)).click(clicker);
            case ArtemisJMX -> artemisPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Artemis JMX")).click(clicker);
            case JMX -> artemisPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("JMX").setExact(true)).click(clicker);
            case Runtime -> artemisPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Runtime")).click(clicker);
        }
        TestUtils.threadSleep(Constants.DURATION_2_SECONDS);
        return artemisPage;
    }

    void checkVersions(Page artemisPage) {
        LOGGER.info("Checking internal versions");
        artemisPage.locator("header").getByRole(AriaRole.BUTTON).nth(1).click(clicker);
        artemisPage.getByText("About").click(clicker);
        String[] versionsStr = artemisPage.locator("#hawtio-about-product-info").allInnerTexts().get(0).split("\n");
        artemisPage.getByLabel("Close Dialog").click(clicker);

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
        setMenu(artemisPage, ArtemisMenu.Artemis);
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
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Manage Columns")).click(clicker);
        TestUtils.threadSleep(Constants.DURATION_500_MILLISECONDS);

        for (String enableColumn : configureColumns) {
            Locator column = page.getByLabel("Manage Columns").getByText(enableColumn);
            if (!column.isChecked() && showColumns || //enable
                    column.isChecked() && !showColumns) { // disable
                column.click(clicker);
            }
        }
        page.getByText("Save").click(clicker);
    }

    protected void clickBrokerJMXOperations(Page artemisPage) {
        setMenu(artemisPage, ArtemisMenu.ArtemisJMX);
        Locator dropdownExpandButton = artemisPage.locator("[id=\"org\\.apache\\.activemq\\.artemis-folder-" + getArtemisContainer().getName() + "\"]")
                .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(getArtemisContainer().getName()));
        // broker operations
        try {
            dropdownExpandButton.click(clicker);
        } catch (TimeoutError e) {
            artemisPage.getByLabel("org.apache.activemq.artemis").click(clicker);
            TestUtils.threadSleep(Constants.DURATION_1_SECOND);
            dropdownExpandButton.click(clicker);
        }
        artemisPage.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName("Operations")).click(clicker);
        TestUtils.threadSleep(Constants.DURATION_1_SECOND);
        playwright.selectors().setTestIdAttribute("aria-labelledby");
    }

    protected void createAddressQueue(Page page, String addressPrefix, String queuePrefix, int count) {
        for (int i = 0; i < count; i++) {
            createAddressQueueOperation(page, addressPrefix + i, queuePrefix + i);
            TestUtils.threadSleep(Constants.DURATION_1_SECOND);
        }
    }

    protected void createAddressQueueOperation(Page page, String addressName, String queueName) {
        setMenu(page, ArtemisMenu.Artemis);
        setTab(page, ArtemisTabs.Addresses);
        LOGGER.info("Creating durable anycast address: {} queue: {}", addressName, queueName);
        createAddress(artemisPage, addressName);
        createQueue(page, addressName, queueName);
        filterClear(page);
    }

    protected void createAddress(Page artemisPage, String address) {
        LOGGER.info("Creating address {}", address);
        artemisPage.getByText("Create Address").click(clicker);
        artemisPage.locator("#address-name").fill(address);
        artemisPage.locator("#ANYCAST").click(clicker);
        artemisPage.getByLabel("Create Address Address Name").getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Create Address")).click(clicker);
        TestUtils.threadSleep(Constants.DURATION_500_MILLISECONDS);
        artemisPage.getByText("Close").click(clicker);
        TestUtils.threadSleep(Constants.DURATION_500_MILLISECONDS);
    }

    protected void createQueue(Page artemisPage, String address, String queue) {
        LOGGER.info("Creating queue {} on address {}", queue, address);
        filterBy(artemisPage, "Name", OperationFilter.Equals, address, null);
        TestUtils.threadSleep(Constants.DURATION_1_SECOND);
        artemisPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Kebab toggle")).click(clicker);
        artemisPage.getByText("Create Queue").click(clicker);
        artemisPage.locator("#queue-name").fill(queue);
        artemisPage.locator("#ANYCAST").click(clicker);
        artemisPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create Queue")).click(clicker);
        TestUtils.threadSleep(Constants.DURATION_500_MILLISECONDS);
        artemisPage.getByText("Close").click(clicker);
        TestUtils.threadSleep(Constants.DURATION_500_MILLISECONDS);
    }

    protected void deleteAddressQueue(Page artemisPage, String address, String queue, int addressCount) {
        for (int i = 0; i < addressCount; i++) {
            deleteQueueOperation(artemisPage, address + i, queue + i);
            deleteAddressOperation(artemisPage, address + i);
        }
    }

    protected void deleteAddressOperation(Page artemisPage, String address) {
        LOGGER.info("Deleting address {}", address);
        setMenu(artemisPage, ArtemisMenu.Artemis);
        setTab(artemisPage, ArtemisTabs.Addresses);
        deleteOperation(artemisPage, address);
    }

    protected void deleteQueueOperation(Page artemisPage, String address, String queue) {
        LOGGER.info("Deleting queue {} on address {}", queue, address);
        setMenu(artemisPage, ArtemisMenu.Artemis);
        setTab(artemisPage, ArtemisTabs.Queues);
        deleteOperation(artemisPage, queue);
    }

    private void deleteOperation(Page artemisPage, String queueOrAddress) {
        filterBy(artemisPage, "Name", OperationFilter.Equals, queueOrAddress, null);
        TestUtils.threadSleep(Constants.DURATION_1_SECOND);
        artemisPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Kebab toggle")).click(clicker);
        artemisPage.getByText(Pattern.compile("Delete.*")).click(clicker);
        TestUtils.threadSleep(Constants.DURATION_1_SECOND);
        artemisPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Confirm")).click(clicker);
        TestUtils.threadSleep(Constants.DURATION_1_SECOND);
    }

    protected void createOperationMany(Page page, String addressPrefix, String queuePrefix, int count) {
        for (int i = 0; i < count; i++) {
            createOperationJMX(page, addressPrefix + i, queuePrefix + i);
            TestUtils.threadSleep(Constants.DURATION_1_SECOND);
        }
    }

    protected void createOperationJMX(Page artemisPage, String address, String queue) {
        LOGGER.info("[JMX] Creating durable anycast address: {} queue: {}", address, queue);
        clickBrokerJMXOperations(artemisPage);

        Locator loc = artemisPage.locator("[id='operation-execute-createQueue(java.lang.String,java.lang.String,boolean,java.lang.String)']");
        artemisPage.getByTestId("operation createQueue(java.lang.String,java.lang.String,boolean,java.lang.String) ex-toggle1").click(clicker);
        TestUtils.threadSleep(Constants.DURATION_1_SECOND);

        try {
            // address
            artemisPage.locator("[id='operation-createQueue(java.lang.String,java.lang.String,boolean,java.lang.String)-arg-form-input-address-0']").fill(address, filler);
        } catch (Exception e) {
            // try it again
            LOGGER.warn("Trying to click again to fill address");
            artemisPage.getByTestId("operation createQueue(java.lang.String,java.lang.String,boolean,java.lang.String) ex-toggle1").click(clicker);
            TestUtils.threadSleep(Constants.DURATION_2_SECONDS);
            artemisPage.locator("[id='operation-createQueue(java.lang.String,java.lang.String,boolean,java.lang.String)-arg-form-input-address-0']").fill(address);
        }
        // queue name
        artemisPage.locator("[id='operation-createQueue(java.lang.String,java.lang.String,boolean,java.lang.String)-arg-form-input-name-1']").fill(queue);
        // durable
        artemisPage.locator("[id='operation-createQueue(java.lang.String,java.lang.String,boolean,java.lang.String)-arg-form-input-durable-2']").setChecked(true);
        // routingType
        artemisPage.locator("[id='operation-createQueue(java.lang.String,java.lang.String,boolean,java.lang.String)-arg-form-input-routingType-3']").fill("ANYCAST");
        loc.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Execute")).click(clicker);
        TestUtils.threadSleep(Constants.DURATION_2_SECONDS);
    }

    protected void deleteAddressOperationMany(Page page, String addressPrefix, int count) {
        for (int i = 0; i < count; i++) {
            deleteAddressOperationJMX(page, addressPrefix + i);
            TestUtils.threadSleep(Constants.DURATION_2_SECONDS);
        }
    }

    protected void deleteAddressOperationJMX(Page artemisPage, String address) {
        LOGGER.info("[JMX] Deleting address: {}", address);
        clickBrokerJMXOperations(artemisPage);
        TestUtils.threadSleep(Constants.DURATION_500_MILLISECONDS);
        Locator loc = artemisPage.locator("[id='operation-execute-deleteAddress(java.lang.String,boolean)']");
        artemisPage.getByTestId("operation deleteAddress(java.lang.String,boolean) ex-toggle1").click(clicker);
        TestUtils.threadSleep(Constants.DURATION_1_SECOND);
        try {
            // name
            artemisPage.locator("[id='operation-deleteAddress(java.lang.String,boolean)-arg-form-input-name-0']").fill(address, filler);
        } catch (Exception e) {
            // try it again
            LOGGER.warn("Trying to click again to fill address");
            artemisPage.getByTestId("operation deleteAddress(java.lang.String,boolean) ex-toggle1").click(clicker);
            TestUtils.threadSleep(Constants.DURATION_1_SECOND);
            artemisPage.locator("[id='operation-deleteAddress(java.lang.String,boolean)-arg-form-input-name-0']").fill(address);
        }
        // force
        artemisPage.locator("[id='operation-deleteAddress(java.lang.String,boolean)-arg-form-input-force-1']").setChecked(true);
        loc.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Execute")).click(clicker);
    }

    protected void filterClear(Page artemisPage) {
        artemisPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Reset")).click(clicker);
        artemisPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Search")).click(clicker);
        TestUtils.threadSleep(Constants.DURATION_1_SECOND);
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
        TestUtils.threadSleep(Constants.DURATION_1_SECOND);

        artemisPage.locator("button").filter(new Locator.FilterOptions().setHasText(Pattern.compile("^xml$|^plaintext$"))).click(clicker);
        artemisPage.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName("plaintext")).click(clicker);
        artemisPage.getByText("Add Headers").click(clicker);

        IntStream.range(0, count).forEach(n -> {
            artemisPage.getByLabel("name-input-0").fill("header-option-1", filler);
            artemisPage.getByLabel("value-input-0").fill(String.valueOf(n), filler);

            String msgContentTmp = msgContent + n;
            artemisPage.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Editor content")).fill("");
            artemisPage.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Editor content")).fill(msgContentTmp, filler);
            artemisPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send")).click(clicker);
            TestUtils.threadSleep(Constants.DURATION_500_MILLISECONDS);
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

    protected void executeJolokiaCommandLocally(ArtemisContainer artemis, String jolokiaUrlCommand, String parameters) {
        jolokiaUrlCommand = jolokiaUrlCommand.replace(
                String.valueOf(artemis.getGenericContainer().getMappedPort(ArtemisConstants.DEFAULT_WEB_CONSOLE_PORT)),
                String.valueOf(ArtemisConstants.DEFAULT_WEB_CONSOLE_PORT));
        String command = String.format("curl -H \"Origin:http://localhost:%d\" -u %s:%s '%s/%s'", ArtemisConstants.DEFAULT_WEB_CONSOLE_PORT,
                ArtemisConstants.ADMIN_NAME, ArtemisConstants.ADMIN_PASS, jolokiaUrlCommand, parameters);
        artemis.executeCommand("sh", "-c", command);
    }

    protected String getJolokiaUrlCommand(Page artemisPage, String operationText) {
        artemisPage.getByRole(AriaRole.LISTITEM).filter(new Locator.FilterOptions().setHasText(operationText)).getByLabel("", new Locator.GetByLabelOptions().setExact(true)).click(clicker);
        Locator listItem = artemisPage.locator("li:has-text('" + operationText + "')");
        listItem.locator("button.pf-v5-c-menu-toggle.pf-m-plain").click(clicker);
        listItem.getByText("Copy Jolokia URL").click(clicker);
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
