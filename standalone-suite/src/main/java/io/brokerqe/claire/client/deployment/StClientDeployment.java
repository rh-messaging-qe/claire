/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.client.deployment;

import io.brokerqe.claire.clients.DeployableClient;
import io.brokerqe.claire.executor.Executor;
import io.brokerqe.claire.container.AbstractGenericContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public abstract class StClientDeployment implements DeployableClient<AbstractGenericContainer, AbstractGenericContainer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(StClientDeployment.class);
    protected AbstractGenericContainer container;

    public StClientDeployment() {
        deployContainer();
    }

    @Override
    public abstract AbstractGenericContainer deployContainer();

    @Override
    public void undeployContainer() {
        container.stop();
    }

    @Override
    public AbstractGenericContainer deployContainer(boolean secured, List<String> secretNames) {
        throw new UnsupportedOperationException("Not deploying secured clients for now");
    }

    @Override
    public AbstractGenericContainer getContainer() {
        return container;
    }

    @Override
    public void setContainer(AbstractGenericContainer container) {
        this.container = (AbstractGenericContainer) container;
    }

    @Override
    public String getContainerName() {
        return this.container.getName();
    }

    @Override
    public Executor getExecutor() {
        return container.getExecutor();
    }

    @Override
    public String getExecutableHome() {
        return ""; // clients are exported on PATH
    }

    @Override
    public String createFile(String name, int size, String unit) {
        unit = unit == null ? "KiB" : unit;
        String fileName = "/tmp/" + name;
        getExecutor().executeCommand(String.format("fallocate -l %d%s %s", size, unit, fileName).split(" "));
        String fileInfo = (String) getExecutor().executeCommand("ls", "-l", fileName);
        LOGGER.debug("[{}] Created file {}", getContainerName(), fileInfo);
        return fileName;
    }
}
