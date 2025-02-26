/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.logging.general;

import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.client.deployment.ArtemisConfigData;
import io.brokerqe.claire.client.deployment.ArtemisDeployment;
import io.brokerqe.claire.client.deployment.BundledClientDeployment;
import io.brokerqe.claire.clients.MessagingClient;
import io.brokerqe.claire.clients.MessagingClientException;
import io.brokerqe.claire.clients.bundled.BundledClientOptions;
import io.brokerqe.claire.clients.bundled.BundledCoreMessagingClient;
import io.brokerqe.claire.container.ArtemisContainer;
import io.brokerqe.claire.exception.ClaireRuntimeException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class LogPropertiesTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(io.brokerqe.claire.logging.general.LogPropertiesTests.class);
    private ArtemisContainer artemis;
    private BundledClientDeployment artemisDeployableClient;
    private Path artemisLogPath, artemisLogPathDir;
    private final String invalidUser = "invalidUser";
    private final String invalidPass = "invalidPass";

    @BeforeAll
    void setupEnv() {
        String artemisName = "artemis";
        LOGGER.info("Creating artemis instance: " + artemisName);
        artemis = ArtemisDeployment.createArtemis(artemisName, new ArtemisConfigData().withTuneFile("tune.yaml.jinja2"));
        artemisDeployableClient = new BundledClientDeployment();
        artemisLogPathDir = Path.of(getTestConfigDir(), artemis.getName(), ArtemisConstants.LOG_DIR);
        artemisLogPath = Path.of(getTestConfigDir(), artemis.getName(), ArtemisConstants.LOG_DIR, ArtemisConstants.ARTEMIS_LOG_FILE);
    }

    private List<Path> listLogGzFiles(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString();
                        return fileName.startsWith("artemis.log.") && fileName.endsWith(".gz");
                    })
                    .collect(Collectors.toList());
        } catch (IOException e) {
            String errMsg = String.format("Error reading directory %s", e.getMessage());
            LOGGER.error(errMsg);
            throw new ClaireRuntimeException(errMsg, e);
        }
    }

    @Test
    // ENTMQBR-9064
    void logFileRotationTest() {

        TestUtils.waitFor("for log file to rotate", Constants.DURATION_10_SECONDS, Constants.DURATION_2_MINUTES, () -> {
            String artemisLog = TestUtils.readFileContent(artemisLogPath.toFile());
            return artemisLog.isEmpty();
        });

        LOGGER.info("Assert a new generated log file in .gz format is present");
        List<Path> gzFiles = listLogGzFiles(artemisLogPathDir);
        LOGGER.debug("log files that end with .gz present in dir: {}", gzFiles.toString());
        assertEquals(1, gzFiles.size());

        LOGGER.info("Try to send messages with incorrect credentials, with Bundled Core Messaging");
        int msgsExpected = 5;
        String address = "lala";
        String queue = "lala";
        BundledClientOptions options = new BundledClientOptions()
                .withDeployableClient(artemisDeployableClient)
                .withDestinationAddress(address)
                .withDestinationPort(DEFAULT_ALL_PORT)
                .withMessageCount(msgsExpected)
                .withPassword(invalidPass)
                .withUsername(invalidUser)
                .withDestinationQueue(queue)
                .withDestinationUrl(artemis.getDefaultBrokerUri());
        MessagingClient bundledClient = new BundledCoreMessagingClient(options);
        try {
            bundledClient.sendMessages();
        } catch (MessagingClientException e) {
            LOGGER.info("As expected, failed to send messages: {}", e.getMessage(), e);
        }

        LOGGER.info("Assert that user validation message is in artemis.log file.");
        String artemisLog = TestUtils.readFileContent(artemisLogPath.toFile());
        String userValidationMsg = String.format(ArtemisConstants.LOG_PATTERN_FAILED_VALID_USER, invalidUser);
        LOGGER.debug("user validation message match: {}", userValidationMsg);
        Assertions.assertThat(artemisLog).containsPattern(userValidationMsg);
    }

}
