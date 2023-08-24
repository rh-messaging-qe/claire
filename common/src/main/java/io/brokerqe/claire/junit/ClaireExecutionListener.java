/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.junit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import io.brokerqe.claire.Environment;
import io.brokerqe.claire.exception.ClaireRuntimeException;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class ClaireExecutionListener implements TestExecutionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClaireExecutionListener.class);
    protected static boolean setupPerformed = false;

    public void testPlanExecutionStarted(TestPlan testPlan) {
        createTestPlan(testPlan);
        setupEnvironment();
    }

//    public void testPlanExecutionFinished(TestPlan testPlan) {
//        LOGGER.debug("Teardown environment started");
//        LOGGER.debug("Teardown environment finished");
//    }

    abstract protected void setupEnvironment();

    protected void createTestPlan(TestPlan testPlan) {
        List<String> testsList = new ArrayList<>();
        Set<TestIdentifier> rootTestIdentified = testPlan.getRoots();
        TestIdentifier junit5Root = rootTestIdentified.stream()
                .filter(e -> Objects.equals(e.getUniqueId(), "[engine:junit-jupiter]"))
                .findFirst()
                .orElseThrow(() -> new ClaireRuntimeException("No test root found"));
        Set<TestIdentifier> testClasses = testPlan.getChildren(junit5Root);
        testClasses.forEach(testClass -> {
            testsList.add("\n");
            Set<TestIdentifier> tests = testPlan.getChildren(testClass);
            tests.stream().map(test -> {
                String fqtn = testClass.getDisplayName() + "." + test.getDisplayName();
                LOGGER.debug("{}", fqtn);
                return fqtn;
            }).collect(Collectors.toCollection(() -> testsList));
        });
        String formattedTestPlan = String.join(",", testsList).replaceAll(",\\n,", "\n ").replaceFirst(",", " ");
        LOGGER.info("[TestPlan] Will execute following {} tests: {}", testsList.size() - Collections.frequency(testsList, "\n"), formattedTestPlan);
    }

    protected static void setupLoggingLevel() {
        String envLogLevel = Environment.get().getTestLogLevel();
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
                        LOGGER.trace("[LOG] Setting up {} to {}", tmpLogger.getName(), envLevel);
                        tmpLogger.setLevel(envLevel);
                    }
                    if (tmpLogger.getName().contains("ROOT")) {
                        tmpLogger.setLevel(Level.WARN);
                    }
                });
        }
    }

}
