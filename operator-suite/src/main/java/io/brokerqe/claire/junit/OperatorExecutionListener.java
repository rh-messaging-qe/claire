/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.junit;

import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.EnvironmentOperator;
import okhttp3.OkHttpClient;
import org.junit.platform.launcher.TestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OperatorExecutionListener extends ClaireExecutionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(OperatorExecutionListener.class);
    protected static EnvironmentOperator testEnvironmentOperator = null;

    protected void setupEnvironment() {
        LOGGER.debug("Setup environment started");
        if (!setupPerformed) {
            testEnvironmentOperator = ResourceManager.getEnvironment();
            setupLoggingLevel();
            testEnvironmentOperator.checkSetProvidedImages();
            ResourceManager.getInstance(testEnvironmentOperator);
            // Following log is added for debugging purposes, when OkHttpClient leaks connection
            java.util.logging.Logger.getLogger(OkHttpClient.class.getName()).setLevel(java.util.logging.Level.FINE);
            if (!testEnvironmentOperator.isOlmInstallation()) {
                ResourceManager.deployArtemisClusterOperatorCRDs();
            }
            setupPerformed = true;
        }
        LOGGER.debug("Setup environment finished");
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        LOGGER.debug("Teardown environment started");
        ResourceManager.undeployAllResources();
        if (!testEnvironmentOperator.isOlmInstallation()) {
            ResourceManager.undeployArtemisClusterOperatorCRDs();
        }
        LOGGER.debug("Teardown environment finished");
        setupPerformed = false;
        LOGGER.debug("Resetting setupPerformed to 'false'");
    }
}
