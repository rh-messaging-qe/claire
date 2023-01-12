/*
 * Copyright Strimzi and Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.junit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Collections;

@ExtendWith(ExtensionContextParameterResolver.class)
public interface TestSeparator {
    Logger LOGGER = LoggerFactory.getLogger(TestSeparator.class);
    String SEPARATOR_CHAR = "-";

    @BeforeEach
    default void beforeEachTest(ExtensionContext testContext) {
        LOGGER.info((char) 27 + "[34m" + String.join("", Collections.nCopies(76, SEPARATOR_CHAR)) + (char) 27 + "[0m");
        LOGGER.info((char) 27 + "[33m" + String.format("Started: %s.%s", testContext.getRequiredTestClass().getName(), testContext.getRequiredTestMethod().getName()) + (char) 27 + "[0m");
    }

    @AfterEach
    default void afterEachTest(ExtensionContext testContext) {
        LOGGER.info((char) 27 + "[33m" + String.format("Finished: %s.%s", testContext.getRequiredTestClass().getName(), testContext.getRequiredTestMethod().getName()) + (char) 27 + "[0m");
        LOGGER.info((char) 27 + "[34m" + String.join("", Collections.nCopies(76, SEPARATOR_CHAR)) + (char) 27 + "[0m");
    }
}
