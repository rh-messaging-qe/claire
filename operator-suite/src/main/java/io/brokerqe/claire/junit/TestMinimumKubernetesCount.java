/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.junit;

import io.brokerqe.claire.KubeClient;
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
import java.lang.reflect.AnnotatedElement;
import java.util.Collection;

import static org.junit.platform.commons.util.AnnotationUtils.findAnnotation;

/**
 * This annotation executes tests only on specified platform.
 * Supported values are "Openshift" and "Kubernetes".
 */

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(TestMinimumKubernetesCount.MinimumKubernetesCountCondition.class)
public @interface TestMinimumKubernetesCount {
    int value();

    class MinimumKubernetesCountCondition implements ExecutionCondition {
        private final static Logger LOGGER = LoggerFactory.getLogger(MinimumKubernetesCountCondition.class);
        private static final ConditionEvaluationResult ENABLED = ConditionEvaluationResult.enabled("@TestMinimumKubernetesCount is not present");

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            AnnotatedElement element = context.getElement().orElse(null);
            String testname = context.getRequiredTestClass().getName();
            try {
                testname += context.getRequiredTestMethod().getName();
            } catch (PreconditionViolationException ignored) { }
            String finalTestname = testname;
            ConditionEvaluationResult result = findAnnotation(element, TestMinimumKubernetesCount.class).map(annotation -> toResult(element, annotation, finalTestname)).orElse(ENABLED);
            return result;
        }

        private ConditionEvaluationResult toResult(AnnotatedElement element, TestMinimumKubernetesCount annotation, String testName) {
            Collection<KubeClient> kubeClients = ResourceManager.getEnvironment().getKubeClients().values();
            if (kubeClients.size() >= annotation.value()) {
                return ConditionEvaluationResult.enabled("Test enabled on provided number of Kubernetes platforms");
            } else {
                LOGGER.warn("[TEST][{}] Disabling test - not enough Kubernetes platforms provided. Needed {}, got {}.", testName, annotation.value(), kubeClients.size());
                return ConditionEvaluationResult.disabled("[TEST] Skipped: Unsupported platform count for this test.");
            }
        }
    }
}
