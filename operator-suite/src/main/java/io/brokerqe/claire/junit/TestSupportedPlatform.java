/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.junit;

import io.brokerqe.claire.KubeClient;
import io.brokerqe.claire.KubernetesPlatform;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
        private final static Logger LOGGER = LoggerFactory.getLogger(SupportedPlatformTestCondition.class);
        private static final ConditionEvaluationResult ENABLED = ConditionEvaluationResult.enabled("@PlatformTest is not present");

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            AnnotatedElement element = context.getElement().orElse(null);
            String testname = context.getRequiredTestClass().getName();
            try {
                testname += context.getRequiredTestMethod().getName();
            } catch (PreconditionViolationException ignored) { }
            String finalTestname = testname;
            ConditionEvaluationResult result = findAnnotation(element, TestSupportedPlatform.class).map(annotation -> toResult(element, annotation, finalTestname)).orElse(ENABLED);
            return result;
        }

        private ConditionEvaluationResult toResult(AnnotatedElement element, TestSupportedPlatform annotation, String testName) {
            Collection<KubeClient> kubeClients = ResourceManager.getEnvironment().getKubeClients().values();
            List<ConditionEvaluationResult> resultList = new ArrayList<>();
            for (KubeClient kubeClient : kubeClients) {
                KubernetesPlatform platformValue = annotation.value();
                if (kubeClient.isKubernetesPlatform() && platformValue.equals(KubernetesPlatform.KUBERNETES)) {
                    resultList.add(ConditionEvaluationResult.enabled("Test enabled on Kubernetes-like platforms"));
                    continue;
                }

                if (kubeClient.isOpenshiftPlatform() && platformValue.equals(KubernetesPlatform.OPENSHIFT)) {
                    resultList.add(ConditionEvaluationResult.enabled("Test enabled on Openshift"));
                    continue;
                }

                LOGGER.info("[TEST][{}] Skipped: Test/class does not meet TestValidSince criteria.", testName);
                return ConditionEvaluationResult.disabled("[TEST] Skipped: Unsupported platform for this test." + kubeClient.getKubernetesPlatform());
            }
            if (resultList.stream().allMatch(resultList.get(0)::equals)) {
                return resultList.get(0);
            } else {
                LOGGER.warn("[TEST][{}]Disabling test - Detected mixture of provided platforms. This is not supported.", testName);
                return ConditionEvaluationResult.disabled("[TEST] Skipped: Unsupported platform for this test.");
            }
        }
    }
}
