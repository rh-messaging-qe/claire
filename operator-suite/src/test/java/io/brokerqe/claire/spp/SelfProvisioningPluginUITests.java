/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.spp;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.junit.UsePlaywright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.KubernetesVersion;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.exception.ClaireRuntimeException;
import io.brokerqe.claire.junit.TestMinimumKubernetesVersion;
import io.brokerqe.claire.junit.TestOLMSupported;
import io.brokerqe.claire.junit.TestValidSince;
import io.brokerqe.claire.plugins.ACSelfProvisioningPlugin;
import io.brokerqe.claire.plugins.AMQSelfProvisioningPlugin;
import io.brokerqe.claire.webconsole.ArtemisMenu;
import io.brokerqe.claire.webconsole.WebconsoleCommon;
import io.fabric8.kubernetes.api.model.Pod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@UsePlaywright
@TestOLMSupported
@TestValidSince(ArtemisVersion.VERSION_2_40)
@TestMinimumKubernetesVersion(KubernetesVersion.VERSION_1_29)
public class SelfProvisioningPluginUITests extends BaseWebUITests {

    private static final Logger LOGGER = LoggerFactory.getLogger(SelfProvisioningPluginUITests.class);
    private final String testNamespace = getRandomNamespaceName("spp-tests", 2);
    private ACSelfProvisioningPlugin acSelfProvisioningPlugin;

    @BeforeAll
    void deploySetup() {
        setupDefaultClusterOperator(testNamespace);
        if (ResourceManager.getEnvironment().isUpstreamArtemis()) {
            acSelfProvisioningPlugin = new ACSelfProvisioningPlugin();
        } else {
            acSelfProvisioningPlugin = new AMQSelfProvisioningPlugin();
        }
        ResourceManager.addDeployedCustomTool(acSelfProvisioningPlugin.deploy());
    }

    @AfterAll
    void undeploy() {
        acSelfProvisioningPlugin.undeploy();
        ResourceManager.removeDeployedCustomTool(acSelfProvisioningPlugin);
        teardownDefaultClusterOperator(testNamespace);
    }

    @AfterEach
    void cleanResources() {
        for (ActiveMQArtemis broker : ResourceManager.getArtemisClient().inNamespace(testNamespace).list().getItems()) {
            LOGGER.info("broker={}", broker.getMetadata().getName());
            try {
                deleteBrokerSpp(page, broker.getMetadata().getName(), testNamespace);
            } catch (Error e) {
                LOGGER.debug("[{}] Error when trying to undeploy forgotten(?) broker {}", testNamespace, broker.getMetadata().getName());
            }
        }
    }

    void navigateWorkloadBrokers(Page page) {
        navigateWorkloadBrokers(page, null);
    }

    void navigateWorkloadBrokers(Page page, String brokerName) {
        LOGGER.info("[{}] Navigate to Workloads/Brokers screen", testNamespace);

        page.waitForSelector("#nav-toggle", new Page.WaitForSelectorOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(60000));

        page.waitForSelector("*", new Page.WaitForSelectorOptions().setTimeout(60000));

        Locator navToggle = page.locator("#nav-toggle");
        Locator pageSidebar = page.locator("#page-sidebar");

        if (!pageSidebar.isVisible()) {
            LOGGER.debug("Click nav-menu as it is not visible");
            navToggle.click(clicker);
        }

        Locator workloadsMenuButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Workloads"));
        workloadsMenuButton.click();

        Locator menuContentLoc = workloadsMenuButton.locator("..").getByRole(AriaRole.LIST);
        List<String> menuContent = menuContentLoc.allInnerTexts();
        if (menuContent.isEmpty()) {
            workloadsMenuButton.click(WebconsoleCommon.getClicker());
            page.waitForLoadState();
            menuContent = workloadsMenuButton.locator("..").getByRole(AriaRole.LIST).allInnerTexts();
        }

        if (!menuContent.get(0).contains("Brokers")) {
            LOGGER.error("Workloads menu does not contain 'Brokers'! {}", menuContent);
            throw new ClaireRuntimeException("Workloads menu does not contain Brokers! Deployment problem?");
        }
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Brokers")).click(WebconsoleCommon.getClicker());
        page.waitForLoadState();
        if (brokerName != null) {
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(brokerName)).click(WebconsoleCommon.getClicker());
        }
        page.waitForLoadState();
    }

    ActiveMQArtemis createBrokerSpp(Page page, String brokerName, String namespace, int initialSize, String port, boolean externalAccess) {
        LOGGER.info("[{}] Create broker {} via UI", namespace, brokerName);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create Broker")).click(WebconsoleCommon.getClicker());
//        TestUtils.threadSleep(Constants.DURATION_10_SECONDS);
        page.getByText("CR Name").fill(brokerName);
        Locator plus = page.getByLabel("plus");
        IntStream.rangeClosed(1, initialSize - 1).forEach(i -> plus.click(WebconsoleCommon.getClicker()));

        LOGGER.info("[{}] set namespace {}", namespace, namespace);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Project:")).click(WebconsoleCommon.getClicker());
        page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(namespace)).click(WebconsoleCommon.getClicker());

        LOGGER.info("[{}] Add acceptor, ports & create broker", namespace);
        page.getByText("Add an acceptor").click(WebconsoleCommon.getClicker());
        page.getByText("Port").fill(port);
        page.getByRole(AriaRole.CHECKBOX, new Page.GetByRoleOptions().setName("Expose")).check();

        if (externalAccess) {
            String certName = "spp-test-issuer";
            page.getByText("Apply preset").click(WebconsoleCommon.getClicker());
            TestUtils.threadSleep(Constants.DURATION_2_SECONDS);
            page.locator("#selectable-first-card").click(WebconsoleCommon.getClicker());
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create a new chain of trust")).click(WebconsoleCommon.getClicker());
            Locator issuerText = page.getByText(Pattern.compile("creation of 3 elements", Pattern.CASE_INSENSITIVE));
            issuerText.locator("..").getByRole(AriaRole.TEXTBOX).fill(certName); // select parent and search within

            page.getByLabel("Select a preset").getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Create").setExact(true)).click(clicker);
            page.getByText("Confirm").click(clicker);
            /**
             * Don't touch anything after preset is applied! Preserved for future tests
            // generate trust secrets
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Generate")).nth(1).click();
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Close drawer panel")).click();
            // Cert secrets
            page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Select a secret")).first().click();
            page.getByText(certName + "-cert-secret").click();
            // Trust secrets
            page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Select a secret")).nth(1).click();
            // this uses always static name "ca-cert-amq-spp-test-secret" -> "ca-cert-amq-spp-test-N-secret"
            page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName("ca-cert-amq-spp-test-secret")).click();
            page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(Pattern.compile(certName.replace("issuer", "secret"), Pattern.CASE_INSENSITIVE))).click();
            */
        }
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create").setExact(true)).click(clicker);

        TestUtils.threadSleep(Constants.DURATION_5_SECONDS);
        ActiveMQArtemis broker = ResourceManager.getArtemisClient().inNamespace(namespace).withName(brokerName).get();
        ResourceManager.waitForBrokerDeployment(namespace, broker, false, null, ResourceManager.calculateWaitTime(broker));
        return broker;
    }

    void deleteBrokerSpp(Page page, String brokerName, String namespace) {
        LOGGER.info("[{}] Delete broker {} via UI", namespace, brokerName);
        navigateWorkloadBrokers(page, brokerName);
        try {
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Kebab toggle")).click(clicker);
        } catch (TimeoutError e) {
            page.getByLabel("Actions").click(clicker);
        }
        page.getByText("Delete Broker").click(clicker);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Delete")).click(clicker);
        TestUtils.threadSleep(Constants.DURATION_5_SECONDS);

        LOGGER.info("[{}] Check non existence of brokers.", namespace);
        navigateWorkloadBrokers(page);
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Brokers")).click(clicker);
        page.waitForLoadState();
        String emptyMessage;
        try {
            page.waitForSelector("[data-test='empty-box']", new Page.WaitForSelectorOptions().setTimeout(5000));
            emptyMessage = page.locator("[data-test='empty-box']").textContent();
        } catch (TimeoutError e) {
            emptyMessage = page.locator("[data-test='empty-message']").textContent();
        }
        assertEquals("Not found", emptyMessage);
    }

    void checkConditions(Page page, String brokerName) {
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Brokers")).click(clicker);
        page.getByPlaceholder("Search by name...").fill(brokerName);
        Locator conditions = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("\\d+ OK.*\\d+")));
        assertEquals("5 OK / 5", conditions.textContent());
        TestUtils.threadSleep(Constants.DURATION_2_SECONDS);

        conditions.click(clicker);
        List<String> textConditions = page.getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("Status Report")).allInnerTexts();
        LOGGER.debug("Conditions: UI={}\nCR status:{}", conditions.textContent(), textConditions);
        try {
            page.getByLabel("Close").click(clicker);
        } catch (TimeoutError e) {
            LOGGER.warn("Pop-up window has been closed too fast (semi-bug in browser?)");
        }
    }

    void checkMessageCountInAddress(Page page, String brokerName, String addressName, int messageCount) {
        LOGGER.info("[{}] Check addresses & message count", testNamespace);
        navigateWorkloadBrokers(page, brokerName);
        WebconsoleCommon.setMenu(page, ArtemisMenu.Pods);

        Pod pod = getClient().getFirstPodByPrefixName(testNamespace, brokerName);
        page.locator("a", new Page.LocatorOptions().setHasText(pod.getMetadata().getName())).click(clicker);
        WebconsoleCommon.setMenu(page, ArtemisMenu.Addresses);
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(addressName)).click(clicker);
        page.getByPlaceholder("Search by attribute name...").fill("RoutedMessageCount");

        List<Locator> locators = page.getByRole(AriaRole.GRID, new Page.GetByRoleOptions().setName("Address Details Table"))
                .getByRole(AriaRole.ROW, new Locator.GetByRoleOptions().setName("RoutedMessageCount")).all();
        for (Locator locator : locators) {
            if (locator.textContent().startsWith("RoutedMessageCount")) {
                String count = locator.textContent().replace("RoutedMessageCount", "");
                assertEquals(String.valueOf(messageCount), count, String.format("Message count in address %s is same as expected!", addressName));
                break;
            }
        }
    }

    // int sizeBy < 0 -> scaledown
    // int sizeBy > 0 -> scaleup
    ActiveMQArtemis scaleBrokerUI(Page page, String brokerName, int sizeBy, int expectedSize, String namespace) {
        LOGGER.info("[{}] Scale down broker {} to 2 replicas", namespace, brokerName);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Kebab toggle")).click(clicker);
        page.getByText("Edit Broker").click(clicker);

        Locator scaleButton;
        if (sizeBy > 0) {
            scaleButton = page.getByLabel("plus");
        } else {
            scaleButton = page.getByLabel("minus");
        }
        for (int i = 0; i < Math.abs(sizeBy); i++) {
            scaleButton.click(clicker);
        }
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Apply").setExact(true)).click(clicker);
        TestUtils.threadSleep(Constants.DURATION_5_SECONDS);

        ActiveMQArtemis broker = ResourceManager.getArtemisClient().inNamespace(namespace).withName(brokerName).get();
        ResourceManager.waitForBrokerDeployment(namespace, broker);
        String sizeActual = getBrokerSize(brokerName);
        assertEquals(String.valueOf(expectedSize), sizeActual);
        return broker;
    }

    @Test
    public void testSppConsole() {
        String brokerName = "spp-test-broker1";
        int initialSize = 3;

        navigateWorkloadBrokers(page);
        ActiveMQArtemis broker1 = createBrokerSpp(page, brokerName, testNamespace, initialSize, "33333", false);

        LOGGER.info("[{}] Check broker {} data", testNamespace, brokerName);
        String size = getBrokerSize(brokerName);
        assertEquals(String.valueOf(initialSize), size);
        assertEquals(brokerName, broker1.getMetadata().getName());
        assertEquals(testNamespace, broker1.getMetadata().getNamespace());
        assertEquals(initialSize, broker1.getSpec().getDeploymentPlan().getSize());

        checkConditions(page, brokerName);
        broker1 = scaleBrokerUI(page, brokerName, -2, 1, testNamespace);
        assertEquals(1, broker1.getSpec().getDeploymentPlan().getSize());
        deleteBrokerSpp(page, brokerName, testNamespace);
    }

    // Create a broker exposing an ingress with a cert-manager cert and
    // exchange messages to this broker from the outside of the cluster.
    @Test
    public void testExternalMessaging() {
        String brokerName = "ext-spp-brk";
        navigateWorkloadBrokers(page);
        // allow external access
        ActiveMQArtemis broker = createBrokerSpp(page, brokerName, testNamespace, 1, "33333", true);

        LOGGER.info("[{}] Check broker {} data", testNamespace, brokerName);
        String size = getBrokerSize(brokerName);
        assertEquals("1", size);
        assertEquals(1, broker.getSpec().getDeploymentPlan().getSize());

        // Copy example send messages command
        navigateWorkloadBrokers(page, brokerName);
        downloadFile(page, "spp-test-issuer-cert-secret.pem");

        String artemisMsgCheckCmd = page.getByRole(AriaRole.CODE).textContent();
        LOGGER.info("[{}] Will execute locally provided `artemis check` command: \n{}", testNamespace, artemisMsgCheckCmd);
        String artemisDefaultDir = TestUtils.getStandaloneArtemisDefaultDir().toString();
        // remove initial "." from provided command
        TestUtils.executeLocalCommand(20, "/bin/bash", "-lc", artemisDefaultDir + artemisMsgCheckCmd.substring(1));

        String addressName = "TEST"; // default address used in command
        checkMessageCountInAddress(page, brokerName, addressName, 10);

        deleteBrokerSpp(page, brokerName, testNamespace);
    }

    String getBrokerSize(String brokerName) {
//        if (ResourceManager.getEnvironment().isUpstreamArtemis()) {
        try {
            // upstream code <td id="Size" class="pf-v5-c-table__td" role="gridcell">1</td>
            Locator sizeCell = page.locator("#Size");
            return sizeCell.textContent();
        } catch (TimeoutError e) {
            // downstream code
            return page.getByRole(AriaRole.ROW, new Page.GetByRoleOptions().setName(brokerName)).locator("td[data-label='Size']").textContent();
        }
    }
}
