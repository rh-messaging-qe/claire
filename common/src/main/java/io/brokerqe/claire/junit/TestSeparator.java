/*
 * Copyright Strimzi and Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.junit;

import io.brokerqe.claire.Constants;
import io.brokerqe.claire.Environment;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.security.CertificateManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

@ExtendWith(ExtensionContextParameterResolver.class)
public interface TestSeparator {
    Logger LOGGER = LoggerFactory.getLogger(TestSeparator.class);
    String SEPARATOR_CHAR = "-";

    @BeforeAll
    static void beforeAllTests(ExtensionContext testContext) {
        LOGGER.info((char) 27 + "[34m" + String.join("", Collections.nCopies(76, SEPARATOR_CHAR)) + (char) 27 + "[0m");
        LOGGER.info((char) 27 + "[33m" + String.format("Started Class: %s", testContext.getRequiredTestClass().getName()) + (char) 27 + "[0m");
        CertificateManager.setCertificateTestDirectory(TestUtils.getTestName(testContext));
    }
    @BeforeEach
    default void beforeEachTest(ExtensionContext testContext) {
        LOGGER.info((char) 27 + "[34m" + String.join("", Collections.nCopies(76, SEPARATOR_CHAR)) + (char) 27 + "[0m");
        LOGGER.info((char) 27 + "[33m" + String.format("[%s/%s] Started: %s.%s",
                ClaireExecutionListener.getCurrentTestCounter(), ClaireExecutionListener.getTotalTestCount(),
                testContext.getRequiredTestClass().getName(), testContext.getRequiredTestMethod().getName()) + (char) 27 + "[0m");
        CertificateManager.setCertificateTestDirectory(TestUtils.getTestName(testContext));
    }

    @AfterEach
    default void afterEachTest(ExtensionContext testContext) {
        LOGGER.info((char) 27 + "[33m" + String.format("Finished: %s.%s", testContext.getRequiredTestClass().getName(), testContext.getRequiredTestMethod().getName()) + (char) 27 + "[0m");
        LOGGER.info((char) 27 + "[34m" + String.join("", Collections.nCopies(76, SEPARATOR_CHAR)) + (char) 27 + "[0m");
        ClaireExecutionListener.incrementCurrentTestCounter();
    }

    @AfterAll
    static void afterAllTests(ExtensionContext testContext) {
        LOGGER.info((char) 27 + "[33m" + String.format("Finished Class: %s", testContext.getRequiredTestClass().getName()) + (char) 27 + "[0m");
        LOGGER.info((char) 27 + "[34m" + String.join("", Collections.nCopies(76, SEPARATOR_CHAR)) + (char) 27 + "[0m");
        TestUtils.deleteEmptyDirectories(Environment.get().getCertificatesLocation());
        TestUtils.deleteEmptyDirectories(Environment.get().getTmpDirLocation());
        TestUtils.createDirectory(Environment.get().getLogsDirLocation() + Constants.FILE_SEPARATOR);
        TestUtils.copyFile(Constants.LOGS_DEFAULT_DIR + "/claire.log", Environment.get().getLogsDirLocation() + Constants.FILE_SEPARATOR +  "claire.log");
    }
}
