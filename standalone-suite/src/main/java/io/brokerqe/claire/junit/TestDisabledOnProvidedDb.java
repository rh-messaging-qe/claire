/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.junit;

import io.brokerqe.claire.Environment;
import io.brokerqe.claire.container.database.ProvidedDatabase;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation ignores test on Provided DB - due to some network limitation for example.
 */

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(TestDisabledOnProvidedDb.SupportedDatabaseTestCondition.class)
public @interface TestDisabledOnProvidedDb {

    class SupportedDatabaseTestCondition implements ExecutionCondition {

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            if (Environment.get().getDatabase() instanceof ProvidedDatabase) {
                return ConditionEvaluationResult.disabled("Test disabled on ProvidedDb");
            } else {
                return ConditionEvaluationResult.enabled("Test enabled on no Db or DeployedDb");
            }
        }
    }
}
