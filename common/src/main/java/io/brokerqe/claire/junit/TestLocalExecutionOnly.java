/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.junit;


import io.brokerqe.claire.Environment;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(TestLocalExecutionOnly.TestLocalExecution.class)
public @interface TestLocalExecutionOnly {

    class TestLocalExecution implements ExecutionCondition, BeforeAllCallback {
        private final static Logger LOGGER = LoggerFactory.getLogger(TestLocalExecutionOnly.class);
        private static boolean enabled = true;


        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            enabled = shouldRun();
            if (enabled) {
                return ConditionEvaluationResult.enabled("Test enabled. TEST_LOCAL_EXEC is true.");
            } else {
                LOGGER.info("[TEST] Skipped: TEST_LOCAL_EXEC is not set or false.");
                return ConditionEvaluationResult.disabled("[TEST] Test disabled. TEST_LOCAL_EXEC is not set or false.");
            }
        }

        @Override
        public void beforeAll(ExtensionContext context) throws Exception {
            if (!enabled) return; // Skip any logic
            // Else run setup logic
        }

        private boolean shouldRun() {
            return Environment.get().isLocalExecution();
        }

    }
}