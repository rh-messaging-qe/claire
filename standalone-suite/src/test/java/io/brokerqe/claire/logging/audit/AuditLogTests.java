/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.logging.audit;

import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.client.deployment.ArtemisConfigData;
import io.brokerqe.claire.client.deployment.ArtemisDeployment;
import io.brokerqe.claire.client.deployment.BundledClientDeployment;
import io.brokerqe.claire.client.deployment.StJavaClientDeployment;
import io.brokerqe.claire.clients.MessagingClient;
import io.brokerqe.claire.clients.MessagingClientException;
import io.brokerqe.claire.clients.bundled.BundledAmqpMessagingClient;
import io.brokerqe.claire.clients.bundled.BundledClientOptions;
import io.brokerqe.claire.clients.container.AmqpCliOptionsBuilder;
import io.brokerqe.claire.clients.container.AmqpQpidClient;
import io.brokerqe.claire.container.ArtemisContainer;
import io.brokerqe.claire.webconsole.WebConsoleSeleniumHelper;
import io.brokerqe.claire.junit.TestDisabledOn;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AuditLogTests extends AbstractSystemTests {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AuditLogTests.class);

    protected ArtemisContainer artemis;
    protected BundledClientDeployment artemisDeployableClient;
    private StJavaClientDeployment stDeployableClient;
    private String brokerUri;
    private Path auditLogPath;

    protected String adminFormattedAuth;
    protected String adminFormattedAddressCore;
    protected String adminFormattedAddressAmqp;
    protected String adminFormattedQueue;
    protected String adminFormattedSent;
    protected String adminFormattedRecv;
    protected String charlieFormattedAuth;
    protected String charlieFormattedSent;
    protected String charlieFormattedRecv;
    protected String aliceFormattedAuth;
    protected String aliceFormattedSent;
    protected String aliceFormattedRecv;
    protected String bobFormattedAuth;
    protected String bobFormattedSent;
    protected String bobFormattedRecv;
    protected String address;
    protected String queue;

    @BeforeAll
    void setupEnv() {
        String artemisName = "artemis";
        ArtemisConfigData artemisConfigData = new ArtemisConfigData();
        LOGGER.info("Creating artemis instance: " + artemisName);
        if (isMinimumTestVersion(ArtemisVersion.VERSION_2_28)) {
            artemisConfigData.withTuneFile("tune.yaml.jinja2");
        } else {
            artemisConfigData.withTuneFile("tune-2.21.0.yaml.jinja2");
        }
        artemis = ArtemisDeployment.createArtemis(artemisName, artemisConfigData);
        artemisDeployableClient = new BundledClientDeployment();
        stDeployableClient = new StJavaClientDeployment();
        brokerUri = Constants.AMQP_URL_PREFIX + artemis.getName() + ":" + DEFAULT_AMQP_PORT;
        auditLogPath = Path.of(getTestConfigDir(), artemis.getName(), ArtemisConstants.LOG_DIR, ArtemisConstants.AUDIT_LOG_FILE);

        // Test data
        address = "myAddress";
        queue = "myQueue";
        charlieFormattedAuth = String.format(ArtemisConstants.LOG_AUDIT_AUTHENTICATION_SUCC_PATTERN, ArtemisConstants.CHARLIE_NAME, ArtemisConstants.ROLE_SENDERS);
        charlieFormattedSent = String.format(ArtemisConstants.LOG_AUDIT_SENT_MESSAGE_PATTERN, ArtemisConstants.CHARLIE_NAME, ArtemisConstants.ROLE_SENDERS, address, queue);
        charlieFormattedRecv = String.format(ArtemisConstants.LOG_AUDIT_RECEIVED_MESSAGE_PATTERN, ArtemisConstants.CHARLIE_NAME, ArtemisConstants.ROLE_SENDERS, queue);

        aliceFormattedAuth = String.format(ArtemisConstants.LOG_AUDIT_AUTHENTICATION_SUCC_PATTERN, ArtemisConstants.ALICE_NAME, ArtemisConstants.ROLE_SENDERS);
        aliceFormattedSent = String.format(ArtemisConstants.LOG_AUDIT_SENT_MESSAGE_PATTERN, ArtemisConstants.ALICE_NAME, ArtemisConstants.ROLE_SENDERS, address, queue);
        aliceFormattedRecv = String.format(ArtemisConstants.LOG_AUDIT_AUTHENTICATION_FAIL_CONSUME_PATTERN, ArtemisConstants.ALICE_NAME, ArtemisConstants.ROLE_SENDERS, ArtemisConstants.ALICE_NAME, queue, address);

        bobFormattedAuth = String.format(ArtemisConstants.LOG_AUDIT_AUTHENTICATION_SUCC_PATTERN, ArtemisConstants.BOB_NAME, ArtemisConstants.ROLE_RECEIVERS);
        bobFormattedSent = String.format(ArtemisConstants.LOG_AUDIT_RECEIVED_MESSAGE_PATTERN, ArtemisConstants.BOB_NAME, ArtemisConstants.ROLE_RECEIVERS, queue);
        bobFormattedRecv = String.format(ArtemisConstants.LOG_AUDIT_AUTHENTICATION_FAIL_PRODUCE_PATTERN, ArtemisConstants.BOB_NAME, ArtemisConstants.ROLE_RECEIVERS, ArtemisConstants.BOB_NAME, queue, address);
        adminFormattedAuth = String.format(ArtemisConstants.LOG_AUDIT_AUTHENTICATION_SUCC_PATTERN, ArtemisConstants.ADMIN_NAME, ArtemisConstants.ROLE_ADMIN);
        adminFormattedAddressCore = String.format(ArtemisConstants.LOG_AUDIT_CREATE_ADDRESS_PATTERN_CORE, ArtemisConstants.ADMIN_NAME, ArtemisConstants.ROLE_ADMIN, address, queue);
        adminFormattedAddressAmqp = String.format(ArtemisConstants.LOG_AUDIT_CREATE_ADDRESS_PATTERN_AMQP, ArtemisConstants.ADMIN_NAME, ArtemisConstants.ROLE_ADMIN, address, queue);
        adminFormattedQueue = String.format(ArtemisConstants.LOG_AUDIT_CREATE_QUEUE_PATTERN, ArtemisConstants.ADMIN_NAME, ArtemisConstants.ROLE_ADMIN, queue, address);
        adminFormattedSent = String.format(ArtemisConstants.LOG_AUDIT_SENT_MESSAGE_PATTERN, ArtemisConstants.ADMIN_NAME, ArtemisConstants.ROLE_ADMIN, address, queue);
        adminFormattedRecv = String.format(ArtemisConstants.LOG_AUDIT_RECEIVED_MESSAGE_PATTERN, ArtemisConstants.ADMIN_NAME, ArtemisConstants.ROLE_ADMIN, queue);
    }

    @Test
    void webConsoleLoginLogoutTest() {
        // TODO: add parameters to test different browsers
        RemoteWebDriver driver = ResourceManager.getChromeRemoteDriver("chrome-browser");

        // load the console url (assertion is inside the method to be reused)
        WebConsoleSeleniumHelper.load(driver, artemis);

        // try to log in (assertion is inside the method to be reused)
        WebConsoleSeleniumHelper.login(driver, artemis);

        // try to log out (assertion is inside the method to be reused)
        WebConsoleSeleniumHelper.logout(driver, artemis);

        // assert the log does not contain the pattern
        String logs = artemis.getLogs();
        assertThat(logs).doesNotContainPattern(ArtemisConstants.LOG_PATTERN_FAILED_AUTH_304);
    }

    protected void checkAuditLogs(List<String> checkPatternLogs) {
        String auditLog = TestUtils.readFileContent(auditLogPath.toFile());
        LOGGER.trace("auditLog: {}", auditLog);
        for (String patternLog : checkPatternLogs) {
            LOGGER.debug("Checking audit log: {}", patternLog);
            assertThat(auditLog).containsPattern(patternLog);
        }
    }

    @AfterEach
    public void cleanBroker(TestInfo testInfo) {
        String auditLogCopy = String.format("%s.%s", auditLogPath.toString(), testInfo.getDisplayName());
        LOGGER.info("Copying file {} -> {}. \nRestarting broker", auditLogPath.toString(), auditLogCopy);
        TestUtils.copyFile(auditLogPath.toString(), auditLogCopy);
        artemis.restartWithStop();
        artemis.ensureBrokerIsActive();
    }

    @Test
    @TestDisabledOn(ArtemisVersion.VERSION_2_21)
    void checkBundledAmqpAuditLogsTest() {
        int msgsExpected = 5;
        LOGGER.info("Test Bundled AMQP Messaging. Starting test for admin user: {}", ArtemisConstants.ADMIN_NAME);
        BundledClientOptions options = new BundledClientOptions()
                .withDeployableClient(artemisDeployableClient)
                .withDestinationAddress(address)
                .withDestinationPort(DEFAULT_AMQP_PORT)
                .withMessageCount(msgsExpected)
                .withPassword(ArtemisConstants.ADMIN_NAME)
                .withUsername(ArtemisConstants.ADMIN_PASS)
                .withDestinationQueue(queue)
                .withDestinationUrl(artemis.getName());
        MessagingClient bundledClient = new BundledAmqpMessagingClient(options);
        int sent = bundledClient.sendMessages();
        int received = bundledClient.receiveMessages();
        MatcherAssert.assertThat(sent, equalTo(msgsExpected));
        MatcherAssert.assertThat(received, equalTo(msgsExpected));
        checkAuditLogs(List.of(adminFormattedAuth, adminFormattedAddressAmqp, adminFormattedQueue, adminFormattedSent, adminFormattedRecv));

        LOGGER.info("Starting test for senders/receivers user: {}", ArtemisConstants.CHARLIE_NAME);
        options = new BundledClientOptions()
                .withDeployableClient(artemisDeployableClient)
                .withDestinationAddress(address)
                .withDestinationPort(DEFAULT_AMQP_PORT)
                .withMessageCount(msgsExpected)
                .withPassword(ArtemisConstants.CHARLIE_NAME)
                .withUsername(ArtemisConstants.CHARLIE_PASS)
                .withDestinationQueue(queue)
                .withDestinationUrl(artemis.getName());
        bundledClient = new BundledAmqpMessagingClient(options);
        sent = bundledClient.sendMessages();
        received = bundledClient.receiveMessages();
        MatcherAssert.assertThat(sent, equalTo(msgsExpected));
        MatcherAssert.assertThat(received, equalTo(msgsExpected));
        checkAuditLogs(List.of(charlieFormattedAuth, charlieFormattedSent, charlieFormattedRecv));

        LOGGER.info("Starting test for senders user: {}", ArtemisConstants.ALICE_NAME);
        options = new BundledClientOptions()
                .withDeployableClient(artemisDeployableClient)
                .withDestinationAddress(address)
                .withDestinationPort(DEFAULT_AMQP_PORT)
                .withMessageCount(msgsExpected)
                .withPassword(ArtemisConstants.ALICE_NAME)
                .withUsername(ArtemisConstants.ALICE_PASS)
                .withDestinationQueue(queue)
                .withDestinationUrl(artemis.getName());
        BundledAmqpMessagingClient bundledClientAlice = new BundledAmqpMessagingClient(options);
        int sentAlice = bundledClientAlice.sendMessages();
        assertThrows(MessagingClientException.class, bundledClientAlice::receiveMessages);
        checkAuditLogs(List.of(aliceFormattedAuth, aliceFormattedSent, aliceFormattedRecv));

        LOGGER.info("Starting test for receivers user: {}", ArtemisConstants.BOB_NAME);
        options = new BundledClientOptions()
                .withDeployableClient(artemisDeployableClient)
                .withDestinationAddress(address)
                .withDestinationPort(DEFAULT_AMQP_PORT)
                .withMessageCount(msgsExpected)
                .withPassword(ArtemisConstants.BOB_NAME)
                .withUsername(ArtemisConstants.BOB_PASS)
                .withDestinationQueue(queue)
                .withDestinationUrl(artemis.getName());
        BundledAmqpMessagingClient bundledClientBob = new BundledAmqpMessagingClient(options);

        assertThrows(MessagingClientException.class, bundledClientBob::sendMessages);
        int receivedBob = bundledClientBob.receiveMessages();
        MatcherAssert.assertThat(sentAlice, equalTo(msgsExpected));
        MatcherAssert.assertThat(receivedBob, equalTo(msgsExpected));
        checkAuditLogs(List.of(bobFormattedAuth, bobFormattedSent, bobFormattedRecv));

        deleteQueue(artemisDeployableClient, queue);
        deleteAddress(artemisDeployableClient, address);
    }

    @Test
    @TestDisabledOn(ArtemisVersion.VERSION_2_21)
    void checkAmqpAuditLogsTest() {
        String fqqn = String.format("%s::%s", address, queue);
        int msgsExpected = 5;

        LOGGER.info("Starting test for admin user: {}", ArtemisConstants.ADMIN_NAME);
        Map<String, String> optionsAdmin = new AmqpCliOptionsBuilder()
                .connUsername(ArtemisConstants.ADMIN_NAME)
                .connPassword(ArtemisConstants.ADMIN_PASS)
                .address(fqqn)
                .count(msgsExpected)
                .build();
        MessagingClient messagingClient = new AmqpQpidClient(stDeployableClient, brokerUri, optionsAdmin, optionsAdmin);
        int sent = messagingClient.sendMessages();
        int received = messagingClient.receiveMessages();
        MatcherAssert.assertThat(sent, equalTo(msgsExpected));
        MatcherAssert.assertThat(received, equalTo(msgsExpected));
        checkAuditLogs(List.of(adminFormattedAuth, adminFormattedAddressAmqp, adminFormattedQueue, adminFormattedSent, adminFormattedRecv));

        LOGGER.info("Starting test for senders/receivers user: {}", ArtemisConstants.CHARLIE_NAME);
        Map<String, String> optionsCharlie = new AmqpCliOptionsBuilder()
                .connUsername(ArtemisConstants.CHARLIE_NAME)
                .connPassword(ArtemisConstants.CHARLIE_PASS)
                .address(fqqn)
                .count(msgsExpected)
                .build();

        messagingClient = new AmqpQpidClient(stDeployableClient, brokerUri, optionsCharlie, optionsCharlie);
        sent = messagingClient.sendMessages();
        received = messagingClient.receiveMessages();
        MatcherAssert.assertThat(sent, equalTo(msgsExpected));
        MatcherAssert.assertThat(received, equalTo(msgsExpected));
        checkAuditLogs(List.of(charlieFormattedAuth, charlieFormattedSent, charlieFormattedRecv));

        LOGGER.info("Starting test for senders user: {}", ArtemisConstants.ALICE_NAME);
        Map<String, String> optionsAlice = new AmqpCliOptionsBuilder()
                .connUsername(ArtemisConstants.ALICE_NAME)
                .connPassword(ArtemisConstants.ALICE_PASS)
                .address(fqqn)
                .count(msgsExpected)
                .build();
        AmqpQpidClient messagingClientAlice = new AmqpQpidClient(stDeployableClient, brokerUri, optionsAlice, optionsAlice);
        int sentAlice = messagingClientAlice.sendMessages();
        assertThrows(MessagingClientException.class, messagingClientAlice::receiveMessages);
        checkAuditLogs(List.of(aliceFormattedAuth, aliceFormattedSent, aliceFormattedRecv));

        LOGGER.info("Starting test for receivers user: {}", ArtemisConstants.BOB_NAME);
        Map<String, String> optionsBob = new AmqpCliOptionsBuilder()
                .connUsername(ArtemisConstants.BOB_NAME)
                .connPassword(ArtemisConstants.BOB_PASS)
                .address(fqqn)
                .count(msgsExpected)
                .build();
        AmqpQpidClient messagingClientBob = new AmqpQpidClient(stDeployableClient, brokerUri, optionsBob, optionsBob);
        assertThrows(MessagingClientException.class, messagingClientBob::sendMessages);
        int receivedBob = messagingClientBob.receiveMessages();
        MatcherAssert.assertThat(sentAlice, equalTo(msgsExpected));
        MatcherAssert.assertThat(receivedBob, equalTo(msgsExpected));
        checkAuditLogs(List.of(bobFormattedAuth, bobFormattedSent, bobFormattedRecv));

        deleteQueue(artemisDeployableClient, queue);
        deleteAddress(artemisDeployableClient, address);
    }
}
