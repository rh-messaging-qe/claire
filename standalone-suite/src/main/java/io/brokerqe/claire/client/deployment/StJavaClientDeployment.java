/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.client.deployment;

import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.container.AbstractGenericContainer;

public class StJavaClientDeployment extends StClientDeployment {

    public StJavaClientDeployment() {
        deployContainer();
    }

    @Override
    public AbstractGenericContainer deployContainer() {
        this.container = ResourceManager.getSystemTestJavaClientsContainerInstance("st-clients-" + TestUtils.generateRandomName());
        container.start();
        setContainer(container);
        return container;
    }
}
