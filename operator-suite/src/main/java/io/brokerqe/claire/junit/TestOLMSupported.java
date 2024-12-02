/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.junit;

import io.brokerqe.claire.ResourceManager;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.PreconditionViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation executes tests only on when Operator is deployed via OLM.
 */

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(TestOLMSupported.OLMOperatorDeploymentCondition.class)
public @interface TestOLMSupported {


    class OLMOperatorDeploymentCondition implements ExecutionCondition {
        private final static Logger LOGGER = LoggerFactory.getLogger(OLMOperatorDeploymentCondition.class);
        private static final ConditionEvaluationResult ENABLED = ConditionEvaluationResult.enabled("@TestOLMSupported is present");

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            String testname = context.getRequiredTestClass().getName();
            try {
                testname += context.getRequiredTestMethod().getName();
            } catch (PreconditionViolationException ignored) { }
            boolean isOlmInstallation = ResourceManager.getEnvironment().isOlmInstallation();
            if (isOlmInstallation) {
                return ConditionEvaluationResult.enabled("Test enabled on Operator OLM installation");
            } else {
                LOGGER.warn("[TEST][{}] Skipping test: Operator not installed using OLM.", testname);
                return ConditionEvaluationResult.disabled("[TEST] Skipped: Operator not installed using OLM.");
            }
        }
    }
}
