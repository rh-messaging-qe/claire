/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.container;

import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.sun.security.auth.module.UnixSystem;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.client.deployment.BundledClientDeployment;
import io.brokerqe.claire.clients.DeployableClient;
import io.brokerqe.claire.clients.Protocol;
import io.brokerqe.claire.exception.ClaireRuntimeException;
import io.brokerqe.claire.helper.TimeHelper;
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

public final class ArtemisContainer extends AbstractGenericContainer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArtemisContainer.class);

    public static final String BACKUP_ANNOUNCED_LOG_REGEX = ".*AMQ221031: backup announced\\n";
    public static final List<Integer> DEFAULT_PORTS = List.of(ArtemisConstants.DEFAULT_ALL_PROTOCOLS_PORT, ArtemisConstants.DEFAULT_AMQP_PORT,
            ArtemisConstants.DEFAULT_MQTT_PORT, ArtemisConstants.DEFAULT_STOMP_PORT, ArtemisConstants.DEFAULT_HORNETQ_PORT, ArtemisConstants.DEFAULT_WEB_CONSOLE_PORT, ArtemisConstants.DEFAULT_JMX_PORT);
    public static final String ARTEMIS_INSTALL_DIR = ArtemisConstants.OPT_DIR + Constants.FILE_SEPARATOR + ArtemisConstants.ARTEMIS_STRING;
    public static final String ARTEMIS_INSTANCE_DIR = ArtemisConstants.VAR_DIR + ArtemisConstants.LIB_DIR + Constants.FILE_SEPARATOR + ArtemisConstants.INSTANCE_STRING;
    public static final String ARTEMIS_INSTANCE_ETC_DIR = ARTEMIS_INSTANCE_DIR + ArtemisConstants.ETC_DIR;
    public static final String ARTEMIS_INSTANCE_DATA_DIR = ARTEMIS_INSTANCE_DIR + ArtemisConstants.DATA_DIR;
    private static final String ARTEMIS_INSTANCE_CONTROLLER_CMD = "/usr/local/bin/artemis-controller.sh";

    public static final String INSTANCE_DIR_KEY = "instanceDir";
    public static final String INSTALL_DIR_KEY = "installDir";
    private boolean secured = false;
    private String installDir;
    private String instanceDir;
    private String configBinDir;
    private String configDir;
    private String libDir;
    private DeployableClient deployableClient;

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
                return getBrokerUri(Protocol.AMQP, 5672);
            }
            case AMQPS -> {
                return getBrokerUri(Protocol.AMQP, 5673);
            }
            case CORE -> {
                return getBrokerUri(Protocol.CORE, 61616);
            }
            case CORES -> {
                return getBrokerUri(Protocol.CORE, 61617);
            }
            default -> {
                return getBrokerUri(Protocol.CORE, 61616);
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

        brokerUri = String.format(brokerUri, getName());
        // TODO use port as well?
//        brokerUri = String.format(brokerUri, getInstanceNameAndPort(port));
        return brokerUri;
    }

    public String getInstallDir() {
        return installDir != null ? installDir : TestUtils.getProjectRelativeFile(ArtemisConstants.INSTALL_DIR);
    }

    public void setInstallDir(String installDir) {
        this.installDir = installDir;
        setConfigLibDir(instanceDir + Constants.FILE_SEPARATOR + ArtemisConstants.LIB_DIR);
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
        LOGGER.debug("[Container {}] - with install dir {} = {}", name, dirPath, ARTEMIS_INSTALL_DIR);
        withFileSystemBind(dirPath, ARTEMIS_INSTALL_DIR, BindMode.READ_ONLY, replaceBind);
    }

    public void withConfigDir(String dirPath) {
        String containerConfigDir = ARTEMIS_INSTANCE_DIR + ArtemisConstants.ETC_DIR;
        LOGGER.debug("[Container {}] with config dir {} = {}", name, dirPath, containerConfigDir);
        setConfigDir(dirPath);
        withFileSystemBind(dirPath, containerConfigDir, BindMode.READ_ONLY);
    }

    public void withInstanceDir(String dirPath) {
        String containerInstanceDir = ARTEMIS_INSTANCE_DIR;
        setInstanceDir(dirPath);
        LOGGER.debug("[Container {}] with instance dir {} = {}", name, dirPath, containerInstanceDir);
        withFileSystemBind(dirPath, containerInstanceDir, BindMode.READ_WRITE);
    }

    public void withConfigFile(String srcFilePath, String dstFileName) {
        String destination = ARTEMIS_INSTANCE_DIR + ArtemisConstants.ETC_DIR + File.separator + dstFileName;
        LOGGER.debug("[Container {}] with config file {} = {}", name, srcFilePath, destination);
        withFileSystemBind(srcFilePath, destination, BindMode.READ_ONLY);
    }

    public void withDataDir(String dirPath) {
        LOGGER.debug("[Container {}] with data dir {} = {}", name, dirPath, ARTEMIS_INSTANCE_DATA_DIR);
        TestUtils.createDirectory(dirPath);
        withFileSystemBind(dirPath, ARTEMIS_INSTANCE_DATA_DIR, BindMode.READ_WRITE);
    }

    public void withJavaHome(String dirPath) {
        LOGGER.debug("[Container {}] with env var {} = {}", name, Constants.JAVA_HOME, dirPath);
        container.withEnv(Constants.JAVA_HOME, dirPath);
    }

    public void withLibFile(String srcFilePath, String dstFileName) {
        String destination = ARTEMIS_INSTANCE_DIR + ArtemisConstants.LIB_DIR + File.separator + dstFileName;
        LOGGER.debug("[Container {}] with lib file {} = {}", name, srcFilePath, destination);
        withFileSystemBind(srcFilePath, destination, BindMode.READ_ONLY);
    }

    public void start() {
        start(Duration.ofMinutes(1));
    }
    public void start(Duration startupTimeout) {
        LOGGER.info("[Container {}] - About to start", name);
        LOGGER.debug("[Container {}] - Using exposed ports: {}", name, DEFAULT_PORTS);

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
            return getHttpsConsoleUrl();
        } else {
            return getHttpConsoleUrl();
        }
    }
    public String getHttpConsoleUrl() {
        return Constants.HTTP + "://" + this.getName()
                + ":" + ArtemisConstants.DEFAULT_WEB_CONSOLE_PORT + "/" + ArtemisConstants.CONSOLE_STRING;
    }

    public String getHttpsConsoleUrl() {
        return Constants.HTTPS + "://" + this.getName()
                + ":" + ArtemisConstants.DEFAULT_WEB_CONSOLE_PORT + "/" + ArtemisConstants.CONSOLE_STRING;
    }

    public String getLoginUrl() {
        return getConsoleUrl() + "/" + ArtemisConstants.AUTH_STRING + "/" + ArtemisConstants.LOGIN_STRING;
    }

    public String getLoggedInUrl() {
        return getConsoleUrl() + "/" + ArtemisConstants.ARTEMIS_STRING
                + "/artemisStatus?nid=root-org.apache.activemq.artemis-" + this.getName();
    }

    @Override
    public void stop() {
        LOGGER.debug("[Container {}] - Stopping", name);
        if (container.isRunning()) {
            dockerClient.stopContainerCmd(container.getContainerId()).exec();
            TimeHelper.waitFor(e -> !container.isRunning(), Constants.DURATION_500_MILLISECONDS, Constants.DURATION_5_SECONDS);
        }
        container.stop();
    }

    public String artemisProcessController(ArtemisProcessControllerActions action) {
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

    public enum ArtemisProcessControllerActions {
        START, STOP, FORCE_STOP
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

}
