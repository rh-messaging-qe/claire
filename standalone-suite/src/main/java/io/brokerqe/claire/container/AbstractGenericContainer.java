/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.container;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.KillContainerCmd;
import com.github.dockerjava.api.command.PauseContainerCmd;
import com.github.dockerjava.api.command.UnpauseContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Bind;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.EnvironmentStandalone;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.exception.ClaireRuntimeException;
import io.brokerqe.claire.executor.Executor;
import io.brokerqe.claire.executor.ExecutorStandalone;
import io.brokerqe.claire.helper.TimeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.ImagePullPolicy;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public abstract class AbstractGenericContainer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractGenericContainer.class);
    private static final String ETC_LOCALTIME = "/etc/localtime";

    protected static final EnvironmentStandalone ENVIRONMENT_STANDALONE = EnvironmentStandalone.getInstance();

    private final Slf4jLogConsumer logConsumer;
    protected GenericContainer<?> container;
    protected String name;
    protected ContainerType type;
    private String userId;
    protected DockerClient dockerClient = DockerClientFactory.lazyClient();

    protected AbstractGenericContainer(String name, String dockerImage) {
        this.name = name;
        logConsumer = new Slf4jLogConsumer(LOGGER);
        if (dockerImage != null) {
            container = new GenericContainer<>(DockerImageName.parse(dockerImage));
            LOGGER.debug("[Container: {}] - With default network: {}", name, ResourceManager.getDefaultNetwork());
            container.withNetwork(ResourceManager.getDefaultNetwork());
        }
    }

    public GenericContainer<?> getGenericContainer() {
        return container;
    }

    public void withCustomNetwork(Network network) {
        LOGGER.debug("[Container: {}] - With custom network: {}", name, network.getId());
        container.withNetwork(network);
    }

    public void withLogWait(String regex) {
        LOGGER.debug("[Container {}] - With log wait regex: {}", name, regex);
        container.setWaitStrategy(Wait.forLogMessage(regex, 1));
    }

    public void withNetworkAlias(String name) {
        container.withNetworkAliases(name);
    }

    public void withUserId(String userId) {
        this.userId = userId;
    }

    public void start() {
        LOGGER.trace("[Container {}] - With hostname: {}", name, name);
        container.withCreateContainerCmdModifier(cmd -> {
            if (userId != null) {
                cmd.withUser(userId);
            }
            cmd.withHostName(name);
        });
        LOGGER.trace("[Container {}] - With network alias: {}", name, name);
        container.withNetworkAliases(name);
        if (ENVIRONMENT_STANDALONE.isLogContainers()) {
            withStdOutLog();
        }
        withPullPolicy(PullPolicy.alwaysPull());
        withFileSystemBind(ETC_LOCALTIME, ETC_LOCALTIME, BindMode.READ_ONLY);
        LOGGER.debug("[Container {}] - Starting", name);
        container.start();
    }

    public boolean isRunning() {
        return container.isRunning();
    }

    public void stop() {
        LOGGER.debug("[Container {}] - Stopping", name);
        container.stop();
    }

    public void pause() {
        LOGGER.debug("[Container {}] - Pausing", name);
        try (PauseContainerCmd pauseCmd = dockerClient.pauseContainerCmd(container.getContainerId())) {
            pauseCmd.exec();
        } catch (NotFoundException e) {
            String errMsg = String.format("error on getting pausing container: %s", e.getMessage());
            LOGGER.error(errMsg, e);
            throw new ClaireRuntimeException(errMsg, e);
        }
    }

    public void unpause() {
        LOGGER.debug("[Container {}] - Unpausing", name);
        try (UnpauseContainerCmd unpauseCmd = dockerClient.unpauseContainerCmd(container.getContainerId())) {
            unpauseCmd.exec();
        } catch (NotFoundException e) {
            String errMsg = String.format("error on getting unpausing container: %s", e.getMessage());
            LOGGER.error(errMsg, e);
            throw new ClaireRuntimeException(errMsg, e);
        }
    }

    public void kill() {
        LOGGER.debug("[Container {}] - Killing", name);
        try (KillContainerCmd killCmd = dockerClient.killContainerCmd(container.getContainerId())) {
            killCmd.exec();
            container.stop();
        } catch (NotFoundException e) {
            String errMsg = String.format("error on getting killing container: %s", e.getMessage());
            LOGGER.error(errMsg, e);
            throw new ClaireRuntimeException(errMsg, e);
        }
    }

    public void restartWithStop() {
        restartWithStop(Duration.ofMinutes(1));
    }
    public void restartWithStop(Duration startTimeout) {
        LOGGER.debug("[Container {}] - Stopping and restarting with timeout {}", name, startTimeout);
        container.stop();
        container.withStartupTimeout(startTimeout);
        container.start();
    }

    public String getHost() {
        return container.getHost();
    }

    public int getPort(int port) {
        return container.getMappedPort(port);
    }

    public String getName() {
        return name;
    }

    public ContainerType getContainerType() {
        return type;
    }

    protected InspectContainerResponse.ContainerState getState() {
        try (InspectContainerCmd inspectCmd = container.getDockerClient().inspectContainerCmd(container.getContainerId())) {
            return inspectCmd.exec().getState();
        }
    }

    public String getStatus() {
        return getState().getStatus();
    }

    public long getExitCode() {
        TimeHelper.waitFor(e -> getState().getExitCodeLong() != null, Constants.DURATION_1_SECOND, Constants.DURATION_30_SECONDS);
        return getState().getExitCodeLong();
    }

    public String getHostAndPort(int port) {
        return getHost() + ":" + getPort(port);
    }

    public String getLogs() {
        return container.getLogs();
    }

    private void withStdOutLog() {
        LOGGER.debug("[Container {}] - With stdout logging", name);
        if (container.getLogConsumers().contains(logConsumer)) {
            return;
        }
        logConsumer.withPrefix(name);
        container.withLogConsumer(logConsumer);
    }

    public void withFileSystemBind(String source, String destination, BindMode mode) {
        LOGGER.trace("[Container {}] - Binding filesystem from {} to {} with mode {}", name, source, destination, mode);
        List<Bind> currentBinds = container.getBinds();
        boolean alreadyContainsBind = currentBinds.stream().anyMatch(p -> p.getVolume().getPath().equals(destination));
        if (!alreadyContainsBind) {
            container.withFileSystemBind(source, destination, mode);
        } else {
            LOGGER.debug("[Container {}] - Ignoring bind {} as it already exist", name, destination);
        }
    }

    public void withEnvVar(Map<String, String> envVars) {
        container.withEnv(envVars);
    }

    public void withCopyFileToContainer(String source, String destination) {
        LOGGER.debug("[Container {}] - Copying host file {} to container file {}", name, source, destination);
        container.withCopyFileToContainer(MountableFile.forHostPath(source), destination);
    }

    public void copyFileFrom(String containerFile, String hostFile) {
        LOGGER.debug("[Container {}] - Copying file {} to host file {}", name, containerFile, hostFile);
        container.copyFileFromContainer(containerFile, hostFile);
    }

    public void deleteFileFrom(String file) {
        LOGGER.debug("[Container {}] - Deleting file {}", name, file);
        executeCommand("rm", "-rf", file);
    }

    public void copyDirFrom(String containerDir, String hostDir) {
        LOGGER.debug("[Container {}] - Copying directory {} to host directory {}", name, containerDir, hostDir);
        String tarDstDir = Constants.TMP_DIR;
        String tarFilename = Constants.TAR_TMP_FILE_PREFIX + TestUtils.getRandomString(6) + ".tar";
        String tarFile = tarDstDir + tarFilename;
        executeCommand("tar", "-cf", tarFile, "-C", containerDir, ".");
        TestUtils.createDirectory(hostDir);
        copyFileFrom(tarFile, hostDir + tarFilename);
        deleteFileFrom(tarFile);
        String dstTarFile = hostDir + tarFilename;
        TestUtils.unTar(dstTarFile, hostDir);
        TestUtils.deleteFile(Paths.get(dstTarFile));
    }

    public void withPullPolicy(ImagePullPolicy pullPolicy) {
        LOGGER.debug("[Container {}] - Setting pull policy as {}", name, pullPolicy.getClass().getSimpleName());
        container.withImagePullPolicy(pullPolicy);
    }

    public void waitForExit(long pollTimeout, long maxTimeout) {
        waitForExit(pollTimeout, maxTimeout, false);
    }

    public void waitForExit(long pollTimeout, long maxTimeout, boolean throwException) {
        TimeHelper.waitFor(e -> getStatus().equalsIgnoreCase("exited"), pollTimeout, maxTimeout);
        LOGGER.info("[Container {}] exited with {}", getName(), getStatus());
        if (getExitCode() != 0L) {
            LOGGER.debug("[Container {}] {}", getName(), getLogs());
            if (throwException) {
                throw new ClaireRuntimeException("[Container " + getName() + "] Exited with error! \n" + getLogs());
            }
        }
    }

    public Object executeCommand(String... command) {
        return getExecutor().executeCommand(command);
    }

    public Executor getExecutor() {
        return new ExecutorStandalone(container);
    }

    public String getInstanceNameAndPort(int portNumber) {
        return name + ":" + portNumber;
    }
}
