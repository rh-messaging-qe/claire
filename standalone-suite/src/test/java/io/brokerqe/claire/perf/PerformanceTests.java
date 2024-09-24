/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.perf;

import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.Environment;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.client.deployment.ArtemisDeployment;
import io.brokerqe.claire.client.deployment.BundledClientDeployment;
import io.brokerqe.claire.clients.DeployableClient;
import io.brokerqe.claire.clients.bundled.ArtemisCommand;
import io.brokerqe.claire.clients.bundled.BundledArtemisClient;
import io.brokerqe.claire.container.ArtemisContainer;
import io.brokerqe.claire.smoke.ClientsMessagingTests;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class PerformanceTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientsMessagingTests.class);
    private String testName;
    private String testNameDir;
    protected String artemisVersion;
    protected Map<String, String> results = new HashMap<>();
    ArtemisContainer artemis;

    @BeforeAll
    void setupEnv() {
        String artemisName = "artemis";
        artemisVersion = Environment.get().getArtemisVersion();
        LOGGER.info("Creating artemis instance: " + artemisName);
        artemis = ArtemisDeployment.getArtemisInstance(artemisName);
        TestUtils.deleteDirectoryRecursively(Path.of(Constants.PERFORMANCE_DIR));
        TestUtils.createDirectory(Constants.PERFORMANCE_DIR);
    }

    @BeforeEach
    void init(TestInfo testInfo) {
        this.testInfo = testInfo;
        testName = testInfo.getTestMethod().orElseThrow().getName().toLowerCase(Locale.ROOT);
        testNameDir = Constants.PERFORMANCE_DIR + "/" + testName;
        TestUtils.createDirectory(testNameDir);
    }

    void storeResults(String results, String hdrFilename, String reportFilename) {
        String perfFilenameHost = testNameDir + "/results_" + artemisVersion;
        String hdrFilenameHost = testNameDir + "/" + hdrFilename;
        String reportFilenameHost = testNameDir + "/" + reportFilename;
        artemis.copyFileFrom(ArtemisContainer.ARTEMIS_INSTANCE_DIR + "/" + hdrFilename, hdrFilenameHost);
        artemis.copyFileFrom(ArtemisContainer.ARTEMIS_INSTANCE_DIR + "/" + reportFilename, reportFilenameHost);
        TestUtils.createFile(perfFilenameHost, results);
    }

    @ParameterizedTest
    @ValueSource(strings = {"amqp"})
//    @ValueSource(strings = {"amqp", "core"})
    public void testSingleQueue(String protocol) {
        LOGGER.info("Test Performance of {} messaging", protocol);
        String hdrFilename = testName + "_" + protocol + "_" + artemisVersion + ".hdr";
        String reportFilename = testName + "_" + protocol + "_" + artemisVersion + "_report.json";
        DeployableClient deployableClient = new BundledClientDeployment();
        Map<String, String> artemisQueueStatOptions = Map.of(
                "duration", "120",
                "protocol", protocol,
                "producers", "1",
                "consumers", "1",
                "threads", "1",
                "warmup", "10",
                "show-latency", "",
                "hdr", hdrFilename,
                "json", reportFilename
        );
        BundledArtemisClient artemisClient = new BundledArtemisClient(deployableClient, ArtemisCommand.PERF_CLIENT, artemisQueueStatOptions);
        Map<String, String> perfOutput = (Map<String, String>) artemisClient.executeCommand();
        LOGGER.info(perfOutput.toString());

        assertThat("performance test ended successfully", perfOutput.get("result"), equalTo("success"));
        assertThat("total send = total received", perfOutput.get("total_sent"), equalTo(perfOutput.get("total_received")));

        storeResults(TestUtils.convertMapToJson(perfOutput), hdrFilename, reportFilename);
    }

    @ParameterizedTest
    @ValueSource(strings = {"amqp", "core"})
    public void testSingleQueuePersistent(String protocol) {
        LOGGER.info("Test Performance of {} messaging", protocol);
        String hdrFilename = testName + "_" + protocol + "_" + artemisVersion + ".hdr";
        String reportFilename = testName + "_" + protocol + "_" + artemisVersion + "_report.json";
        DeployableClient deployableClient = new BundledClientDeployment();
        Map<String, String> artemisQueueStatOptions = Map.of(
                "duration", "120",
                "protocol", protocol,
                "producers", "1",
                "consumers", "1",
                "threads", "1",
                "warmup", "10",
                "persistent", "",
                "show-latency", "",
                "hdr", hdrFilename,
                "json", reportFilename
        );
        BundledArtemisClient artemisClient = new BundledArtemisClient(deployableClient, ArtemisCommand.PERF_CLIENT, artemisQueueStatOptions);
        Map<String, String> perfOutput = (Map<String, String>) artemisClient.executeCommand();
        LOGGER.info(perfOutput.toString());

        assertThat("performance test ended successfully", perfOutput.get("result"), equalTo("success"));
        assertThat("total send = total received", perfOutput.get("total_sent"), equalTo(perfOutput.get("total_received")));
        storeResults(TestUtils.convertMapToJson(perfOutput), hdrFilename, reportFilename);
    }

    @ParameterizedTest
    @ValueSource(strings = {"amqp", "core"})
    public void testSingleQueueRate(String protocol) {
        LOGGER.info("Test Performance of {} messaging", protocol);
        String hdrFilename = testName + "_" + protocol + "_" + artemisVersion + ".hdr";
        String reportFilename = testName + "_" + protocol + "_" + artemisVersion + "_report.json";
        DeployableClient deployableClient = new BundledClientDeployment();
        Map<String, String> artemisQueueStatOptions = Map.of(
                // --rate 30000 --hdr /tmp/30K.hdr --warmup 20 --max-pending 100 --show-latency --url tcp://localhost:61616?confirmationWindowSize=20000 --consumer-url tcp://localhost:61616 queue://TEST_QUEUE
                "duration", "120",
                "rate", "30000",
                "protocol", protocol,
                "warmup", "20",
                "show-latency", "",
                "url", "tcp://localhost:61616?confirmationWindowSize=20000",
                "consumer-url", "tcp://localhost:61616",
                "hdr", hdrFilename,
                "json", reportFilename
        );
        BundledArtemisClient artemisClient = new BundledArtemisClient(deployableClient, ArtemisCommand.PERF_CLIENT, artemisQueueStatOptions);
        Map<String, String> perfOutput = (Map<String, String>) artemisClient.executeCommand();
        LOGGER.info(perfOutput.toString());

        assertThat("performance test ended successfully", perfOutput.get("result"), equalTo("success"));
        assertThat("total send = total received", perfOutput.get("total_sent"), equalTo(perfOutput.get("total_received")));
        storeResults(TestUtils.convertMapToJson(perfOutput), hdrFilename, reportFilename);
    }

    @ParameterizedTest
    @ValueSource(strings = {"amqp", "core"})
    public void test10Topics3producers2consumers(String protocol) {
        LOGGER.info("Test Performance of {} messaging", protocol);
        String hdrFilename = testName + "_" + protocol + "_" + artemisVersion + ".hdr";
        String reportFilename = testName + "_" + protocol + "_" + artemisVersion + "_report.json";
        DeployableClient deployableClient = new BundledClientDeployment();
        // --warmup 20 --max-pending 100 --show-latency --url tcp://localhost:61616?confirmationWindowSize=20000 --consumer-url tcp://localhost:61616                 // --producers 3 --consumers 2 --num-destinations 10 --durable --persistent topic://DURABLE_TOPIC
        Map<String, String> artemisQueueStatOptions = Map.ofEntries(
                Map.entry("duration", "180"),
                Map.entry("warmup", "20"),
                Map.entry("max-pending", "100"),
                Map.entry("show-latency", ""),
                Map.entry("url", "tcp://localhost:61616?confirmationWindowSize=20000"),
                Map.entry("consumer-url", "tcp://localhost:61616"),
                Map.entry("producers", "3"),
                Map.entry("consumers", "2"),
                Map.entry("clientID", "tralala"),
                Map.entry("num-destinations", "10"),
                Map.entry("durable", ""),
                Map.entry("persistent", ""),
                Map.entry("protocol", protocol),
//                Map.entry("rate", "60000"),
                Map.entry("threads", "3"),
                Map.entry("hdr", hdrFilename),
                Map.entry("json", reportFilename)
        );
        BundledArtemisClient artemisClient = new BundledArtemisClient(deployableClient, ArtemisCommand.PERF_CLIENT, artemisQueueStatOptions, "topic://DURABLE_TOPIC");
        Map<String, String> perfOutput = (Map<String, String>) artemisClient.executeCommand();
        LOGGER.info(perfOutput.toString());
        storeResults(TestUtils.convertMapToJson(perfOutput), hdrFilename, reportFilename);
    }
}
