/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.container;

import io.brokerqe.claire.Constants;
import io.brokerqe.claire.exception.ClaireRuntimeException;
import io.brokerqe.claire.helper.TimeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class NfsServerContainer extends AbstractGenericContainer {

    private static final Logger LOGGER = LoggerFactory.getLogger(NfsServerContainer.class);
    public static final String DEFAULT_CLIENT_OPTIONS = "vers=4,proto=tcp,sync,noac,soft,lookupcache=none,timeo=15,retrans=1";

    private final Map<String, String> exportsMap = new HashMap<>();

    public NfsServerContainer(String name) {
        super(name, ENVIRONMENT_STANDALONE.getNfsServerContainerImage());
        this.name = name;
        this.type = ContainerType.NFS_SERVER;
    }

    public void withExportDir(String hostDir, String containerDir) {
        Path hostPath = Paths.get(hostDir);
        if (!hostPath.toFile().exists()) {
            try {
                Files.createDirectories(hostPath);
            } catch (IOException e) {
                String errorMsg = "Error creating directory: " + e.getMessage();
                throw new ClaireRuntimeException(errorMsg, e);
            }
        }
        exportsMap.put(hostDir, containerDir);
    }

    public void start() {
        LOGGER.info("[{}] - About to start", name);
        container.withPrivilegedMode(true);
        exportsMap.forEach((hostDir, containerDir) -> withFileSystemBind(hostDir, containerDir, BindMode.READ_WRITE));
        container.withCommand(exportsMap.values().toArray(new String[0]));
        super.start();
    }

    @Override
    public void stop() {
        LOGGER.debug("[{}] - Stopping", name);
        if (container.isRunning()) {
            dockerClient.stopContainerCmd(container.getContainerId()).exec();
            TimeHelper.waitFor(e -> !container.isRunning(), Constants.DURATION_500_MILLISECONDS, Constants.DURATION_5_SECONDS);
        }
        container.stop();
    }

    public String getNfsMountString(String exportDirName, String mountPath) {
        return getNfsMountString(exportDirName, mountPath, null);
    }

    public String getNfsMountString(String serverName, String serverPort, String exportDirName, String mountPath, String additionalOpts) {
        Map.Entry<String, String> entry = exportsMap.entrySet().stream().filter(e -> e.getKey().contains(exportDirName)).findFirst().orElseThrow();
        String mountString =  serverName + ":" + entry.getValue().replaceAll("/exports", "") + "#" + mountPath;
        if (serverPort != null) {
            mountString += "#port=" + serverPort;
        } else {
            mountString += "#port=2049";
        }
        if (additionalOpts != null) {
            mountString += "," + additionalOpts;
        }
        return mountString;
    }

    public String getNfsMountString(String exportDirName, String mountPath, String additionalOpts) {
        return getNfsMountString(name, null, exportDirName, mountPath, additionalOpts);
    }
}
