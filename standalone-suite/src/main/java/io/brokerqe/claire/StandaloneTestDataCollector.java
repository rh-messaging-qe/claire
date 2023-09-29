/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire;

import io.brokerqe.claire.exception.ClaireRuntimeException;
import io.brokerqe.claire.container.ArtemisContainer;
import io.brokerqe.claire.container.ContainerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StandaloneTestDataCollector extends TestDataCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger(StandaloneTestDataCollector.class);

    private static final String CONTAINERS_STRING = "containers";
    private static final String CONTAINER_LOG_SUFFIX = "_container.log";


    @Override
    protected void collectTestData() {
        LOGGER.info("Gathering debug data for failed {}#{} into {}", testClass, testMethod, archiveDir);
        getContainersLogs();
        getBrokerFiles();
    }

    private void getContainersLogs() {
        LOGGER.info("Gathering debug data for containers");
        ResourceManager.getContainers().forEach((name, container) -> {
            LOGGER.info("Gathering logs for container: {}", name);
            String containersDir =  archiveDir + Constants.FILE_SEPARATOR + CONTAINERS_STRING;
            String containerDir = containersDir + Constants.FILE_SEPARATOR + name;
            String logFile = containerDir + Constants.FILE_SEPARATOR + name + CONTAINER_LOG_SUFFIX;

            TestUtils.createDirectory(containerDir);
            String log = container.getLogs();
            TestUtils.createFile(logFile, log);
        });
    }

    private void getBrokerFiles() {
        ResourceManager.getContainers().forEach((name, container) -> {
            LOGGER.info("Gathering broker data for container: {}", name);
            if (ContainerType.ARTEMIS == container.getContainerType()) {
                String containersDir =  archiveDir + Constants.FILE_SEPARATOR + CONTAINERS_STRING;
                String containerDir = containersDir + Constants.FILE_SEPARATOR + name;
                String dstDir = containerDir + Constants.FILE_SEPARATOR + ArtemisConstants.INSTANCE_STRING;
                String srcDir = ArtemisContainer.ARTEMIS_INSTANCE_DIR;
                try {
                    container.copyDirFrom(srcDir, dstDir);
                } catch (ClaireRuntimeException e) {
                    String errMsg = String.format("Error on copying directory %s from container %s to %s: %s",
                            srcDir, container.getName(), dstDir, e.getMessage());
                    throw new ClaireRuntimeException(errMsg, e);
                }
            }
        });
    }

}
