/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.upgrade;

import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.client.AmqpUtil;
import io.brokerqe.claire.client.JmsClient;
import io.brokerqe.claire.container.ArtemisContainer;
import jakarta.jms.Message;
import jakarta.jms.Queue;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.aggregator.ArgumentsAccessor;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

@Disabled
public class HAReplicationUpgradeTests extends AbstractSystemTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(HAReplicationUpgradeTests.class);
    private ArtemisContainer artemisPrimary0;
    private ArtemisContainer artemisPrimary1;
    private ArtemisContainer artemisPrimary2;
    private ArtemisContainer artemisBackup0;
    private ArtemisContainer artemisBackup1;
    private ArtemisContainer artemisBackup2;


    Stream<? extends Arguments> getUpgradePlanArguments() {
        ArrayList<HashMap<String, String>> mapped = new Yaml().load(getEnvironment().getTestUpgradePlanContent());
        return  mapped.stream().map(line ->
                Arguments.of(line.get("version"), line.get("channel"), line.get("indexImageBundle"))
        );
    }

    @BeforeAll
    void setupEnvironment() {
        LOGGER.info("[UpgradeTestPlan] {}", getEnvironment().getTestUpgradePlanContent());
//        for (int i : new int[]{0, 1, 2}) {
        String artemisPrimaryName = "artemisPrimary";
        LOGGER.info("Creating artemis instance: " + artemisPrimaryName);
        String primaryTuneFile = generateYacfgProfilesContainerTestDir("primary-tune.yaml.jinja2");
        artemisPrimary0 = getArtemisInstance(artemisPrimaryName + 0, primaryTuneFile);
        artemisPrimary1 = getArtemisInstance(artemisPrimaryName + 1, primaryTuneFile);
//            artemisPrimary2 = getArtemisInstance(artemisPrimaryName + 2, primaryTuneFile);

        String artemisBackupName = "artemisBackup";
        LOGGER.info("Creating artemis instance: " + artemisBackupName);
        String backupTuneFile = generateYacfgProfilesContainerTestDir("backup-tune.yaml.jinja2");
        artemisBackup0 = getArtemisInstance(artemisBackupName + 0, backupTuneFile, true);
        artemisBackup1 = getArtemisInstance(artemisBackupName + 1, backupTuneFile, true);
//            artemisBackup2 = getArtemisInstance(artemisBackupName + 2, backupTuneFile, true);
//        }
    }

    @ParameterizedTest
    @MethodSource("getUpgradePlanArguments")
    void microUpgradeHA(ArgumentsAccessor argumentsAccessor) {
        String version = argumentsAccessor.getString(0);
        String channel = argumentsAccessor.getString(1);
        String iib = argumentsAccessor.getString(2);

        int numOfMessages = 100;
        String addressName = "TestQueue1";
        String queueName = "TestQueue1";

        // create a qpid jms client
        String primaryAmqpHostAndPort = artemisPrimary0.getHostAndPort(ArtemisConstants.DEFAULT_ALL_PROTOCOLS_PORT);
        String backupAmqpHostAndPort = artemisBackup0.getHostAndPort(ArtemisConstants.DEFAULT_ALL_PROTOCOLS_PORT);
        String amqpOpts = "failover.amqpOpenServerListAction=IGNORE";
        String url = AmqpUtil.buildAmqFailoverUrl(amqpOpts, primaryAmqpHostAndPort, backupAmqpHostAndPort);

        LOGGER.info("Creating client");
        JmsClient client = ResourceManager.getJmsClient("client-1", new JmsConnectionFactory(url))
                .withCredentials(ArtemisConstants.ADMIN_NAME, ArtemisConstants.ADMIN_PASS)
                .withDestination(Queue.class, queueName);

        LOGGER.info("Producing {} messages to queue {}", numOfMessages, queueName);
        client.produce(numOfMessages);
        Map<String, Message> producedMsgs = client.getProducedMsgs();

        LOGGER.info("Ensure queue contains {} messages on primary", numOfMessages);
        ensureQueueCount(artemisPrimary0, addressName, queueName, RoutingType.ANYCAST, numOfMessages);

        // ensure replica is in sync
        ensureBrokerReplicaIsInSync(artemisBackup0);

        // stop the primary instance
        artemisPrimary0.stop();

        // ensure the backup instance became the current live
        ensureBrokerIsLive(artemisBackup0);

        LOGGER.info("Ensure queue contains {} messages on backup", numOfMessages);
        ensureQueueCount(artemisBackup0, addressName, queueName, RoutingType.ANYCAST, numOfMessages);

        LOGGER.info("Consuming {} messages from queue {} on backup", numOfMessages, queueName);
        client.consume(numOfMessages);
        Map<String, Message> consumedMsgs = client.getConsumedMsgs();

        LOGGER.info("Ensure queue is empty");
        ensureQueueCount(artemisBackup0, addressName, queueName, RoutingType.ANYCAST, 0);

        LOGGER.info("Ensuring produced and consumed messages are the same");
        ensureSameMessages(numOfMessages, producedMsgs, consumedMsgs);
    }

}
