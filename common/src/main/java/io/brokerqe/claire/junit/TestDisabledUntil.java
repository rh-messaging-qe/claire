/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.junit;


import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.Environment;
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
import java.lang.reflect.AnnotatedElement;

import static org.junit.platform.commons.util.AnnotationUtils.findAnnotation;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(TestDisabledUntil.TestVersionValidityCondition.class)
public @interface TestDisabledUntil {

    ArtemisVersion value();
    class TestVersionValidityCondition implements ExecutionCondition {
        private final static Logger LOGGER = LoggerFactory.getLogger(TestVersionValidityCondition.class);
        private String testName;
        private static final ConditionEvaluationResult ENABLED = ConditionEvaluationResult.enabled("@TestDisabledUntil is not present");

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            AnnotatedElement element = context.getElement().orElse(null);
            testName = context.getTestClass().get().getName() + "." + context.getDisplayName();
            ArtemisVersion artemisTestVersion = Environment.get().getArtemisTestVersion();
            return findAnnotation(element, TestDisabledUntil.class).map(annotation -> toResult(annotation, artemisTestVersion)).orElse(ENABLED);
        }

        private ConditionEvaluationResult toResult(TestDisabledUntil annotation, ArtemisVersion artemisTestVersionEnv) {
            ArtemisVersion artemisTestVersionAnnotated = annotation.value();

            if (artemisTestVersionEnv == null) {
                return ConditionEvaluationResult.enabled("Test enabled. ARTEMIS_TEST_VERSION is not specified.");
            }

            if (artemisTestVersionAnnotated.getVersionNumber() < artemisTestVersionEnv.getVersionNumber()) {
                return ConditionEvaluationResult.enabled("Test enabled. TestDisabledUntil(" + artemisTestVersionAnnotated + ") < " + artemisTestVersionEnv);
            } else {
                LOGGER.info("[TEST] " + testName + " skipped: TestDisabledUntil(" + artemisTestVersionAnnotated + ") >= " + artemisTestVersionEnv);
                return ConditionEvaluationResult.disabled("[TEST] Skipped: TestDisabledUntil(" + artemisTestVersionAnnotated + ") >= " + artemisTestVersionEnv + " than provided.");
            }
        }
    }
}
