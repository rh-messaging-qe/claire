/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.junit;

import io.brokerqe.claire.KubernetesVersion;
import io.brokerqe.claire.ResourceManager;
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

/**
 * This annotation executes tests only on specified platform.
 * Supported values are "Openshift" and "Kubernetes".
 */

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(TestMinimumKubernetesVersion.MinimumKubernetesCountCondition.class)
public @interface TestMinimumKubernetesVersion {
    // 1.28 - 4.15
    // 1.29 - 4.16
    // 1.30 - 4.17
    // 1.31 - 4.18
    // 1.32 - 4.19
    KubernetesVersion value();

    class MinimumKubernetesCountCondition implements ExecutionCondition {
        private final static Logger LOGGER = LoggerFactory.getLogger(MinimumKubernetesCountCondition.class);
        private static final ConditionEvaluationResult ENABLED = ConditionEvaluationResult.enabled("@TestValidSince is not present");

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            AnnotatedElement element = context.getElement().orElse(null);
            String testname = context.getRequiredTestClass().getName();
            KubernetesVersion kubernetesVersionEnv = ResourceManager.getKubeClient().getKubernetesVersion();
            return findAnnotation(element, TestMinimumKubernetesVersion.class).map(annotation -> toResult(annotation, kubernetesVersionEnv, testname)).orElse(ENABLED);
        }

        private ConditionEvaluationResult toResult(TestMinimumKubernetesVersion annotation, KubernetesVersion kubernetesVersionEnv, String testName) {
            if (kubernetesVersionEnv.getVersionNumber() >= annotation.value().getVersionNumber()) {
                return ConditionEvaluationResult.enabled("Test enabled on provided number of Kubernetes platforms");
            } else {
                LOGGER.warn("[TEST][{}] Disabling test - Kubernetes version is old. Needed {} - got {}.", testName,
                        annotation.value().getVersionNumber(), kubernetesVersionEnv.getVersionNumber());
                return ConditionEvaluationResult.disabled("[TEST] Skipped: Unsupported platform count for this test.");
            }
        }
    }
}
