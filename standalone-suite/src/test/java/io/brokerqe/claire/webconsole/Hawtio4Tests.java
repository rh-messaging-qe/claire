/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.webconsole;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.client.deployment.ArtemisConfigData;
import io.brokerqe.claire.client.deployment.ArtemisDeployment;
import io.brokerqe.claire.clients.Protocol;
import io.brokerqe.claire.container.ArtemisContainer;
import io.brokerqe.claire.junit.TestValidSince;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesPattern;

@TestValidSince(ArtemisVersion.VERSION_2_40)
public class Hawtio4Tests extends BaseWebUITests {

    private static final Logger LOGGER = LoggerFactory.getLogger(Hawtio4Tests.class);
    protected ArtemisContainer artemisInstance;

    @BeforeAll
    void setupEnv() {
        String artemisName = "artemis";
        LOGGER.info("Creating artemis instance: " + artemisName);
        ArtemisConfigData artemisConfigData = new ArtemisConfigData().withTuneFile("tune.yaml.jinja2");
        artemisConfigData.withDebugLogs(true);
        artemisInstance = ArtemisDeployment.createArtemis(artemisName, artemisConfigData);
        launchBrowser();
        setArtemisContainer(artemisInstance);
        loginToArtemis(artemisInstance.getHttpConsoleUrl(true, false), ArtemisConstants.ADMIN_NAME, ArtemisConstants.ADMIN_PASS);
        // TMP MODE: make sure connector is started
//        artemisPage = loginToArtemisConnector(artemisInstance, "http://localhost:8080/console/connect/remote", webPort, ArtemisConstants.ADMIN_NAME, ArtemisConstants.ADMIN_PASS);
    }

    @BeforeEach
    void beforeEach() {
        WebconsoleCommon.navigateHome(artemisPage);
    }

    @Test
    @Tag(Constants.TAG_WEBCONSOLE)
    void addressQueueStatsTest() {
        int addressCount = 10;
        String prefix = "lala";
        WebconsoleCommon.checkVersions(artemisPage);
        WebconsoleCommon.createOperationMany(artemisPage, getArtemisContainer().getName(), prefix, prefix, addressCount);

        testSimpleSendReceive(artemisInstance, null, artemisInstance.getBrokerUri(Protocol.AMQP) + ":" + DEFAULT_AMQP_PORT,
                "lala0", ArtemisConstants.ADMIN_NAME, ArtemisConstants.ADMIN_PASS, false);
        sendReceiveMessagesNoCheck(artemisInstance, "lala1", 100, 50);

        WebconsoleCommon.checkAddressesPresence(artemisPage, addressCount, prefix);
        WebconsoleCommon.checkQueuesPresence(artemisPage, addressCount, prefix);

        Map<String, String> expectedMapLala0 = Map.of(
            "Address", "lala0",
            "Name", "lala0",
            "Total Messages Added", "1",
            "Total Messages Acked", "1",
            "Message Count", "0"
        );
        WebconsoleCommon.checkQueueStats(artemisPage, "lala0", expectedMapLala0);

        Map<String, String> expectedMapLala1 = Map.of(
            "Address", "lala1",
            "Name", "lala1",
            "Total Messages Added", "100",
            "Total Messages Acked", "50",
            "Message Count", "50"
        );
        WebconsoleCommon.checkQueueStats(artemisPage, "lala1", expectedMapLala1);
        WebconsoleCommon.deleteAddressOperationMany(artemisPage, getArtemisContainer().getName(), prefix, addressCount);
        WebconsoleCommon.checkAddressesPresence(artemisPage, 0, "lala");
    }

    @Test
    @Tag(Constants.TAG_WEBCONSOLE)
    void createDeleteAddressQueueTest() {
        int addressCount = 5;
        String prefix = "tralala";
        WebconsoleCommon.createAddressQueue(artemisPage, prefix, prefix, addressCount);
        WebconsoleCommon.deleteAddressQueue(artemisPage, prefix, prefix, addressCount);
    }

    @Test
    @Tag(Constants.TAG_WEBCONSOLE)
    public void testJolokiaCommands() {
        context.grantPermissions(Arrays.asList("clipboard-read", "clipboard-write"));
        String addressName = "jolokia-address";
        String queueName = "jolokia-queue";
        WebconsoleCommon.clickBrokerJMXOperations(artemisPage, getArtemisContainer().getName());

        LOGGER.info("Create Address using Jolokia URL");
        String jolokiaUrlCommand = getJolokiaUrlCommand(artemisPage, "createAddress(String,");
        executeJolokiaCommandLocally(artemisInstance, jolokiaUrlCommand, addressName + "/ANYCAST");

        LOGGER.info("Create Queue using Jolokia URL");
        jolokiaUrlCommand = getJolokiaUrlCommand(artemisPage, "createQueue(String, String, boolean)");
        executeJolokiaCommandLocally(artemisInstance, jolokiaUrlCommand, String.format("%s/%s/true", addressName, queueName));
        WebconsoleCommon.navigateHome(artemisPage);

        LOGGER.info("Send messages using Jolokia URL");
        int msgCount = 20;
        WebconsoleCommon.sendMessageQueue(artemisPage, queueName, "This is a test message content #", msgCount);
        WebconsoleCommon.navigateHome(artemisPage);

        LOGGER.info("Browse message using Jolokia URL");
        WebconsoleCommon.browseMessages(artemisPage, queueName, "This is a test message content #",
                Map.of("address", "jolokia-address::jolokia-queue"),
                Map.of("header-option-1", String.valueOf(msgCount - 1)));
        WebconsoleCommon.navigateHome(artemisPage);

        WebconsoleCommon.clickBrokerJMXOperations(artemisPage, getArtemisContainer().getName());
        LOGGER.info("Delete forcefully address using Jolokia URL");
        jolokiaUrlCommand = getJolokiaUrlCommand(artemisPage, "deleteAddress(String, boolean)");
        executeJolokiaCommandLocally(artemisInstance, jolokiaUrlCommand, String.format("%s/true", addressName));
        WebconsoleCommon.checkAddressesPresence(artemisPage, 0, addressName);
    }

    @Test
    @Tag(Constants.TAG_WEBCONSOLE)
    public void tesDeployedQueues() {
        LOGGER.info("Check deployed 500 broker addresses & queues");
        WebconsoleCommon.setTab(artemisPage, ArtemisTabs.Addresses);
        checkDestinations(artemisPage, "my_test_\\d+", 500);

        WebconsoleCommon.setTab(artemisPage, ArtemisTabs.Queues);
        checkDestinations(artemisPage, "my_test_\\d+", 500);
    }

    private void checkDestinations(Page artemisPage, String destinationPattern, int destinationCount) {
        artemisPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile(".* of .* items"))).click(WebconsoleCommon.getClicker());
        artemisPage.getByText("100 per page").click(WebconsoleCommon.getClicker());
        TestUtils.threadSleep(Constants.DURATION_2_SECONDS);
        WebconsoleCommon.filterBy(artemisPage, "Name", OperationFilter.Contains, "my_test_", "ID");

        Locator buttonNext = artemisPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("next"));
        Locator buttonPrev = artemisPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("previous"));

        List<Map<String, String>> addressesData = new ArrayList<>(WebconsoleCommon.getTableData(artemisPage));
        do {
            buttonNext.click(WebconsoleCommon.getClicker());
            TestUtils.threadSleep(Constants.DURATION_2_SECONDS);
            addressesData.addAll(WebconsoleCommon.getTableData(artemisPage));
        } while (buttonNext.isEnabled());
        for (Map<String, String> addressData : addressesData) {
            LOGGER.trace("{}", addressData);
            assertThat(addressData.get("Name"), matchesPattern(destinationPattern));
        }
        assertThat(addressesData.size(), equalTo(destinationCount));
    }

}
