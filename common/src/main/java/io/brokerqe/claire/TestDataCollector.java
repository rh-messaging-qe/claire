/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.LifecycleMethodExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestWatcher;
import org.junit.platform.commons.PreconditionViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;


public abstract class TestDataCollector implements TestWatcher, TestExecutionExceptionHandler, LifecycleMethodExecutionExceptionHandler {

    static final Logger LOGGER = LoggerFactory.getLogger(TestDataCollector.class);
    static String archiveDir;
    Environment environment;
    String testClass;
    String testMethod;
    Object testInstance;

    @Override
    public void handleBeforeAllMethodExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        prepareCollectLogProperties(context, throwable);
        collectTestData();
        throw throwable;
    }

    @Override
    public void handleBeforeEachMethodExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        prepareCollectLogProperties(context, throwable);
        collectTestData();
        throw throwable;
    }

    @Override
    public void handleAfterEachMethodExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        prepareCollectLogProperties(context, throwable);
        collectTestData();
        throw throwable;
    }

    @Override
    public void handleAfterAllMethodExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        prepareCollectLogProperties(context, throwable);
        collectTestData();
        throw throwable;
    }

    @Override
    public void handleTestExecutionException(ExtensionContext extensionContext, Throwable throwable) throws Throwable {
        prepareCollectLogProperties(extensionContext, throwable);
        collectTestData();
        throw throwable;
    }

    private void prepareCollectLogProperties(ExtensionContext extensionContext, Throwable throwable) throws Throwable {
        environment = Environment.get();
        if (!environment.isCollectTestData()) {
            LOGGER.info("Gathering of debug data is disabled!");
            throw throwable;
        }
        testClass = extensionContext.getRequiredTestClass().getName();
        try {
            testMethod = extensionContext.getRequiredTestMethod().getName();
        } catch (PreconditionViolationException e) {
            testMethod = "no-test-method";
        }
        testInstance = extensionContext.getRequiredTestInstance();

        String classDir = TestUtils.getClassName(extensionContext);
        String testDir = TestUtils.getTestName(extensionContext);
        archiveDir = environment.getLogsDirLocation() + Constants.FILE_SEPARATOR + testDir.replaceFirst("io.brokerqe.claire.", "");
        String certificatesDir = Environment.get().getCertificatesLocation() + Constants.FILE_SEPARATOR + testDir;
        String certificatesDirClass = Environment.get().getCertificatesLocation() + Constants.FILE_SEPARATOR + classDir;
        createDirectory(archiveDir, certificatesDir, certificatesDirClass);
    }

    private void createDirectory(String archiveDir, String certificatesDir, String certificatesDirClass) {
        TestUtils.createDirectory(archiveDir);
        String certificatesArchiveDirectory = archiveDir + Constants.FILE_SEPARATOR + "certificates";
        String certificatesArchiveDirectoryClass = archiveDir + Constants.FILE_SEPARATOR + "class_certificates";
        if (!TestUtils.isEmptyDirectory(certificatesDirClass) || !TestUtils.isEmptyDirectory(certificatesDir)) {
            if (TestUtils.directoryExists(certificatesArchiveDirectory)) {
                LOGGER.warn("[TDC] Skipping duplicated copying of certificates into {}", certificatesArchiveDirectory);
            } else {
                TestUtils.copyDirectoryFlat(certificatesDir, certificatesArchiveDirectory);
                TestUtils.copyDirectoryFlat(certificatesDirClass, certificatesArchiveDirectoryClass);
            }
        }
    }

    /**
     * Method is currently not used, but might be useful in future (getting variable from class by name)
     * @param testInstance
     * @param fieldName
     * @return
     */
    public Object getTestInstanceDeclaredField(Object testInstance, String fieldName) {
        Field field = null;
        Class<?> clazz = testInstance.getClass();
        while (clazz != null && field == null) {
            try {
                field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(testInstance);
            } catch (IllegalAccessException | NoSuchFieldException e) {
                LOGGER.trace("DeclaredField {} not found in class {}, trying superclass()", fieldName, clazz);
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }


    abstract void collectTestData();

}
