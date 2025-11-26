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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.platform.commons.util.AnnotationUtils.findAnnotation;

/**
 * This annotation disables execution of test/s for specified platforms.
 * Supported values are "KUBERNETES", "OPENSHIFT" and "MICROSHIFT".
 */

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(DisabledTestPlatform.DisabledTestPlatformCondition.class)
public @interface DisabledTestPlatform {
    KubernetesPlatform[] platforms();

    class DisabledTestPlatformCondition implements ExecutionCondition {
        private final static Logger LOGGER = LoggerFactory.getLogger(DisabledTestPlatformCondition.class);
        private static final ConditionEvaluationResult ENABLED = ConditionEvaluationResult.enabled("@DisabledTestPlatform is not present");

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            AnnotatedElement element = context.getElement().orElse(null);
            String testName = context.getRequiredTestClass().getName();
            try {
                testName += context.getRequiredTestMethod().getName();
            } catch (PreconditionViolationException ignored) { }
            String finalTestName = testName;
            return findAnnotation(element, DisabledTestPlatform.class).map(annotation -> toResult(element, annotation, finalTestName)).orElse(ENABLED);
        }

        private ConditionEvaluationResult toResult(AnnotatedElement element, DisabledTestPlatform annotation, String testName) {
            List<String> disabledPlatformNames = Arrays.stream(annotation.platforms()).toList().stream().map(KubernetesPlatform::name).collect(Collectors.toList());
            Collection<KubeClient> kubeClients = ResourceManager.getEnvironment().getKubeClients().values();

            for (KubeClient kubeClient : kubeClients) {
                if (kubeClient.isAwseksPlatform()) {
                    if (disabledPlatformNames.contains(KubernetesPlatform.AWS_EKS.toString())) {
                        LOGGER.trace("isAwseksPlatform: {}, DisabledTestPlatforms: {}", kubeClient.isKubernetesPlatform(), disabledPlatformNames);
                        LOGGER.info("[TEST][{}] Skipped: Test/class can't be executed on AWS_EKS based on DisabledTestPlatform criteria.", testName);
                        return ConditionEvaluationResult.disabled("[TEST] Skipped: Unsupported on platform: " + kubeClient.getKubernetesPlatform());
                    } else {
                        return ConditionEvaluationResult.enabled("[TEST] Enabled: Supported on platform: " + kubeClient.getKubernetesPlatform());
                    }
                }
                if (kubeClient.isKubernetesPlatform() && disabledPlatformNames.contains(KubernetesPlatform.KUBERNETES.toString())) {
                    LOGGER.trace("isKubernetesPlatform: {}, DisabledTestPlatforms: {}", kubeClient.isKubernetesPlatform(), disabledPlatformNames);
                    LOGGER.info("[TEST][{}] Skipped: Test/class can't be executed on kubernetes based on DisabledTestPlatform criteria.", testName);
                    return ConditionEvaluationResult.disabled("[TEST] Skipped: Unsupported on platform: " + kubeClient.getKubernetesPlatform());
                }
                if (kubeClient.isOpenshiftPlatform() && disabledPlatformNames.contains(KubernetesPlatform.OPENSHIFT.toString())) {
                    LOGGER.trace("isOpenshiftPlatform: {}, DisabledTestPlatforms: {}", kubeClient.isOpenshiftPlatform(), disabledPlatformNames);
                    LOGGER.info("[TEST][{}] Skipped: Test/class can't be executed on openshift based on DisabledTestPlatform criteria.", testName);
                    return ConditionEvaluationResult.disabled("[TEST] Skipped: Unsupported on platform: " + kubeClient.getKubernetesPlatform());
                }

                if (kubeClient.isMicroshiftPlatform() && disabledPlatformNames.contains(KubernetesPlatform.MICROSHIFT.toString())) {
                    LOGGER.trace("isMicroshiftPlatform: {}, DisabledTestPlatforms: {}", kubeClient.isMicroshiftPlatform(), disabledPlatformNames);
                    LOGGER.info("[TEST][{}] Skipped: Test/class can't be executed on microshift based on DisabledTestPlatform criteria.", testName);
                    return ConditionEvaluationResult.disabled("[TEST] Skipped: Unsupported on platform: " + kubeClient.getKubernetesPlatform());
                }
            }

            LOGGER.debug("[TEST][{}]: Test/class do not meet DisabledTestPlatformCondition criteria.", testName);
            return ConditionEvaluationResult.enabled("[TEST] Enabled on this platform");
        }
    }
}
