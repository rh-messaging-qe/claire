/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.container;

import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.sun.security.auth.module.UnixSystem;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.client.deployment.ArtemisConfigData;
import io.brokerqe.claire.client.deployment.BundledClientDeployment;
import io.brokerqe.claire.clients.DeployableClient;
import io.brokerqe.claire.clients.MessagingClient;
import io.brokerqe.claire.clients.Protocol;
import io.brokerqe.claire.clients.bundled.BundledClientOptions;
import io.brokerqe.claire.clients.bundled.BundledCoreMessagingClient;
import io.brokerqe.claire.clients.container.AmqpQpidClient;
import io.brokerqe.claire.database.Database;
import io.brokerqe.claire.exception.ClaireRuntimeException;
import io.brokerqe.claire.helper.ArtemisJmxHelper;
import io.brokerqe.claire.helper.TimeHelper;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.output.FrameConsumerResultCallback;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.ToStringConsumer;
import org.testcontainers.shaded.com.google.common.primitives.Ints;

import java.io.File;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public final class ArtemisContainer extends AbstractGenericContainer {

    public enum ArtemisProcessControllerAction {
        START, STOP, FORCE_STOP
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ArtemisContainer.class);

    public static final String BACKUP_ANNOUNCED_LOG_REGEX = ".*AMQ221031: backup announced\\n";
    public static final String PRIMARY_OBTAINED_LOCK_REGEX = ".*AMQ221035: Primary Server Obtained primary lock\\n";
    public static final String PRIMARY_LIVE_LOG_REGEX = ".*AMQ221007: Server is now (live|active)\\n";
    public static final List<Integer> DEFAULT_PORTS = List.of(ArtemisConstants.DEFAULT_ALL_PROTOCOLS_PORT, ArtemisConstants.DEFAULT_AMQP_PORT,
            ArtemisConstants.DEFAULT_MQTT_PORT, ArtemisConstants.DEFAULT_STOMP_PORT, ArtemisConstants.DEFAULT_HORNETQ_PORT, ArtemisConstants.DEFAULT_WEB_CONSOLE_PORT, ArtemisConstants.DEFAULT_JMX_PORT);
    public static final String ARTEMIS_INSTALL_DIR = ArtemisConstants.OPT_DIR + Constants.FILE_SEPARATOR + ArtemisConstants.ARTEMIS_STRING;
    public static final String ARTEMIS_INSTANCE_DIR = ArtemisConstants.VAR_DIR + ArtemisConstants.LIB_DIR + Constants.FILE_SEPARATOR + ArtemisConstants.INSTANCE_STRING;
    public static final String ARTEMIS_INSTANCE_ETC_DIR = ARTEMIS_INSTANCE_DIR + ArtemisConstants.ETC_DIR;
    public static final String ARTEMIS_INSTANCE_DATA_DIR = ARTEMIS_INSTANCE_DIR + ArtemisConstants.DATA_DIR;
    public static final String ARTEMIS_TUNE_LOGS_DEBUG_FILE = YacfgArtemisContainer.CLAIRE_STANDALONE_YACFG_PROFILES + Constants.FILE_SEPARATOR + "logs" + Constants.FILE_SEPARATOR + "tune-debug-logs.yaml";
    public static final String ARTEMIS_TUNE_LOGS_DEBUG_ALL_FILE = YacfgArtemisContainer.CLAIRE_STANDALONE_YACFG_PROFILES + Constants.FILE_SEPARATOR + "logs" + Constants.FILE_SEPARATOR + "tune-debug-all.yaml";
    private static final String ARTEMIS_INSTANCE_CONTROLLER_CMD = "/usr/local/bin/artemis-controller.sh";

    private boolean secured = false;
    private String installDir;
    private String instanceDir;
    private String configBinDir;
    private String configDir;
    private String libDir;
    private DeployableClient deployableClient;
    private boolean isPrimary = false;
    private boolean isBackup = false;
    private boolean isActive = false;
    private ArtemisConfigData artemisConfigData;
    private boolean isContainerNameUsable = false;
    private boolean containerNameConnectionTested = false;

    public ArtemisContainer(String name) {
        super(name, ENVIRONMENT_STANDALONE.getArtemisContainerImage());
        type = ContainerType.ARTEMIS;
        deployableClient = new BundledClientDeployment();
        deployableClient.setContainer(this.getGenericContainer());
    }

    public String getDefaultBrokerUri() {
        return getBrokerUri(Protocol.CORE, 61616);
    }

    public String getBrokerUri(Protocol protocol) {
        switch (protocol) {
            case AMQP -> {
                return getBrokerUri(Protocol.AMQP, ArtemisConstants.DEFAULT_AMQP_PORT);
            }
            case AMQPS -> {
                return getBrokerUri(Protocol.AMQP, 5673);
            }
            case CORE -> {
                return getBrokerUri(Protocol.CORE, ArtemisConstants.DEFAULT_ALL_PROTOCOLS_PORT);
            }
            case CORES -> {
                return getBrokerUri(Protocol.CORE, 61617);
            }
            default -> {
                return getBrokerUri(Protocol.CORE, ArtemisConstants.DEFAULT_ALL_PROTOCOLS_PORT);
            }
        }
    }

    public String getBrokerUri(Protocol protocol, int port) {
        String brokerUri;
        switch (protocol) {
            case AMQP -> brokerUri = Constants.AMQP_URL_PREFIX.toLowerCase(Locale.ROOT) + "%s";
            case CORE -> brokerUri = Constants.TCP_URL_PREFIX.toLowerCase(Locale.ROOT) + "%s";
            default -> throw new IllegalArgumentException("Unknown port!");
        }

        brokerUri = getBrokerHostUri(brokerUri, protocol);
        // TODO use port as well? - currently no, as all client expect port as separate argument
//        if (port != null) {
//            brokerUri = String.format(brokerUri, getInstanceNameAndPort(port));
//        }
        return brokerUri;
    }

    /**
     * Test valid broker uri - hostname vs IP usage in broker uri.
     * Test using Core/AMQP client.
     *
     * @param brokerUri
     * @param protocol
     * @return
     */
    private String getBrokerHostUri(String brokerUri, Protocol protocol) {
        String brokerUriName = String.format(brokerUri, getName());
        String brokerUriAddress = String.format(brokerUri, getContainerIpAddress());

        if (containerNameConnectionTested) {
            if (isContainerNameUsable) {
                return brokerUriName;
            } else {
                return brokerUriAddress;
            }
        }

        try {
            MessagingClient messagingTestClient;
            String testAddress = "internaltestAddress";
            if (protocol == Protocol.AMQP) {
                LOGGER.info("Checking to use artemis container name in brokerURI.");
                Map<String, String> clientOptions = Map.of(
                        "conn-username", ArtemisConstants.ADMIN_NAME,
                        "conn-password", ArtemisConstants.ADMIN_PASS,
                        "address", testAddress,
                        "count", "0"
                );

                messagingTestClient = new AmqpQpidClient(deployableClient, brokerUriName + ":" + ArtemisConstants.DEFAULT_ALL_PROTOCOLS_PORT, clientOptions, clientOptions);
                // should we use new StJavaDeployment instead?
//                messagingTestClient = new AmqpQpidClient(new StJavaClientDeployment(), brokerUriName + ":" + ArtemisConstants.DEFAULT_ALL_PROTOCOLS_PORT, clientOptions, clientOptions);
            } else {
                BundledClientOptions options = new BundledClientOptions()
                        .withDeployableClient(deployableClient)
                        .withDestinationAddress(testAddress)
                        .withDestinationPort(String.valueOf(ArtemisConstants.DEFAULT_ALL_PROTOCOLS_PORT))
                        .withMessageCount(0)
                        .withPassword(ArtemisConstants.ADMIN_NAME)
                        .withUsername(ArtemisConstants.ADMIN_PASS)
                        .withDestinationQueue(testAddress)
                        .withDestinationUrl(name);
                messagingTestClient = new BundledCoreMessagingClient(options);
            }

            messagingTestClient.sendMessages();

            containerNameConnectionTested = true;
            isContainerNameUsable = true;
            return brokerUriName;
        } catch (Exception e) {
            LOGGER.warn("Name resolution failed. Using container IP address in brokerURI instead of name.");
            isContainerNameUsable = false;
            containerNameConnectionTested = true;
            return brokerUriAddress;
        }
    }


    public ArtemisConfigData getArtemisConfigData() {
        return artemisConfigData;
    }

    public void setArtemisData(ArtemisConfigData artemisConfigData) {
        this.artemisConfigData = artemisConfigData;
    }

    public String getInstallDir() {
        return installDir != null ? installDir : TestUtils.getProjectRelativeFile(ArtemisConstants.INSTALL_DIR);
    }

    public void setInstallDir(String installDir) {
        this.installDir = installDir;
        setConfigLibDir(installDir + Constants.FILE_SEPARATOR + ArtemisConstants.LIB_DIR);
    }

    public void setInstanceDir(String instanceDir) {
        this.instanceDir = instanceDir;
    }

    public String getInstanceDir() {
        return instanceDir;
    }

    public void setConfigDir(String configDir) {
        this.configDir = configDir;
    }

    public String getConfigBinDir() {
        return configBinDir != null ? configBinDir : TestUtils.getProjectRelativeFile(Constants.ARTEMIS_DEFAULT_CFG_BIN_DIR);
    }

    public void setConfigBinDir(String configBinDir) {
        this.configBinDir = configBinDir;
    }

    public String getConfigLibDir() {
        return libDir != null ? libDir : TestUtils.getProjectRelativeFile(Constants.ARTEMIS_DEFAULT_CFG_LIB_DIR);
    }

    public void setConfigLibDir(String libDir) {
        this.libDir = libDir;
    }

    public String getArtemisJavaHomeDir() {
        return ENVIRONMENT_STANDALONE.getArtemisContainerJavaHome();
    }

    public void withInstallDir(String dirPath) {
        withInstallDir(dirPath, false);
    }

    public void withInstallDir(String dirPath, boolean replaceBind) {
        LOGGER.debug("[{}] with install dir {} = {}", name, dirPath, ARTEMIS_INSTALL_DIR);
        withFileSystemBind(dirPath, ARTEMIS_INSTALL_DIR, BindMode.READ_ONLY, replaceBind);
    }

    public void withConfigDir(String dirPath) {
        String containerConfigDir = ARTEMIS_INSTANCE_DIR + ArtemisConstants.ETC_DIR;
        LOGGER.debug("[{}] with config dir {} = {}", name, dirPath, containerConfigDir);
        setConfigDir(dirPath);
        withFileSystemBind(dirPath, containerConfigDir, BindMode.READ_ONLY);
    }

    public void withInstanceDir(String dirPath) {
        String containerInstanceDir = ARTEMIS_INSTANCE_DIR;
        setInstanceDir(dirPath);
        LOGGER.debug("[{}] with instance dir {} = {}", name, dirPath, containerInstanceDir);
        withFileSystemBind(dirPath, containerInstanceDir, BindMode.READ_WRITE);
    }

    public void withConfigFile(String srcFilePath, String dstFileName) {
        String destination = ARTEMIS_INSTANCE_DIR + ArtemisConstants.ETC_DIR + File.separator + dstFileName;
        LOGGER.debug("[{}] with config file {} = {}", name, srcFilePath, destination);
        withFileSystemBind(srcFilePath, destination, BindMode.READ_ONLY);
    }

    public void withDataDir(String dirPath) {
        LOGGER.debug("[{}] with data dir {} = {}", name, dirPath, ARTEMIS_INSTANCE_DATA_DIR);
        TestUtils.createDirectory(dirPath);
        withFileSystemBind(dirPath, ARTEMIS_INSTANCE_DATA_DIR, BindMode.READ_WRITE);
    }

    public void withJavaHome(String dirPath) {
        LOGGER.debug("[{}] with env var {} = {}", name, Constants.JAVA_HOME, dirPath);
        container.withEnv(Constants.JAVA_HOME, dirPath);
    }

    public void withLibFile(String srcFilePath, String dstFileName) {
        String destination = ARTEMIS_INSTANCE_DIR + ArtemisConstants.LIB_DIR + File.separator + dstFileName;
        LOGGER.debug("[{}] with lib file {} = {}", name, srcFilePath, destination);
        withFileSystemBind(srcFilePath, destination, BindMode.READ_ONLY);
    }

    public void start() {
        start(Duration.ofMinutes(1));
    }

    public void start(Duration startupTimeout) {
        LOGGER.info("[{}] About to start", name);
        LOGGER.debug("[{}] Using exposed ports: {}", name, DEFAULT_PORTS);

        container.addExposedPorts(Ints.toArray(DEFAULT_PORTS));
        container.withPrivilegedMode(true);
        container.withStartupTimeout(startupTimeout);
        withJavaHome(getArtemisJavaHomeDir());
        withInstallDir(getInstallDir());
        withFileSystemBind(getConfigBinDir(), ARTEMIS_INSTANCE_DIR + ArtemisConstants.BIN_DIR, BindMode.READ_WRITE);
        withFileSystemBind(getConfigLibDir(), ARTEMIS_INSTANCE_DIR + ArtemisConstants.LIB_DIR, BindMode.READ_WRITE);
        long uid = new UnixSystem().getUid();
        long gid = new UnixSystem().getGid();
        withEnvVar(Map.of("ARTEMIS_GROUP_GID", String.valueOf(gid), "ARTEMIS_USER_UID", String.valueOf(uid)));
        super.start();
    }

    public String getConsoleUrl() {
        if (secured) {
            return getHttpConsoleUrl(false, true);
        } else {
            return getHttpConsoleUrl(false, false);
        }
    }

    public String getHttpConsoleUrl(boolean useLocalhost, boolean useHttps) {
        String url = (useHttps ? Constants.HTTPS : Constants.HTTP) + "://";
        if (useLocalhost) {
            return url + "localhost:" + getGenericContainer().getMappedPort(ArtemisConstants.DEFAULT_WEB_CONSOLE_PORT) + "/" + ArtemisConstants.CONSOLE_STRING;
        } else {
            return url + this.getContainerIpAddress() + ":" + ArtemisConstants.DEFAULT_WEB_CONSOLE_PORT + "/" + ArtemisConstants.CONSOLE_STRING;
        }
    }

    public String getLoginUrl() {
        if (getArtemisConfigData().getArtemisTestVersion().getVersionNumber() > ArtemisVersion.VERSION_2_38.getVersionNumber()) {
            return getConsoleUrl() + "/" + ArtemisConstants.LOGIN_STRING;
        } else {
            return getConsoleUrl() + "/" + ArtemisConstants.AUTH_STRING + "/" + ArtemisConstants.LOGIN_STRING;
        }
    }

    public String getLoggedInUrl() {
        if (getArtemisConfigData().getArtemisTestVersion().getVersionNumber() > ArtemisVersion.VERSION_2_38.getVersionNumber()) {
            return getConsoleUrl() + "/" + ArtemisConstants.ARTEMIS_STRING;
        } else {
            return getConsoleUrl() + "/" + ArtemisConstants.ARTEMIS_STRING
                    + "/artemisStatus?nid=root-org.apache.activemq.artemis-" + this.getName();
        }
    }

    @Override
    public void stop() {
        LOGGER.debug("[{}] Stopping", name);
        if (container.isRunning()) {
            dockerClient.stopContainerCmd(container.getContainerId()).exec();
            TimeHelper.waitFor(e -> !container.isRunning(), Constants.DURATION_500_MILLISECONDS, Constants.DURATION_5_SECONDS);
        }
        container.stop();
    }

    public String artemisProcessController(ArtemisProcessControllerAction action) {
        String[] command = {ARTEMIS_INSTANCE_CONTROLLER_CMD, action.toString().toLowerCase(Locale.ROOT)};
        LOGGER.info("Executing artemis_controller with action {} on broker instance {}", action.toString().toLowerCase(Locale.ROOT), name);
        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(container.getContainerId())
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withUser("artemis")
                .withCmd(command)
                .exec();
        FrameConsumerResultCallback callback = new FrameConsumerResultCallback();
        ToStringConsumer stdoutConsumer = new ToStringConsumer();
        ToStringConsumer stderrConsumer = new ToStringConsumer();
        Throwable t = null;

        try {
            callback.addConsumer(OutputFrame.OutputType.STDOUT, stdoutConsumer);
            callback.addConsumer(OutputFrame.OutputType.STDERR, stderrConsumer);
            dockerClient.execStartCmd(execCreateCmdResponse.getId()).exec(callback).awaitCompletion();
            Long exitCode = dockerClient.inspectExecCmd(execCreateCmdResponse.getId()).exec().getExitCodeLong();

            if (exitCode != 0) {
                String errMsg = String.format("Error on executing command '%s' in container %s, return code: %s ",
                        String.join(" ", command), container.getContainerName(), exitCode);
                LOGGER.error("[ExecutorStandalone] {} {} ", errMsg, stderrConsumer.toString(Charset.defaultCharset()));
                throw new ClaireRuntimeException(errMsg);
            }
            return stdoutConsumer.toString(Charset.defaultCharset());
        } catch (Throwable t1) {
            String errMsg = String.format("Error on executing command '%s' in container %s: %s",
                    String.join(" ", command), container.getContainerName(), t1.getMessage());
            LOGGER.error(errMsg);
            t = t1;
            throw new ClaireRuntimeException(errMsg, t);
        } finally {
            try {
                callback.close();
            } catch (Throwable t2) {
                if (t != null) {
                    t.addSuppressed(t2);
                } else {
                    String errMsg = String.format("Error on executing command '%s' in container %s: %s",
                            String.join(" ", command), container.getContainerName(), t2.getMessage());
                    LOGGER.error(errMsg);
                    throw new ClaireRuntimeException(errMsg, t2);
                }
            }
        }
    }

    public void ensureBrokerStarted() {
        ensureBrokerStarted(true);
    }

    public void ensureBrokerStarted(boolean checkHaStatus) {
        boolean isStarted = ArtemisJmxHelper.isStarted(this, true, 30, Constants.DURATION_2_SECONDS);
        assertThat(isStarted).isTrue();
        if (checkHaStatus) {
            if (isPrimary()) {
                ensureBrokerIsActive();
            }
            if (isBackup()) {
                ensureBrokerIsBackup();
            }
        }
    }

    public void ensureBrokerIsActive() {
        ensureBrokerIsActive(30, Constants.DURATION_5_SECONDS);
    }

    public void ensureBrokerIsActive(long retries, long pollTime) {
        LOGGER.info("Ensure broker instance {} became the broker live", name);
        isActive = ArtemisJmxHelper.isActive(this, true, retries, pollTime);
        assertThat(isActive).isTrue();
    }

    public void ensureBrokerIsBackup() {
        boolean isBackup = ArtemisJmxHelper.isBackup(this, true, 40, Constants.DURATION_2_SECONDS);
        assertThat(isBackup).isTrue();
    }

    public void ensureBrokerReplicaIsInSync() {
        boolean isReplicaInSync = ArtemisJmxHelper.isReplicaInSync(this, true, 10, Constants.DURATION_2_SECONDS);
        assertThat(isReplicaInSync).isTrue();
    }

    public void ensureBrokerPagingCount(String addressName, int expectedResult) {
        long pagingCount = ArtemisJmxHelper.getAddressPageCount(this, addressName, expectedResult, 10, Constants.DURATION_2_SECONDS);
        assertThat(pagingCount).isEqualTo(expectedResult);
    }

    public void ensureBrokerIsPaging(String addressName, boolean expectedResult) {
        boolean isPaging = ArtemisJmxHelper.isPaging(this, addressName, expectedResult, 10, Constants.DURATION_2_SECONDS);
        assertThat(isPaging).isEqualTo(expectedResult);
    }

    public void ensureQueueCount(String addressName, String queueName, RoutingType routeType, int expectedResult) {
        LOGGER.info("Ensure queue has {} messages", expectedResult);
        Long countResult = ArtemisJmxHelper.getQueueCount(this, addressName, queueName, routeType, expectedResult, 10, Constants.DURATION_2_SECONDS);
        assertThat(countResult).isEqualTo(expectedResult);
    }

    public void ensureBrokerUsesJdbc(Database database) {
        assertThat(getLogs()).containsAnyOf(database.getJdbcUrl(), database.getConnectionUrl());
    }

    public boolean isPrimary() {
        return isPrimary;
    }

    public void setPrimary(boolean primary) {
        isPrimary = primary;
    }

    public void setBackup(boolean backup) {
        isBackup = backup;
    }

    public boolean isBackup() {
        return isBackup;
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }

    public boolean isActive() {
        return isActive();
    }

    public boolean isSecured() {
        return secured;
    }

    public void setSecured(boolean secured) {
        this.secured = secured;
    }

    public DeployableClient getDeployableClient() {
        return deployableClient;
    }

    // Use this method from outside - means some other method tested network connection for us.
    // That's why we set connectionTested as well
    public void setContainerNameUsable(boolean containerNameUsable) {
        isContainerNameUsable = containerNameUsable;
        setContainerNameConnectionTested(true);
    }

    public void setContainerNameConnectionTested(boolean containerNameConnectionTested) {
        this.containerNameConnectionTested = containerNameConnectionTested;
    }

    public int getMappedPort(int port) {
        return container.getMappedPort(port);
    }

}
