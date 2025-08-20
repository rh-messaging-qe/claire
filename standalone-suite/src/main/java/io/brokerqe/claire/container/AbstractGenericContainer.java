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
import com.github.dockerjava.api.model.ContainerNetwork;
import com.github.dockerjava.api.model.Volume;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.CommandResult;
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
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
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
            LOGGER.debug("[{}] With default network: {}", name, ResourceManager.getDefaultNetwork().getId());
            container.withNetwork(ResourceManager.getDefaultNetwork());
        }
    }

    public GenericContainer<?> getGenericContainer() {
        return container;
    }

    public String getContainerIpAddress() {
        Map<String, ContainerNetwork> map = getGenericContainer().getContainerInfo().getNetworkSettings().getNetworks();
        Object mapId = map.keySet().toArray()[0];
        return map.get(mapId).getIpAddress();
    }

    public void withCustomNetwork(Network network) {
        LOGGER.debug("[{}] With custom network: {}", name, network.getId());
        container.withNetwork(network);
    }

    public void withLogWait(String regex) {
        LOGGER.debug("[{}] With log wait regex: {}", name, regex);
        container.setWaitStrategy(Wait.forLogMessage(regex, 1));
    }

    public void withNetworkAlias(String name) {
        container.withNetworkAliases(name);
    }

    public void withUserId(String userId) {
        this.userId = userId;
    }

    public void start() {
        LOGGER.trace("[{}] With hostname: {}", name, name);
        container.withCreateContainerCmdModifier(cmd -> {
            if (userId != null) {
                cmd.withUser(userId);
            }
            cmd.withHostName(name);
            cmd.withName(name);
        });
        LOGGER.trace("[{}] With network alias: {}", name, name);
        container.withNetworkAliases(name);
        if (ENVIRONMENT_STANDALONE.isLogContainers()) {
            withStdOutLog();
        }
        withPullPolicy(ENVIRONMENT_STANDALONE.getImagePullPolicy());
        withFileSystemBind(ETC_LOCALTIME, ETC_LOCALTIME, BindMode.READ_ONLY);
        LOGGER.debug("[{}] Starting", name);
        container.start();
    }

    public boolean isRunning() {
        return container.isRunning();
    }

    public void stop() {
        LOGGER.debug("[{}] Stopping", name);
        container.stop();
    }

    public void pause() {
        LOGGER.debug("[{}] Pausing", name);
        try (PauseContainerCmd pauseCmd = dockerClient.pauseContainerCmd(container.getContainerId())) {
            pauseCmd.exec();
        } catch (NotFoundException e) {
            String errMsg = String.format("error on getting pausing container: %s", e.getMessage());
            LOGGER.error(errMsg, e);
            throw new ClaireRuntimeException(errMsg, e);
        }
    }

    public void unpause() {
        LOGGER.debug("[{}] Unpausing", name);
        try (UnpauseContainerCmd unpauseCmd = dockerClient.unpauseContainerCmd(container.getContainerId())) {
            unpauseCmd.exec();
        } catch (NotFoundException e) {
            String errMsg = String.format("error on getting unpausing container: %s", e.getMessage());
            LOGGER.error(errMsg, e);
            throw new ClaireRuntimeException(errMsg, e);
        }
    }

    public void kill() {
        LOGGER.debug("[{}] Killing", name);
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
        LOGGER.debug("[{}] Stopping and restarting with timeout {}", name, startTimeout);
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

    public String getLogTail(int lines) {
        return TestUtils.getLastLines(container.getLogs(), lines).toString();
    }

    private void withStdOutLog() {
        LOGGER.debug("[{}] With stdout logging", name);
        if (container.getLogConsumers().contains(logConsumer)) {
            return;
        }
        logConsumer.withPrefix(name);
        container.withLogConsumer(logConsumer);
    }

    public void withFileSystemBind(String source, String destination, BindMode mode) {
        withFileSystemBind(source, destination, mode, false);
    }

    public void withFileSystemBind(String source, String destination, BindMode mode, boolean replaceBind) {
        LOGGER.trace("[{}] Binding filesystem {} -> {} mode: {}", name, source, destination, mode);
        List<Bind> currentBinds = container.getBinds();

        if (replaceBind) {
            List<Bind> tmpBinds = new ArrayList<>(List.copyOf(currentBinds));
            LOGGER.debug("[{}] Replacing bind filesystem {} -> {} mode: {}", name, source, destination, mode);
            tmpBinds.removeIf(bind -> bind.getVolume().toString().equals(ArtemisContainer.ARTEMIS_INSTALL_DIR));
            tmpBinds.add(new Bind(source, new Volume(destination)));
            container.setBinds(tmpBinds);
            return;
        }

        boolean alreadyContainsBind = currentBinds.stream().anyMatch(p -> p.getVolume().getPath().equals(destination));
        if (!alreadyContainsBind) {
            container.withFileSystemBind(source, destination, mode);
        } else {
            LOGGER.debug("[{}] Ignoring bind {} as it already exists", name, destination);
        }
    }

    public void withEnvVar(Map<String, String> envVars) {
        container.withEnv(envVars);
    }

    public void withCopyFileToContainer(String source, String destination) {
        LOGGER.debug("[{}] Copying host file {} to container file {}", name, source, destination);
        container.withCopyFileToContainer(MountableFile.forHostPath(source), destination);
    }

    public void copyFileFrom(String containerFile, String hostFile) {
        LOGGER.debug("[{}] Copying file {} to host file {}", name, containerFile, hostFile);
        container.copyFileFromContainer(containerFile, hostFile);
    }

    public void deleteFileFrom(String file) {
        LOGGER.debug("[{}] Deleting file {}", name, file);
        executeCommand("rm", "-rf", file);
    }

    public void copyDirFrom(String containerDir, String hostDir) {
        LOGGER.debug("[{}] Copying directory {} to host directory {}", name, containerDir, hostDir);
        String tarDstDir = ArtemisConstants.TMP_DIR;
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

    public void copyWithinContainer(String containerSrcDir, String containerDestDir) {
        LOGGER.debug("[{}] [container] Copying directory {} to directory {}", name, containerSrcDir, containerDestDir);
        executeCommand("cp", "-r", containerSrcDir, containerDestDir);
    }

    public void withPullPolicy(ImagePullPolicy pullPolicy) {
        LOGGER.debug("[{}] Setting pull policy as {}", name, pullPolicy.getClass().getSimpleName());
        container.withImagePullPolicy(pullPolicy);
    }

    public void waitForExit(long pollTimeout, long maxTimeout) {
        waitForExit(pollTimeout, maxTimeout, false);
    }

    public void waitForExit(long pollTimeout, long maxTimeout, boolean throwException) {
        TimeHelper.waitFor(e -> getStatus().equalsIgnoreCase("exited"), pollTimeout, maxTimeout);
        LOGGER.info("[{}] exited with {}", getName(), getStatus());
        if (getExitCode() != 0L) {
            LOGGER.debug("[{}] {}", getName(), getLogs());
            if (throwException) {
                throw new ClaireRuntimeException("[" + getName() + "] Exited with error! \n" + getLogs());
            }
        }
    }

    public CommandResult executeCommand(String... command) {
        // you might need to use "sh", "-c", <your-cmd>
        return getExecutor().executeCommand(command);
    }

    public Executor getExecutor() {
        return new ExecutorStandalone(container);
    }

    public String getInstanceNameAndPort(int portNumber) {
        return name + ":" + portNumber;
    }
}
