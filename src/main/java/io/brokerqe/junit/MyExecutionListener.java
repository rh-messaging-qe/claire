/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.junit;

import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MyExecutionListener implements TestExecutionListener {

    static final Logger LOGGER = LoggerFactory.getLogger(MyExecutionListener.class);
    public void testPlanExecutionStarted(TestPlan testPlan) {
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
}
