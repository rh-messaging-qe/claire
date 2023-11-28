/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.helpers;

import io.brokerqe.claire.Constants;
import io.brokerqe.claire.EnvironmentOperator;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;

public class DataStorer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataStorer.class);
    private static String yamlDirectory = null;
    private static EnvironmentOperator environmentOperator = null;
    private static SerializationFormat dumpFormat;

    private static void initializeDumper() {
        if (environmentOperator == null) {
            environmentOperator = ResourceManager.getEnvironment();
        }
        yamlDirectory = environmentOperator.getSerializationDirectory();
        TestUtils.createDirectory(yamlDirectory);
        dumpFormat = environmentOperator.getSerializationFormat();
    }

    public static void dumpResourceToFile(List<HasMetadata> objects) {
        for (HasMetadata object : objects) {
            dumpResourceToFile(object);
        }
    }
    public static void dumpResourceToFile(HasMetadata object) {
        if (yamlDirectory == null || environmentOperator == null) {
            initializeDumper();
        }
        if (!environmentOperator.isSerializationEnabled()) {
            return;
        }
        String testDirName = getTestDirName();
        TestUtils.createDirectory(testDirName);
        String objectFilename = testDirName + Constants.FILE_SEPARATOR + object.getKind() + "_" + object.getMetadata().getName() + "_" +
                TestUtils.generateTimestamp() + "." + dumpFormat.toString().toLowerCase(Locale.ROOT);
        LOGGER.debug("Dumping object {} to {}", object.getMetadata().getName(), objectFilename);

        if (dumpFormat.equals(SerializationFormat.YAML)) {
            dumpResourceAsYaml(object, objectFilename);
        } else if (dumpFormat.equals(SerializationFormat.JSON)) {
            dumpResourceAsJson(object, objectFilename);
        } else {
            LOGGER.error("Unknown serialization format!");
        }
    }

    protected static void dumpResourceAsYaml(HasMetadata object, String objectFilename) {
        String data = Serialization.asYaml(object);
        TestUtils.createFile(objectFilename, data);
    }

    protected static void dumpResourceAsJson(HasMetadata object, String objectFilename) {
        String data = Serialization.asJson(object);
        TestUtils.createFile(objectFilename, data);
    }

    public static void storeCommand(String commandContent) {
        String testDirName = getTestDirName();
        TestUtils.createDirectory(testDirName);
        String commandFilename = testDirName + Constants.FILE_SEPARATOR + "commands.log";
        TestUtils.createDirectory(testDirName);
        TestUtils.appendToFile(commandFilename, commandContent + Constants.LINE_SEPARATOR);
        LOGGER.debug("Storing command {} to {}", commandContent, commandFilename);
    }

    private static String getTestDirName() {
        TestInfo testInfo = ResourceManager.getTestInfo();
        String testDirName = yamlDirectory;
        if (testInfo != null) {
            testDirName += Constants.FILE_SEPARATOR + testInfo.getTestClass().orElseThrow().getName();
            if (testInfo.getTestMethod().isPresent()) {
                testDirName += Constants.FILE_SEPARATOR + testInfo.getTestMethod().orElseThrow().getName();
            }
        }
        return testDirName;
    }
}
