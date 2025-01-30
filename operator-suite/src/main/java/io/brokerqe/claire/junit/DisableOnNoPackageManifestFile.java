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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(DisableOnNoPackageManifestFile.NoUpgradePlanTestCondition.class)
public @interface DisableOnNoPackageManifestFile {

    class NoUpgradePlanTestCondition implements ExecutionCondition {

        private final static Logger LOGGER = LoggerFactory.getLogger(DisabledTestPlatform.DisabledTestPlatformCondition.class);

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            try {
                ResourceManager.getEnvironment().getTestUpgradePackageManifestContent();
            } catch (IllegalArgumentException e) {
                LOGGER.warn("[TEST] Test disabled as no PackageManifest file was provided.");
                return ConditionEvaluationResult.disabled("Test disabled as no PackageManifest file was provided");
            }
            return ConditionEvaluationResult.enabled("PackageManifest file provided, test enabled");
        }
    }
}
