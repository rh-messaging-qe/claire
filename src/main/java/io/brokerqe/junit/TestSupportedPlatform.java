/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.junit;

import io.brokerqe.KubeClient;
import io.brokerqe.KubernetesPlatform;
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
@ExtendWith(TestSupportedPlatform.SupportedPlatformTestCondition.class)
public @interface TestSupportedPlatform {
    KubernetesPlatform value();

    class SupportedPlatformTestCondition implements ExecutionCondition {

        KubeClient kubeClient = new KubeClient("default");
        private final static Logger LOGGER = LoggerFactory.getLogger(SupportedPlatformTestCondition.class);
        private static final ConditionEvaluationResult ENABLED = ConditionEvaluationResult.enabled("@PlatformTest is not present");

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            AnnotatedElement element = context.getElement().orElse(null);
            ConditionEvaluationResult result = findAnnotation(element, TestSupportedPlatform.class).map(annotation -> toResult(element, annotation)).orElse(ENABLED);
            kubeClient.getKubernetesClient().close();
            return result;
        }

        private ConditionEvaluationResult toResult(AnnotatedElement element, TestSupportedPlatform annotation) {
            KubernetesPlatform platformValue = annotation.value();
            if (kubeClient.isKubernetesPlatform() && platformValue.equals(KubernetesPlatform.KUBERNETES)) {
                return ConditionEvaluationResult.enabled("Test enabled on Kubernetes-like platforms");
            }

            if (kubeClient.isOpenshiftPlatform() && platformValue.equals(KubernetesPlatform.OPENSHIFT)) {
                return ConditionEvaluationResult.enabled("Test enabled on Openshift");
            }

            LOGGER.info("[TEST] Skipped: Test/class does not meet TestValidSince criteria.");
            return ConditionEvaluationResult.disabled("[TEST] Skipped: Unsupported platform for this test.");
        }
    }
}
