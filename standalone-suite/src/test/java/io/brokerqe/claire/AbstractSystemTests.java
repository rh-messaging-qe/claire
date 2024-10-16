/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire;

import io.brokerqe.claire.client.deployment.StJavaClientDeployment;
import io.brokerqe.claire.clients.DeployableClient;
import io.brokerqe.claire.clients.MessagingClient;
import io.brokerqe.claire.clients.Protocol;
import io.brokerqe.claire.clients.bundled.ArtemisCommand;
import io.brokerqe.claire.clients.bundled.BundledAmqpMessagingClient;
import io.brokerqe.claire.clients.bundled.BundledArtemisClient;
import io.brokerqe.claire.clients.bundled.BundledClientOptions;
import io.brokerqe.claire.clients.bundled.BundledCoreMessagingClient;
import io.brokerqe.claire.clients.container.AmqpQpidClient;
import io.brokerqe.claire.container.ArtemisContainer;
import io.brokerqe.claire.exception.ClaireRuntimeException;
import io.brokerqe.claire.junit.TestSeparator;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.commons.io.FileUtils;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(StandaloneTestDataCollector.class)
public class AbstractSystemTests implements TestSeparator {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractSystemTests.class);
    protected static final String DEFAULT_ALL_PORT = String.valueOf(ArtemisConstants.DEFAULT_ALL_PROTOCOLS_PORT);
    protected static final String DEFAULT_AMQP_PORT = String.valueOf(ArtemisConstants.DEFAULT_AMQP_PORT);
    protected TestInfo testInfo;

    @BeforeAll
    public void setupTestEnvironment() {
        if (getEnvironment().getJdbcDatabaseFile() != null) {
            getEnvironment().setupDatabase();
        }
        init(testInfo);
    }

    @AfterAll
    public void tearDownTestEnvironment() {
        ResourceManager.disconnectAllClients();
        ResourceManager.stopAllContainers();
        Path testCfgDir = Paths.get(getTestConfigDir());
        try {
            FileUtils.deleteDirectory(testCfgDir.toFile());
        } catch (IOException e) {
            String errMsg = String.format("Error on deleting directory %s: %s", testCfgDir, e.getMessage());
            throw new ClaireRuntimeException(errMsg, e);
        }
    }

    @BeforeEach
    void init(TestInfo testInfo) {
        this.testInfo = testInfo;
        EnvironmentStandalone.getInstance().setPackageClassDir(getPkgClassAsDir());
        EnvironmentStandalone.getInstance().setTestConfigDir(getTestConfigDir());
        EnvironmentStandalone.getInstance().setTestTempDir(getTestTempDir());
    }

    /**
     * Returns true, if actualTestVersion is equal or newer than provided versionValidSince.
     * @param minimumTestVersion test code applicable from/for this given version
     * @return true if is valid version (equal or newer to provided testVersionValidSince)
     */
    public boolean isMinimumTestVersion(ArtemisVersion minimumTestVersion) {
        return getEnvironment().getArtemisTestVersion().getVersionNumber() >= minimumTestVersion.getVersionNumber();
    }

    public String getTestRandomName() {
        // Call this method directly from testMethod to work https://stackoverflow.com/a/34948763/2604720
        return Thread.currentThread().getStackTrace()[2].getMethodName() + "-" + TestUtils.generateRandomName();
    }

    protected Environment getEnvironment() {
        return EnvironmentStandalone.getInstance();
    }

    protected String getPkgClassAsDir() {
        String pkgAndClass = this.getClass().getName().replaceAll(Constants.CLAIRE_TEST_PKG_REGEX, "");
        pkgAndClass = pkgAndClass.replaceAll("\\.", Constants.FILE_SEPARATOR);
        return pkgAndClass;
    }

    protected String getTestConfigDir() {
        String cfgDir = getPkgClassAsDir();
        return TestUtils.getProjectRelativeFile(Constants.ARTEMIS_TEST_CFG_DIR + Constants.FILE_SEPARATOR + cfgDir);
    }

    protected String getTestTempDir() {
        return getEnvironment().getTmpDirLocation() + Constants.FILE_SEPARATOR + getPkgClassAsDir();
    }

    protected String getSecuredConsoleTemplate() {
        return """
                boostrap_xml_bindings:
                  - name: 'artemis'
                    uri: https://0.0.0.0:8161
                    sniHostCheck: "false"
                    sniRequired: "false"
                    clientAuth: "false"
                    keyStorePath: %s
                    keyStorePassword: brokerPass
                    trustStorePath: %s
                    trustStorePassword: brokerPass
                """;
    }

    public static void ensureSameMessages(int totalProducedMessages, Map<String, Message> producedMsgs, Map<String, Message> consumedMsgs) {
        // ensure produced messages number is correct
        assertThat(producedMsgs).isNotEmpty().hasSize(totalProducedMessages);

        // ensure consumed messages number is correct
        assertThat(consumedMsgs).isNotEmpty().hasSize(totalProducedMessages);

        // ensure all produced messages are consumed and contains the same content
        for (Map.Entry<String, Message> entry : producedMsgs.entrySet()) {
            String msgId = entry.getKey();
            if (TextMessage.class.isAssignableFrom(entry.getValue().getClass())
                    && TextMessage.class.isAssignableFrom(consumedMsgs.get(msgId).getClass())) {
                TextMessage v = (TextMessage) entry.getValue();
                try {
                    TextMessage consumedMsg = (TextMessage) consumedMsgs.get(msgId);
                    assertThat(consumedMsg.getText()).contains(v.getText());
                } catch (JMSException e) {
                    String errMsg = String.format("error on getting message information: %s", e.getMessage());
                    LOGGER.error(errMsg);
                    throw new ClaireRuntimeException(errMsg, e);
                }
            } else {
                String errMsg = "Tried to process unsupported type of message";
                LOGGER.error(errMsg);
                throw new ClaireRuntimeException(errMsg);
            }
        }
    }

    // ==== Messaging methods
    protected void deleteAddress(DeployableClient artemisDeployableClient, String name) {
        BundledArtemisClient artemisClient = new BundledArtemisClient(artemisDeployableClient, ArtemisCommand.ADDRESS_DELETE, Map.of("name", name));
        artemisClient.executeCommand();
    }

    protected void deleteQueue(DeployableClient artemisDeployableClient, String name) {
        BundledArtemisClient artemisClient = new BundledArtemisClient(artemisDeployableClient, ArtemisCommand.QUEUE_DELETE, Map.of("name", name));
        artemisClient.executeCommand();
    }

    public void createAddress(DeployableClient artemisDeployableClient, String name) {
        BundledArtemisClient artemisClient = new BundledArtemisClient(artemisDeployableClient, ArtemisCommand.ADDRESS_CREATE, Map.of("name", name));
        artemisClient.executeCommand();
    }

    public void createQueue(ArtemisContainer artemis, Map<String, String> artemisCreateQueueOptions) {
        createQueue(artemis.getDeployableClient(), artemisCreateQueueOptions, ArtemisConstants.ADMIN_NAME, ArtemisConstants.ADMIN_PASS);
    }

    public void createQueue(DeployableClient artemisDeployableClient, Map<String, String> artemisCreateQueueOptions, String username, String password) {
        BundledArtemisClient artemisClient = new BundledArtemisClient(artemisDeployableClient, ArtemisCommand.QUEUE_CREATE, username, password, artemisCreateQueueOptions);
        artemisClient.executeCommand();
    }

    public MessagingClient createMessagingClient(BundledClientOptions options) {
        MessagingClient messagingClient;

        if (options.getProtocol().toString().startsWith(Protocol.AMQP.name())) {
            options.getDeployableClient().getContainer();
            messagingClient = new BundledAmqpMessagingClient(options);
        } else {
            messagingClient = new BundledCoreMessagingClient(options);
        }
        return messagingClient;
    }

    public int sendMessages(BundledClientOptions options) {
        return sendMessages(null, options);
    }

    public int sendMessages(MessagingClient messagingClient, BundledClientOptions options) {
        if (messagingClient == null) {
            messagingClient = createMessagingClient(options);
        }
        LOGGER.info("[{}] Sending {} messages to {}.", options.getDeployableClient().getContainerName(), options.getMessageCount(), options.getDestinationQueue());
        return messagingClient.sendMessages();
    }

    public int receiveMessages(BundledClientOptions options) {
        return receiveMessages(null, options);
    }

    public int receiveMessages(MessagingClient messagingClient, BundledClientOptions options) {
        if (messagingClient == null) {
            messagingClient = createMessagingClient(options);
        }
        LOGGER.info("[{}] Receiving {} messages to {}.", options.getDeployableClient().getContainerName(), options.getMessageCount(), options.getDestinationQueue());
        return messagingClient.receiveMessages();
    }

    public void sendReceiveDurableMsgQueue(ArtemisContainer artemis, BundledClientOptions senderOptions, BundledClientOptions receiverOptions) {
        Map<String, String> artemisCreateQueueOptions = new HashMap<>(Map.of(
                "name", senderOptions.getDestinationQueue(),
                "address", senderOptions.getDestinationAddress(),
                ArtemisConstants.ROUTING_TYPE_ANYCAST, "",
                "durable", "",
                "auto-create-address", "",
                "preserve-on-no-consumers", ""
        ));
        Map<String, String> artemisDeleteQueueOptions = new HashMap<>(Map.of(
                "name", senderOptions.getDestinationAddress()
        ));

        BundledArtemisClient artemisClient = new BundledArtemisClient(artemis.getDeployableClient(), ArtemisCommand.QUEUE_CREATE, ArtemisConstants.ADMIN_NAME, ArtemisConstants.ADMIN_PASS, artemisCreateQueueOptions);
        artemisClient.executeCommand();

        MessagingClient sender = createMessagingClient(senderOptions);
        int sent = sendMessages(sender, senderOptions);

        MessagingClient receiver = createMessagingClient(receiverOptions);
        int received = receiveMessages(receiver, senderOptions);
        MatcherAssert.assertThat(received, equalTo(sent));

        artemisClient = new BundledArtemisClient(artemis.getDeployableClient(), ArtemisCommand.QUEUE_DELETE, artemisDeleteQueueOptions);
        artemisClient.executeCommand();
    }

    public void produceAndConsumeOnPrimaryAndOnBackupTest(ArtemisContainer.ArtemisProcessControllerAction stopAction,
                                                          ArtemisContainer artemisPrimary, ArtemisContainer artemisBackup) {
        LOGGER.info("Setting client configurations");
        DeployableClient stDeployableClient = new StJavaClientDeployment();
        int sendMessages = 10;
        int receiveMessages = 5;

        String addressName = getTestRandomName();
        Map<String, String> senderOpts = new HashMap<>(Map.of(
                "conn-username", ArtemisConstants.ADMIN_NAME,
                "conn-password", ArtemisConstants.ADMIN_PASS,
                "address", addressName,
                "count", String.valueOf(sendMessages)
        ));
        Map<String, String> receiverOpts = new HashMap<>(Map.of(
                "conn-username", ArtemisConstants.ADMIN_NAME,
                "conn-password", ArtemisConstants.ADMIN_PASS,
                "address", addressName,
                "count", String.valueOf(receiveMessages)
        ));
        String primaryAmqpHostAndPort = artemisPrimary.getInstanceNameAndPort(ArtemisConstants.DEFAULT_ALL_PROTOCOLS_PORT);
        String backupAmqpHostAndPort = artemisBackup.getInstanceNameAndPort(ArtemisConstants.DEFAULT_ALL_PROTOCOLS_PORT);
        MessagingClient primaryMessagingClient = new AmqpQpidClient(stDeployableClient, primaryAmqpHostAndPort, senderOpts, receiverOpts);
        MessagingClient backupMessagingClient = new AmqpQpidClient(stDeployableClient, backupAmqpHostAndPort, senderOpts, receiverOpts);

        LOGGER.info("Sending {} messages to broker primary {}", sendMessages, artemisPrimary.getName());
        int sent = primaryMessagingClient.sendMessages();

        LOGGER.info("Receiving {} messages from broker primary {}", receiveMessages, artemisPrimary.getName());
        int received = primaryMessagingClient.receiveMessages();

        artemisPrimary.artemisProcessController(stopAction);
        artemisBackup.ensureBrokerIsActive();

        LOGGER.info("Sending {} messages to broker backup {}", sendMessages, artemisBackup.getName());
        sent += backupMessagingClient.sendMessages();

        LOGGER.info("Receiving {} messages from broker backup {}", receiveMessages, artemisBackup.getName());
        received += backupMessagingClient.receiveMessages();

        artemisPrimary.artemisProcessController(ArtemisContainer.ArtemisProcessControllerAction.START);
        artemisPrimary.ensureBrokerIsActive();

        LOGGER.info("Receiving {} messages from broker primary {}", receiveMessages, artemisPrimary.getName());
        received += primaryMessagingClient.receiveMessages();
        LOGGER.info("Receiving {} messages from broker primary {}", receiveMessages, artemisPrimary.getName());
        received += primaryMessagingClient.receiveMessages();

        LOGGER.info("Ensure broker number of sent messages are equal received ones");
        MatcherAssert.assertThat(sent, equalTo(received));

        artemisPrimary.ensureQueueCount(addressName, addressName, RoutingType.ANYCAST, 0);
    }

    public Duration calculateArtemisStartupTimeout(int size, String unit, int messageCount) {
        int seconds = messageCount;
        if (size != 0) {
            seconds *= size;
        }
        if (unit != null) {
            switch (unit) {
                case "KiB" -> seconds *= 0.5;
                case "MiB" -> seconds *= 15;
                case "GiB" -> seconds *= 100;
            }
        }
        return Duration.ofSeconds(seconds);
    }

    // common assertion methods
    public void assertContains(String log, String text) {
        Assertions.assertTrue(log.contains(text));
    }

    public void assertVersionLogs(ArtemisContainer artemis, String version, String artemisVersion) {
        // Red Hat AMQ Broker 7.11.7.GA
        // AMQ101000: Starting ActiveMQ Artemis Server version 2.28.0.redhat-00022
        String brokerVersionOldString = ArtemisConstants.getArtemisVersionString(version);
        String brokerVersionNewString = ArtemisConstants.getArtemisVersionStringOld(version);

        LOGGER.info("[UPGRADE][{}] Checking for correct versions in logs {}, {}", artemis.getName(), version, artemisVersion);
        MatcherAssert.assertThat(artemis.getLogs(), anyOf(containsString(brokerVersionOldString), containsString(brokerVersionNewString)));
        MatcherAssert.assertThat(artemis.getLogs(), containsString(ArtemisConstants.getArtemisStartingServerVersionString(artemisVersion)));
    }
}
