/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.junit;

import io.brokerqe.claire.EnvironmentStandalone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StandaloneExecutionListener extends ClaireExecutionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(StandaloneExecutionListener.class);

    protected void setupEnvironment() {
        LOGGER.debug("Setup environment started");
        if (!setupPerformed) {
            EnvironmentStandalone.getInstance();
            setupLoggingLevel();
            setupPerformed = true;
        }
        LOGGER.debug("Setup environment finished");
    }
}
