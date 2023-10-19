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
@ExtendWith(TestDisabledOn.TestVersionValidityCondition.class)
public @interface TestDisabledOn {

    ArtemisVersion[] value();

    class TestVersionValidityCondition implements ExecutionCondition {
        private final static Logger LOGGER = LoggerFactory.getLogger(TestVersionValidityCondition.class);
        private static final ConditionEvaluationResult ENABLED = ConditionEvaluationResult.enabled("@TestDisabledOn is not present");
        private String testName;

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            AnnotatedElement element = context.getElement().orElse(null);
            testName = context.getTestClass().get().getName() + "." + context.getDisplayName();
            ArtemisVersion artemisTestVersion = Environment.get().getArtemisTestVersion();
            return findAnnotation(element, TestDisabledOn.class).map(annotation -> toResult(annotation, artemisTestVersion)).orElse(ENABLED);
        }

        private ConditionEvaluationResult toResult(TestDisabledOn annotation, ArtemisVersion artemisTestVersionEnv) {
            ArtemisVersion[] artemisTestVersionAnnotates = annotation.value();

            if (artemisTestVersionEnv == null) {
                return ConditionEvaluationResult.enabled("Test enabled. ARTEMIS_TEST_VERSION is not specified.");
            }

            for (ArtemisVersion artemisTestVersionAnnotated : artemisTestVersionAnnotates) {
                if (artemisTestVersionAnnotated.getVersionNumber() == artemisTestVersionEnv.getVersionNumber()) {
                    LOGGER.info("[TEST] " + testName + " skipped: TestDisabledOn(" + artemisTestVersionAnnotated + ") == " + artemisTestVersionEnv);
                    return ConditionEvaluationResult.disabled("[TEST] Skipped: TestDisabledOn(" + artemisTestVersionAnnotated + ") == " + artemisTestVersionEnv);
                }
            }
            return ConditionEvaluationResult.enabled("Test enabled.");
        }
    }
}
