/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.junit;

import io.brokerqe.claire.KubeClient;
import io.brokerqe.claire.KubernetesArchitecture;
import io.brokerqe.claire.ResourceManager;
import io.fabric8.kubernetes.api.model.Node;
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
import java.util.Locale;
import java.util.stream.Collectors;

import static org.junit.platform.commons.util.AnnotationUtils.findAnnotation;

/**
 * This annotation disables execution of test/s for specified architecture.
 * Supported values are "AMD64", "ARM64", "PPC64LE" and "S390X".
 */

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(DisabledTestArchitecture.SupportedPlatformTestCondition.class)
public @interface DisabledTestArchitecture {
    KubernetesArchitecture[] archs();

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
            ConditionEvaluationResult result = findAnnotation(element, DisabledTestArchitecture.class).map(annotation -> toResult(element, annotation, finalTestname)).orElse(ENABLED);
            return result;
        }

        private ConditionEvaluationResult toResult(AnnotatedElement element, DisabledTestArchitecture annotation, String testName) {
            // convert Array of KubernetesArchitecture Enums to item.name() strings list
            List<String> disabledArchNames = Arrays.stream(annotation.archs()).toList().stream().map(KubernetesArchitecture::name).collect(Collectors.toList());
            Collection<KubeClient> kubeClients = ResourceManager.getEnvironment().getKubeClients().values();

            for (KubeClient kubeClient : kubeClients) {
                List<Node> nodes = kubeClient.getKubernetesClient().nodes().list().getItems();
                for (Node node : nodes) {
                    String nodeArch = node.getMetadata().getLabels().get("kubernetes.io/arch");
                    if (disabledArchNames.contains(nodeArch.toUpperCase(Locale.ROOT))) {
                        LOGGER.trace("{} in {}", nodeArch, disabledArchNames);
                        LOGGER.info("[TEST][{}] Skipped: Test/class can't be executed on {} DisabledTestArchitecture criteria.", testName, nodeArch);
                        return ConditionEvaluationResult.disabled("[TEST] Skipped: Unsupported architecture for this test. " + nodeArch);
                    } else {
                        LOGGER.trace("{} not in {}", nodeArch, disabledArchNames);
                        return ConditionEvaluationResult.enabled("[TEST] Enabled on this architecture " + nodeArch);
                    }
                }
            }
            return ConditionEvaluationResult.disabled("[TEST] No nodes info found. Disabling test.");
        }
    }
}
