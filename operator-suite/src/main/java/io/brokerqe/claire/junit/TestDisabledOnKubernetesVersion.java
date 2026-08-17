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
@ExtendWith(TestDisabledOnKubernetesVersion.DisabledKubernetesVersion.class)
public @interface TestDisabledOnKubernetesVersion {
    KubernetesVersion value();

    class DisabledKubernetesVersion implements ExecutionCondition {
        private final static Logger LOGGER = LoggerFactory.getLogger(DisabledKubernetesVersion.class);
        private static final ConditionEvaluationResult ENABLED = ConditionEvaluationResult.enabled("@TestDisabledOnKubernetesVersion is not present");

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            AnnotatedElement element = context.getElement().orElse(null);
            String testname = context.getRequiredTestClass().getName();
            KubernetesVersion kubernetesVersionEnv = ResourceManager.getKubeClient().getKubernetesVersion();
            return findAnnotation(element, TestDisabledOnKubernetesVersion.class).map(annotation -> toResult(annotation, kubernetesVersionEnv, testname)).orElse(ENABLED);
        }

        private ConditionEvaluationResult toResult(TestDisabledOnKubernetesVersion annotation, KubernetesVersion kubernetesVersionEnv, String testName) {
            if (kubernetesVersionEnv.getVersionNumber() == annotation.value().getVersionNumber()) {
                LOGGER.warn("[TEST][{}] Disabling test - This Kubernetes version is excluded on {} - got {}.", testName,
                        annotation.value().getVersionNumber(), kubernetesVersionEnv.getVersionNumber());
                return ConditionEvaluationResult.disabled("[TEST] Skipped: Unsupported Kubernetes version for this test.");
            } else {
                return ConditionEvaluationResult.enabled("Test enabled on provided version of Kubernetes platforms");
            }
        }
    }
}
