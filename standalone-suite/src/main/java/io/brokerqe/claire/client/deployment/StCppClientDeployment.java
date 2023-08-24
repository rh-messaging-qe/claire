/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.client.deployment;

import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.executor.ExecutorStandalone;
import io.brokerqe.claire.container.AbstractGenericContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StCppClientDeployment extends StClientDeployment {

    private static final Logger LOGGER = LoggerFactory.getLogger(StCppClientDeployment.class);
    private ExecutorStandalone executor;

    public StCppClientDeployment() {
        deployContainer();
    }

    @Override
    public AbstractGenericContainer deployContainer() {
        this.container = ResourceManager.getSystemTestCppClientContainerInstance("st-cpp-client-" + TestUtils.generateRandomName());
        container.start();
        setContainer(container);
        return container;
    }
}
