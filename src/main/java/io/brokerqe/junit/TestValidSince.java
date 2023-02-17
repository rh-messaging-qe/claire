/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.junit;


import io.brokerqe.ArtemisVersion;
import io.brokerqe.ResourceManager;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;

import static org.junit.platform.commons.util.AnnotationUtils.findAnnotation;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(TestValidSince.TestVersionValidityCondition.class)
public @interface TestValidSince {

    ArtemisVersion value();
    class TestVersionValidityCondition implements ExecutionCondition {
        private static final ConditionEvaluationResult ENABLED = ConditionEvaluationResult.enabled("@TestValidSince is not present");

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            AnnotatedElement element = context.getElement().orElse(null);
            ArtemisVersion artemisTestVersion = ResourceManager.getEnvironment().getArtemisTestVersion();
            return findAnnotation(element, TestValidSince.class).map(annotation -> toResult(annotation, artemisTestVersion)).orElse(ENABLED);
        }

        private ConditionEvaluationResult toResult(TestValidSince annotation, ArtemisVersion artemisTestVersionEnv) {
            ArtemisVersion artemisTestVersionAnnotated = annotation.value();

            if (artemisTestVersionEnv == null) {
                return ConditionEvaluationResult.enabled("Test enabled. ARTEMIS_TEST_VERSION is not specified.");
            }

            if (artemisTestVersionAnnotated.getVersionNumber() <= artemisTestVersionEnv.getVersionNumber()) {
                return ConditionEvaluationResult.enabled("Test enabled. TestValidSince " + artemisTestVersionAnnotated + " < " + artemisTestVersionEnv);
            } else {
                return ConditionEvaluationResult.disabled("[SKIP] TestValidSince " + artemisTestVersionAnnotated +
                        " > " + artemisTestVersionEnv + " than provided.");
            }
        }
    }
}
