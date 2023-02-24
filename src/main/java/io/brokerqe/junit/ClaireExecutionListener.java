/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.junit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import io.brokerqe.Environment;
import io.brokerqe.ResourceManager;
import okhttp3.OkHttpClient;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class ClaireExecutionListener implements TestExecutionListener {

    static final Logger LOGGER = LoggerFactory.getLogger(ClaireExecutionListener.class);
    private static boolean setupPerformed = false;
    protected static Environment testEnvironment = null;

    public void testPlanExecutionStarted(TestPlan testPlan) {
        createTestPlan(testPlan);
        setupEnvironment();
    }

    private void setupEnvironment() {
        LOGGER.debug("Setup environment started");
        if (!setupPerformed) {
            testEnvironment = ResourceManager.getEnvironment();
            setupLoggingLevel();
            ResourceManager.getInstance(testEnvironment);
            // Following log is added for debugging purposes, when OkHttpClient leaks connection
            java.util.logging.Logger.getLogger(OkHttpClient.class.getName()).setLevel(java.util.logging.Level.FINE);
            if (!testEnvironment.isOlmInstallation()) {
                ResourceManager.deployArtemisClusterOperatorCRDs();
            }
            setupPerformed = true;
        }
        LOGGER.debug("Setup environment finished");
    }

    private void createTestPlan(TestPlan testPlan) {
        List<String> testsList = new ArrayList<>();
        Set<TestIdentifier> rootTestIdentified = testPlan.getRoots();
        if (rootTestIdentified.size() > 1) {
            LOGGER.warn("More roots! To be implemented later");
        } else if (rootTestIdentified.size() == 1) {
            Set<TestIdentifier> testClasses = testPlan.getChildren((TestIdentifier) rootTestIdentified.toArray()[0]);
            testClasses.forEach(testClass -> {
                testsList.add("\n");
                Set<TestIdentifier> tests = testPlan.getChildren(testClass);
                tests.stream().map(test -> {
                    String fqtn = testClass.getDisplayName() + "." + test.getDisplayName();
                    LOGGER.debug("{}", fqtn);
                    return fqtn;
                }).collect(Collectors.toCollection(() -> testsList));
            });
        } else {
            LOGGER.error("No root TestIdentifier found for tests! No tests to execute");
        }
        String formattedTestPlan = String.join(",", testsList).replaceAll(",\\n,", "\n ").replaceFirst(",", " ");
        LOGGER.info("[TestPlan] Will execute following {} tests: {}", testsList.size() - Collections.frequency(testsList, "\n"), formattedTestPlan);
    }

    static void setupLoggingLevel() {
        String envLogLevel = testEnvironment.getTestLogLevel();
        if (envLogLevel == null || envLogLevel.equals("")) {
            LOGGER.debug("Not setting log level at all.");
        } else {
            Level envLevel = Level.toLevel(envLogLevel.toUpperCase(Locale.ROOT));
            LOGGER.info("All logging changed to level: {}", envLevel.levelStr);
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            List<ch.qos.logback.classic.Logger> loggerList = loggerContext.getLoggerList();
            loggerList.forEach(
                tmpLogger -> {
                    // Do not set `ROOT` and `io` logger, as it would set it on all used components, not just this project.
//                        if (!List.of("ROOT", "io").contains(tmpLogger.getName())) {
                    if (tmpLogger.getName().contains("io.brokerqe")) {
                        tmpLogger.setLevel(envLevel);
                    }
                });
        }
    }

    public void testPlanExecutionFinished(TestPlan testPlan) {
        LOGGER.debug("Teardown environment started");
        ResourceManager.undeployAllResources();
        if (!testEnvironment.isOlmInstallation()) {
            ResourceManager.undeployArtemisClusterOperatorCRDs();
        }
        LOGGER.debug("Teardown environment finished");
    }
}
