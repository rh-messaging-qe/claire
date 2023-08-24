/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.client.container;

import com.sun.security.auth.module.UnixSystem;
import io.brokerqe.claire.container.AbstractGenericContainer;
import io.brokerqe.claire.container.ContainerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SystemTestProtonPythonClientContainer extends AbstractGenericContainer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SystemTestProtonPythonClientContainer.class);

    public SystemTestProtonPythonClientContainer(String name) {
        super(name, ENVIRONMENT_STANDALONE.getSystemtestCliProtonPython());
        this.type = ContainerType.SYSTEMTEST_CLIENTS;
        container.withCreateContainerCmdModifier(cmd -> {
            cmd.withUser(String.valueOf(new UnixSystem().getUid()));
            cmd.withCmd("sleep", "infinity");
        });
    }

    public void start() {
        LOGGER.info("[Container {}] Starting", name);
        super.start();
    }

}
