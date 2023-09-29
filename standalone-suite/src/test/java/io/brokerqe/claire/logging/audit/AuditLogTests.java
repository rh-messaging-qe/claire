/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.logging.audit;

import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.client.deployment.BundledClientDeployment;
import io.brokerqe.claire.clients.MessagingClient;
import io.brokerqe.claire.clients.MessagingClientException;
import io.brokerqe.claire.clients.bundled.BundledAmqpMessagingClient;
import io.brokerqe.claire.clients.bundled.BundledClientOptions;
import io.brokerqe.claire.clients.bundled.BundledCoreMessagingClient;
import io.brokerqe.claire.container.ArtemisContainer;
import io.brokerqe.claire.helper.webconsole.LoginPageHelper;
import io.brokerqe.claire.helper.webconsole.MainPageHelper;
import io.brokerqe.claire.helper.webconsole.WebConsoleHelper;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AuditLogTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditLogTests.class);

    private ArtemisContainer artemisInstance;
    private BundledClientDeployment artemisDeployableClient;

    @BeforeAll
    void setupEnv() {
        String artemisName = "artemis";
        LOGGER.info("Creating artemis instance: " + artemisName);
        String tuneFile = generateYacfgProfilesContainerTestDir("tune.yaml.jinja2");
        artemisInstance = getArtemisInstance(artemisName, tuneFile);
        artemisDeployableClient = new BundledClientDeployment();
    }

    @Test
    void webConsoleLoginLogoutTest() {
        // TODO: add parameters to test different browsers
        // create a selenium remote web driver instance
        RemoteWebDriver driver = ResourceManager.getChromeRemoteDriver("chrome-browser");

        // load the console url (assertion is inside the method to be reused)
        WebConsoleHelper.load(driver, artemisInstance);

        // try to log in (assertion is inside the method to be reused)
        LoginPageHelper.login(driver, artemisInstance);

        // try to log out (assertion is inside the method to be reused)
        MainPageHelper.logout(driver, artemisInstance);

        // assert the log does not contain the pattern
        String logs = artemisInstance.getLogs();
        assertThat(logs).doesNotContainPattern(ArtemisConstants.LOG_PATTERN_FAILED_AUTH_304);
    }

    @Test
    void checkAuditLogsTest() {
        String address = "myAddress";
        String queue = "myQueue";
        Path auditLogPath = Path.of(getTestConfigDir(), artemisInstance.getName(), ArtemisConstants.LOG_DIR, ArtemisConstants.AUDIT_LOG_FILE);

        String adminFormattedAuth = String.format(ArtemisConstants.LOG_AUDIT_AUTHENTICATION_SUCC_PATTERN, ArtemisConstants.ADMIN_NAME, ArtemisConstants.ROLE_ADMIN);
        String adminFormattedAddress = String.format(ArtemisConstants.LOG_AUDIT_CREATE_ADDRESS_PATTERN, ArtemisConstants.ADMIN_NAME, ArtemisConstants.ROLE_ADMIN, address, queue);
        String adminFormattedQueue = String.format(ArtemisConstants.LOG_AUDIT_CREATE_QUEUE_PATTERN, ArtemisConstants.ADMIN_NAME, ArtemisConstants.ROLE_ADMIN, queue, address);
        String adminFormattedSent = String.format(ArtemisConstants.LOG_AUDIT_SENT_MESSAGE_PATTERN, ArtemisConstants.ADMIN_NAME, ArtemisConstants.ROLE_ADMIN, address, queue);
        String adminFormattedRecv = String.format(ArtemisConstants.LOG_AUDIT_RECEIVED_MESSAGE_PATTERN, ArtemisConstants.ADMIN_NAME, ArtemisConstants.ROLE_ADMIN, queue);

        String charlieFormattedAuth = String.format(ArtemisConstants.LOG_AUDIT_AUTHENTICATION_SUCC_PATTERN, ArtemisConstants.CHARLIE_NAME, ArtemisConstants.ROLE_SENDERS);
        String charlieFormattedSent = String.format(ArtemisConstants.LOG_AUDIT_SENT_MESSAGE_PATTERN, ArtemisConstants.CHARLIE_NAME, ArtemisConstants.ROLE_SENDERS, address, queue);
        String charlieFormattedRecv = String.format(ArtemisConstants.LOG_AUDIT_RECEIVED_MESSAGE_PATTERN, ArtemisConstants.CHARLIE_NAME, ArtemisConstants.ROLE_SENDERS, queue);

        String aliceFormattedAuth = String.format(ArtemisConstants.LOG_AUDIT_AUTHENTICATION_SUCC_PATTERN, ArtemisConstants.ALICE_NAME, ArtemisConstants.ROLE_SENDERS);
        String aliceFormattedSent = String.format(ArtemisConstants.LOG_AUDIT_SENT_MESSAGE_PATTERN, ArtemisConstants.ALICE_NAME, ArtemisConstants.ROLE_SENDERS, address, queue);
        String aliceFormattedRecv = String.format(ArtemisConstants.LOG_AUDIT_AUTHENTICATION_FAIL_CONSUME_PATTERN, ArtemisConstants.ALICE_NAME, ArtemisConstants.ROLE_SENDERS, ArtemisConstants.ALICE_NAME, queue, address);

        String bobFormattedAuth = String.format(ArtemisConstants.LOG_AUDIT_AUTHENTICATION_SUCC_PATTERN, ArtemisConstants.BOB_NAME, ArtemisConstants.ROLE_RECEIVERS);
        String bobFormattedSent = String.format(ArtemisConstants.LOG_AUDIT_RECEIVED_MESSAGE_PATTERN, ArtemisConstants.BOB_NAME, ArtemisConstants.ROLE_RECEIVERS, queue);
        String bobFormattedRecv = String.format(ArtemisConstants.LOG_AUDIT_AUTHENTICATION_FAIL_PRODUCE_PATTERN, ArtemisConstants.BOB_NAME, ArtemisConstants.ROLE_RECEIVERS, ArtemisConstants.BOB_NAME, address);

        LOGGER.info("Test Bundled Core Messaging");
        int msgsExpected = 5;
        BundledClientOptions options = new BundledClientOptions()
                .withDeployableClient(artemisDeployableClient)
                .withDestinationAddress(address)
                .withDestinationPort(DEFAULT_ALL_PORT)
                .withMessageCount(msgsExpected)
                .withPassword(ArtemisConstants.ADMIN_NAME)
                .withUsername(ArtemisConstants.ADMIN_PASS)
                .withDestinationQueue(queue)
                .withDestinationUrl(artemisInstance.getName());
        MessagingClient bundledClient = new BundledCoreMessagingClient(options);
        int sent = bundledClient.sendMessages();
        int received = bundledClient.receiveMessages();
        MatcherAssert.assertThat(sent, equalTo(msgsExpected));
        MatcherAssert.assertThat(received, equalTo(msgsExpected));

        String auditLog = TestUtils.readFileContent(auditLogPath.toFile());
        LOGGER.warn("auditLog: {}", auditLog);

        assertThat(auditLog).containsPattern(adminFormattedAuth);
        assertThat(auditLog).containsPattern(adminFormattedAddress);
        assertThat(auditLog).containsPattern(adminFormattedQueue);
        assertThat(auditLog).containsPattern(adminFormattedSent);
        assertThat(auditLog).containsPattern(adminFormattedRecv);

        options = new BundledClientOptions()
                .withDeployableClient(artemisDeployableClient)
                .withDestinationAddress(address)
                .withDestinationPort(DEFAULT_ALL_PORT)
                .withMessageCount(msgsExpected)
                .withPassword(ArtemisConstants.CHARLIE_NAME)
                .withUsername(ArtemisConstants.CHARLIE_PASS)
                .withDestinationQueue(queue)
                .withDestinationUrl(artemisInstance.getName());
        bundledClient = new BundledCoreMessagingClient(options);
        sent = bundledClient.sendMessages();
        received = bundledClient.receiveMessages();
        MatcherAssert.assertThat(sent, equalTo(msgsExpected));
        MatcherAssert.assertThat(received, equalTo(msgsExpected));

        auditLog = TestUtils.readFileContent(auditLogPath.toFile());
        assertThat(auditLog).containsPattern(charlieFormattedAuth);
        assertThat(auditLog).containsPattern(charlieFormattedSent);
        assertThat(auditLog).containsPattern(charlieFormattedRecv);

        options = new BundledClientOptions()
                .withDeployableClient(artemisDeployableClient)
                .withDestinationAddress(address)
                .withDestinationPort(DEFAULT_ALL_PORT)
                .withMessageCount(msgsExpected)
                .withPassword(ArtemisConstants.ALICE_NAME)
                .withUsername(ArtemisConstants.ALICE_PASS)
                .withDestinationQueue(queue)
                .withDestinationUrl(artemisInstance.getName());
        BundledCoreMessagingClient bundledClientAlice = new BundledCoreMessagingClient(options);
        int sentAlice = bundledClientAlice.sendMessages();
        assertThrows(MessagingClientException.class, bundledClientAlice::receiveMessages);

        auditLog = TestUtils.readFileContent(auditLogPath.toFile());
        LOGGER.warn("auditLog: {}", auditLog);
        assertThat(auditLog).containsPattern(aliceFormattedAuth);
        assertThat(auditLog).containsPattern(aliceFormattedSent);
        assertThat(auditLog).containsPattern(aliceFormattedRecv);

        options = new BundledClientOptions()
                .withDeployableClient(artemisDeployableClient)
                .withDestinationAddress(address)
                .withDestinationPort(DEFAULT_ALL_PORT)
                .withMessageCount(msgsExpected)
                .withPassword(ArtemisConstants.BOB_NAME)
                .withUsername(ArtemisConstants.BOB_PASS)
                .withDestinationQueue(queue)
                .withDestinationUrl(artemisInstance.getName());
        BundledCoreMessagingClient bundledClientBob = new BundledCoreMessagingClient(options);

        assertThrows(MessagingClientException.class, bundledClientBob::sendMessages);
        int receivedBob = bundledClientBob.receiveMessages();
        MatcherAssert.assertThat(sentAlice, equalTo(msgsExpected));
        MatcherAssert.assertThat(receivedBob, equalTo(msgsExpected));

        auditLog = TestUtils.readFileContent(auditLogPath.toFile());
        LOGGER.warn("auditLog: {}", auditLog);
        assertThat(auditLog).containsPattern(bobFormattedAuth);
        assertThat(auditLog).containsPattern(bobFormattedSent);
        assertThat(auditLog).containsPattern(bobFormattedRecv);

        deleteQueue(artemisDeployableClient, queue);
        deleteAddress(artemisDeployableClient, address);
    }

    @Test
    @Disabled("Some AMQP discrepancy on user logging")
    void checkAmqpAuditLogsTest() {
        String address = "myAddress";
        String queue = "myQueue";
        Path auditLogPath = Path.of(getTestConfigDir(), artemisInstance.getName(), ArtemisConstants.LOG_DIR, ArtemisConstants.AUDIT_LOG_FILE);
        String adminFormattedAuth = String.format(ArtemisConstants.LOG_AUDIT_AUTHENTICATION_SUCC_PATTERN, ArtemisConstants.ADMIN_NAME, ArtemisConstants.ROLE_ADMIN);
        String adminFormattedAddress = String.format(ArtemisConstants.LOG_AUDIT_CREATE_ADDRESS_PATTERN_AMQP, ArtemisConstants.ADMIN_NAME, ArtemisConstants.ROLE_ADMIN, address, queue);
        String adminFormattedQueue = String.format(ArtemisConstants.LOG_AUDIT_CREATE_QUEUE_PATTERN, ArtemisConstants.ADMIN_NAME, ArtemisConstants.ROLE_ADMIN, queue, address);
        String adminFormattedSent = String.format(ArtemisConstants.LOG_AUDIT_SENT_MESSAGE_PATTERN, ArtemisConstants.ADMIN_NAME, ArtemisConstants.ROLE_ADMIN, address, queue);
        String adminFormattedRecv = String.format(ArtemisConstants.LOG_AUDIT_RECEIVED_MESSAGE_PATTERN, ArtemisConstants.ADMIN_NAME, ArtemisConstants.ROLE_ADMIN, queue);

        String charlieFormattedAuth = String.format(ArtemisConstants.LOG_AUDIT_AUTHENTICATION_SUCC_PATTERN, ArtemisConstants.CHARLIE_NAME, ArtemisConstants.ROLE_SENDERS);
        String charlieFormattedSent = String.format(ArtemisConstants.LOG_AUDIT_SENT_MESSAGE_PATTERN, ArtemisConstants.CHARLIE_NAME, ArtemisConstants.ROLE_SENDERS, address, queue);
        String charlieFormattedRecv = String.format(ArtemisConstants.LOG_AUDIT_RECEIVED_MESSAGE_PATTERN, ArtemisConstants.CHARLIE_NAME, ArtemisConstants.ROLE_SENDERS, queue);

        String aliceFormattedAuth = String.format(ArtemisConstants.LOG_AUDIT_AUTHENTICATION_SUCC_PATTERN, ArtemisConstants.ALICE_NAME, ArtemisConstants.ROLE_SENDERS);
        String aliceFormattedSent = String.format(ArtemisConstants.LOG_AUDIT_SENT_MESSAGE_PATTERN, ArtemisConstants.ALICE_NAME, ArtemisConstants.ROLE_SENDERS, address, queue);
        String aliceFormattedRecv = String.format(ArtemisConstants.LOG_AUDIT_AUTHENTICATION_FAIL_CONSUME_PATTERN, ArtemisConstants.ALICE_NAME, ArtemisConstants.ROLE_SENDERS, ArtemisConstants.ALICE_NAME, queue, address);

        String bobFormattedAuth = String.format(ArtemisConstants.LOG_AUDIT_AUTHENTICATION_SUCC_PATTERN, ArtemisConstants.BOB_NAME, ArtemisConstants.ROLE_RECEIVERS);
        String bobFormattedSent = String.format(ArtemisConstants.LOG_AUDIT_RECEIVED_MESSAGE_PATTERN, ArtemisConstants.BOB_NAME, ArtemisConstants.ROLE_RECEIVERS, queue);
        String bobFormattedRecv = String.format(ArtemisConstants.LOG_AUDIT_AUTHENTICATION_FAIL_PRODUCE_PATTERN, ArtemisConstants.BOB_NAME, ArtemisConstants.ROLE_RECEIVERS, ArtemisConstants.BOB_NAME, address);

        LOGGER.info("Test Bundled Core Messaging");
        int msgsExpected = 5;
        BundledClientOptions options = new BundledClientOptions()
                .withDeployableClient(artemisDeployableClient)
                .withDestinationAddress(address)
                .withDestinationPort(DEFAULT_ALL_PORT)
                .withMessageCount(msgsExpected)
                .withPassword(ArtemisConstants.ADMIN_NAME)
                .withUsername(ArtemisConstants.ADMIN_PASS)
                .withDestinationQueue(queue)
                .withDestinationUrl(artemisInstance.getName());
        MessagingClient bundledClient = new BundledAmqpMessagingClient(options);
        int sent = bundledClient.sendMessages();
        int received = bundledClient.receiveMessages();
        MatcherAssert.assertThat(sent, equalTo(msgsExpected));
        MatcherAssert.assertThat(received, equalTo(msgsExpected));

        String auditLog = TestUtils.readFileContent(auditLogPath.toFile());
        LOGGER.warn("auditLog: {}", auditLog);
        //       AMQ601262: User admin(amq)@192.168.32.2:34798 is creating address on target resource: 371c8f14-5e35-11ee-bfdb-0242c0a82002 with parameters: [Address [name=myAddress, id=0, routingTypes={ANYCAST}, autoCreated=false, paused=false, bindingRemovedTimestamp=-1, swept=false, createdTimestamp=1695929314633], true]
        //   ".* AMQ601262: User admin\(amq\)@.* is creating address on target resource: .* with parameters: \[myAddress::myQueue.*"
        assertThat(auditLog).containsPattern(adminFormattedAuth);
        assertThat(auditLog).containsPattern(adminFormattedAddress);
        assertThat(auditLog).containsPattern(adminFormattedQueue);
        assertThat(auditLog).containsPattern(adminFormattedSent);
        assertThat(auditLog).containsPattern(adminFormattedRecv);

        options = new BundledClientOptions()
                .withDeployableClient(artemisDeployableClient)
                .withDestinationAddress(address)
                .withDestinationPort(DEFAULT_ALL_PORT)
                .withMessageCount(msgsExpected)
                .withPassword(ArtemisConstants.CHARLIE_NAME)
                .withUsername(ArtemisConstants.CHARLIE_PASS)
                .withDestinationQueue(queue)
                .withDestinationUrl(artemisInstance.getName());
        bundledClient = new BundledAmqpMessagingClient(options);
        sent = bundledClient.sendMessages();
        received = bundledClient.receiveMessages();
        MatcherAssert.assertThat(sent, equalTo(msgsExpected));
        MatcherAssert.assertThat(received, equalTo(msgsExpected));

        auditLog = TestUtils.readFileContent(auditLogPath.toFile());
        assertThat(auditLog).containsPattern(charlieFormattedAuth);
        assertThat(auditLog).containsPattern(charlieFormattedSent);
        assertThat(auditLog).containsPattern(charlieFormattedRecv);

        options = new BundledClientOptions()
                .withDeployableClient(artemisDeployableClient)
                .withDestinationAddress(address)
                .withDestinationPort(DEFAULT_ALL_PORT)
                .withMessageCount(msgsExpected)
                .withPassword(ArtemisConstants.ALICE_NAME)
                .withUsername(ArtemisConstants.ALICE_PASS)
                .withDestinationQueue(queue)
                .withDestinationUrl(artemisInstance.getName());
        BundledAmqpMessagingClient bundledClientAlice = new BundledAmqpMessagingClient(options);
        int sentAlice = bundledClientAlice.sendMessages();
        assertThrows(MessagingClientException.class, bundledClientAlice::receiveMessages);

        auditLog = TestUtils.readFileContent(auditLogPath.toFile());
        LOGGER.warn("auditLog: {}", auditLog);
        assertThat(auditLog).containsPattern(aliceFormattedAuth);
        assertThat(auditLog).containsPattern(aliceFormattedSent);
        assertThat(auditLog).containsPattern(aliceFormattedRecv);

        options = new BundledClientOptions()
                .withDeployableClient(artemisDeployableClient)
                .withDestinationAddress(address)
                .withDestinationPort(DEFAULT_ALL_PORT)
                .withMessageCount(msgsExpected)
                .withPassword(ArtemisConstants.BOB_NAME)
                .withUsername(ArtemisConstants.BOB_PASS)
                .withDestinationQueue(queue)
                .withDestinationUrl(artemisInstance.getName());
        BundledAmqpMessagingClient bundledClientBob = new BundledAmqpMessagingClient(options);

        assertThrows(MessagingClientException.class, bundledClientBob::sendMessages);
        int receivedBob = bundledClientBob.receiveMessages();
        MatcherAssert.assertThat(sentAlice, equalTo(msgsExpected));
        MatcherAssert.assertThat(receivedBob, equalTo(msgsExpected));

        auditLog = TestUtils.readFileContent(auditLogPath.toFile());
        LOGGER.warn("auditLog: {}", auditLog);
        assertThat(auditLog).containsPattern(bobFormattedAuth);
        assertThat(auditLog).containsPattern(bobFormattedSent);
        assertThat(auditLog).containsPattern(bobFormattedRecv);
        deleteQueue(artemisDeployableClient, queue);
        deleteAddress(artemisDeployableClient, address);
    }

}
