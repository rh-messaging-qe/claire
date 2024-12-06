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
import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.KubernetesVersion;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.junit.TestMinimumKubernetesVersion;
import io.brokerqe.claire.junit.TestOLMSupported;
import io.brokerqe.claire.plugins.ACSelfProvisioningPlugin;
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
@TestMinimumKubernetesVersion(KubernetesVersion.VERSION_1_29)
public class SelfProvisioningPluginUITests extends BaseWebUITests {

    private static final Logger LOGGER = LoggerFactory.getLogger(SelfProvisioningPluginUITests.class);
    private final String testNamespace = getRandomNamespaceName("spp-tests", 2);

    String dashboardsUrl = getClient().getKubernetesClient().getMasterUrl().getHost().replace("api", "https://console-openshift-console.apps") + "/dashboards";
    String[] kubeCredentials = ResourceManager.getEnvironment().getKubeCredentials();

    @BeforeAll
    void deploySetup() {
        setupDefaultClusterOperator(testNamespace);
        ACSelfProvisioningPlugin.deploy();
    }

    @AfterAll
    void undeploy() {
        ACSelfProvisioningPlugin.undeploy();
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
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Workloads")).click();
        try {
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Brokers")).click(new Locator.ClickOptions().setTimeout(5000));
        } catch (TimeoutError e) {
            // probably workloads have been clicked twice (thus closed; so click again)
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Workloads")).click();
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Brokers")).click(new Locator.ClickOptions().setTimeout(5000));
        }

        if (brokerName != null) {
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(brokerName)).click();
        }
    }

    ActiveMQArtemis createBrokerSpp(Page page, String brokerName, String namespace, int initialSize, String port, boolean externalAccess) {
        LOGGER.info("[{}] Create broker {} via UI", namespace, brokerName);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create Broker")).click();
        page.getByText("CR Name").fill(brokerName);
        Locator plus = page.getByLabel("plus");
        IntStream.rangeClosed(1, initialSize - 1).forEach(i -> plus.click());

        LOGGER.info("[{}] set namespace {}", namespace, namespace);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Project:")).click();
        page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(namespace)).click();

        LOGGER.info("[{}] Add acceptor, ports & create broker", namespace);
        page.getByText("Add an acceptor").click();
        page.getByText("Port").fill(port);
        page.getByRole(AriaRole.CHECKBOX, new Page.GetByRoleOptions().setName("Expose")).check();

        if (externalAccess) {
            String certName = "spp-test-issuer";
            page.getByText("Apply preset").click();
            TestUtils.threadSleep(Constants.DURATION_2_SECONDS);
            page.locator("#selectable-first-card").click();
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create a new chain of trust")).click();
            Locator issuerText = page.getByText(Pattern.compile("creation of 3 elements", Pattern.CASE_INSENSITIVE));
            issuerText.locator("..").getByRole(AriaRole.TEXTBOX).fill(certName); // select parent and search within

            page.getByLabel("Select a preset").getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Create").setExact(true)).click();
            page.getByText("Confirm").click();
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
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create").setExact(true)).click();

        TestUtils.threadSleep(Constants.DURATION_5_SECONDS);
        ActiveMQArtemis broker = ResourceManager.getArtemisClient().inNamespace(namespace).withName(brokerName).get();
        ResourceManager.waitForBrokerDeployment(namespace, broker);
        return broker;
    }

    void deleteBrokerSpp(Page page, String brokerName, String namespace) {
        LOGGER.info("[{}] Delete broker {} via UI", namespace, brokerName);
        navigateWorkloadBrokers(page, brokerName);
        try {
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Kebab toggle")).click(new Locator.ClickOptions().setTimeout(5000));
        } catch (TimeoutError e) {
            page.getByLabel("Actions").click();
        }
        page.getByText("Delete Broker").click();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Delete")).click();
        TestUtils.threadSleep(Constants.DURATION_5_SECONDS);

        LOGGER.info("[{}] Check non existence of brokers.", namespace);
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Brokers")).click();
        playwright.selectors().setTestIdAttribute("data-test");  // default: "data-testid"
        String emptyMessage = page.getByTestId("empty-message").textContent();
        assertEquals("Not found", emptyMessage);
//        playwright.selectors().setTestIdAttribute("data-testid");  // default: "data-testid"
    }

    void checkConditions(Page page, String brokerName) {
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Brokers")).click();
        page.getByPlaceholder("Search by name...").fill(brokerName);
        String conditions = page.locator("#conditions").textContent();
        assertEquals("5 OK / 5", conditions);
        TestUtils.threadSleep(Constants.DURATION_2_SECONDS);

        page.locator("#conditions").click();
        List<String> textConditions = page.getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("Status Report")).allInnerTexts();
        LOGGER.debug("Conditions: UI={}\nCR status:{}", conditions, textConditions);
        try {
            page.getByLabel("Close").click();
        } catch (TimeoutError e) {
            LOGGER.warn("Pop-up window has been closed too fast (semi-bug in browser?)");
        }
    }

    void checkMessageCountInAddress(Page page, String brokerName, String addressName, int messageCount) {
        LOGGER.info("[{}] Check addresses & message count", testNamespace);
        navigateWorkloadBrokers(page, brokerName);
        page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName("Pods")).click();
        Pod pod = getClient().getFirstPodByPrefixName(testNamespace, brokerName);
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(pod.getMetadata().getName())).click();
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Addresses")).click();
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(addressName)).click();
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
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Kebab toggle")).click();
        page.getByText("Edit Broker").click();

        Locator scaleButton;
        if (sizeBy > 0) {
            scaleButton = page.getByLabel("plus");
        } else {
            scaleButton = page.getByLabel("minus");
        }
        for (int i = 0; i < Math.abs(sizeBy); i++) {
            scaleButton.click();
        }
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Apply").setExact(true)).click();
        TestUtils.threadSleep(Constants.DURATION_5_SECONDS);

        ActiveMQArtemis broker = ResourceManager.getArtemisClient().inNamespace(namespace).withName(brokerName).get();
        ResourceManager.waitForBrokerDeployment(namespace, broker);
        String sizeActual = page.locator("#Size").textContent();
        assertEquals(String.valueOf(expectedSize), sizeActual);
        return broker;
    }

    @Test
    public void testSppConsole() {
        String brokerName = "spp-test-broker1";
        int initialSize = 3;
        loginToOcp(dashboardsUrl, kubeCredentials[0], kubeCredentials[1]);

        navigateWorkloadBrokers(page);
        ActiveMQArtemis broker1 = createBrokerSpp(page, brokerName, testNamespace, initialSize, "33333", false);

        LOGGER.info("[{}] Check broker {} data", testNamespace, brokerName);
        String size = page.locator("#Size").textContent();
        page.getByRole(AriaRole.ROW, new Page.GetByRoleOptions().setName(brokerName)).locator("#Size").textContent();

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
        loginToOcp(dashboardsUrl, kubeCredentials[0], kubeCredentials[1]);

        navigateWorkloadBrokers(page);
        // allow external access
        ActiveMQArtemis broker = createBrokerSpp(page, brokerName, testNamespace, 1, "33333", true);

        LOGGER.info("[{}] Check broker {} data", testNamespace, brokerName);
        String size = page.getByRole(AriaRole.ROW, new Page.GetByRoleOptions().setName(brokerName)).locator("#Size").textContent();
        assertEquals("1", size);
        assertEquals(1, broker.getSpec().getDeploymentPlan().getSize());

        // Copy example send messages command
        navigateWorkloadBrokers(page, brokerName);
        downloadFile(page, "spp-test-issuer-cert-secret.pem");

        String artemisMsgCheckCmd = page.getByRole(AriaRole.CODE).textContent();
        LOGGER.info("[{}] Will execute locally provided `artemis check` command: \n{}", testNamespace, artemisMsgCheckCmd);
        String artemisDefaultDir = TestUtils.getStandaloneArtemisDefaultDir().toString();
        // remove initial "." from provided command
        TestUtils.executeLocalCommand(20, "/bin/bash", "-c", artemisDefaultDir + artemisMsgCheckCmd.substring(1));

        String addressName = "TEST"; // default address used in command
        checkMessageCountInAddress(page, brokerName, addressName, 10);

        deleteBrokerSpp(page, brokerName, testNamespace);
    }
}
