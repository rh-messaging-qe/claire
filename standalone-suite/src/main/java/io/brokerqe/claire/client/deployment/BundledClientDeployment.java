/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.client.deployment;

import io.brokerqe.claire.Constants;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.clients.DeployableClient;
import io.brokerqe.claire.exception.ClaireNotImplementedException;
import io.brokerqe.claire.executor.Executor;
import io.brokerqe.claire.executor.ExecutorStandalone;
import io.brokerqe.claire.container.AbstractGenericContainer;
import io.brokerqe.claire.container.ContainerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

import java.util.List;
import java.util.Map;

public class BundledClientDeployment implements DeployableClient<GenericContainer<?>, GenericContainer<?>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(BundledClientDeployment.class);
    private GenericContainer<?> container;

    @Override
    public GenericContainer<?> getContainer() {
        if (container == null) {
            LOGGER.debug("[STClient] Using default first found systemtest-clients container");
            for (Map.Entry<String, AbstractGenericContainer> entry : ResourceManager.getContainers().entrySet()) {
                if (entry.getValue().getContainerType().equals(ContainerType.ARTEMIS)) {
                    container = entry.getValue().getGenericContainer();
                    LOGGER.debug("Found first artemis container");
                    break;
                }
            }
        }
        return container;
    }

    @Override
    public void setContainer(GenericContainer<?> container) {
        this.container = (GenericContainer<?>) container;
    }

    @Override
    public String getContainerName() {
        return container.getContainerName();
    }

    @Override
    public Executor getExecutor() {
        return new ExecutorStandalone((GenericContainer<?>) getContainer());
    }

    @Override
    public String getExecutableHome() {
        return Constants.ARTEMIS_INSTANCE_BIN_DIR;
    }

    @Override
    public String createFile(String name, int size, String unit) {
        throw new ClaireNotImplementedException("Creating files in container not yet implemented!");
    }

    @Override
    public GenericContainer<?> deployContainer() {
        // We do not perform deployment of this container. It is already deployed by brokerPod
        return getContainer();
    }

    @Override
    public void undeployContainer() {
        throw new UnsupportedOperationException("Undeployment of broker pod is not in this class's responsibility");
    }

    @Override
    public GenericContainer<?> deployContainer(boolean secured, List<String> secretNames) {
        // We do not perform deployment of this container. It is already deployed by brokerPod
        return getContainer();
    }

}
