/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.junit;

import io.brokerqe.claire.Environment;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

public class AbortCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (Environment.isAbortTestExecution()) {
            return ConditionEvaluationResult.disabled("Aborted due to previous failures. " +
                    "Too many failures from start. Possible misconfiguration of Environment?");
        }
        return ConditionEvaluationResult.enabled("OK");
    }
}
