/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.junit;

import io.brokerqe.claire.ResourceManager;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.yaml.snakeyaml.Yaml;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(DisableOnNoUpgradePlan.NoUpgradePlanTestCondition.class)
public @interface DisableOnNoUpgradePlan {

    class NoUpgradePlanTestCondition implements ExecutionCondition {

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            try {
                new Yaml().load(ResourceManager.getEnvironment().getTestUpgradePlanContent());
            } catch (IllegalArgumentException e) {
                return ConditionEvaluationResult.disabled("Test disabled as no upgrade plan provided");
            }
            return ConditionEvaluationResult.enabled("Upgrade plan provided, test enabled");
        }
    }
}
