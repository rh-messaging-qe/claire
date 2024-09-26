/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.ha.replication;

import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.client.JmsClient;
import io.brokerqe.claire.client.AmqpUtil;
import io.brokerqe.claire.client.deployment.ArtemisDeployment;
import io.brokerqe.claire.container.ArtemisContainer;
import io.brokerqe.claire.container.ZookeeperContainerCluster;
import jakarta.jms.Message;
import jakarta.jms.Queue;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class FailoverReplicationTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(FailoverReplicationTests.class);
    private ArtemisContainer artemisPrimary;
    private ArtemisContainer artemisBackup;

    @BeforeAll
    void setupEnv() {
        // create a Zookeeper cluster of 3 nodes
        ZookeeperContainerCluster zkCluster = new ZookeeperContainerCluster(3, "zk");
        zkCluster.start();

        String artemisPrimaryName = "artemisPrimary";
        LOGGER.info("Creating artemis instance: " + artemisPrimaryName);
        String primaryTuneFile = ArtemisDeployment.generateYacfgProfilesContainerTestDir("primary-tune.yaml.jinja2", getPkgClassAsDir());
        artemisPrimary = ArtemisDeployment.getArtemisInstance(artemisPrimaryName, primaryTuneFile);

        String artemisBackupName = "artemisBackup";
        LOGGER.info("Creating artemis instance: " + artemisBackupName);
        String backupTuneFile = ArtemisDeployment.generateYacfgProfilesContainerTestDir("backup-tune.yaml.jinja2", getPkgClassAsDir());
        artemisBackup = ArtemisDeployment.getArtemisInstance(artemisBackupName, backupTuneFile, true);
    }

    @Test
    @Tag(Constants.TAG_SMOKE)
    void produceOnPrimaryConsumeOnBackup() {
        int numOfMessages = 100;
        String addressName = "TestQueue1";
        String queueName = "TestQueue1";

        // create a qpid jms client
        String primaryAmqpHostAndPort = artemisPrimary.getHostAndPort(ArtemisConstants.DEFAULT_ALL_PROTOCOLS_PORT);
        String backupAmqpHostAndPort = artemisBackup.getHostAndPort(ArtemisConstants.DEFAULT_ALL_PROTOCOLS_PORT);
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
        artemisPrimary.ensureQueueCount(addressName, queueName, RoutingType.ANYCAST, numOfMessages);

        // ensure replica is in sync
        artemisBackup.ensureBrokerReplicaIsInSync();

        // stop the primary instance
        artemisPrimary.stop();

        // ensure the backup instance became the current live
        artemisBackup.ensureBrokerIsLive();

        LOGGER.info("Ensure queue contains {} messages on backup", numOfMessages);
        artemisBackup.ensureQueueCount(addressName, queueName, RoutingType.ANYCAST, numOfMessages);

        LOGGER.info("Consuming {} messages from queue {} on backup", numOfMessages, queueName);
        client.consume(numOfMessages);
        Map<String, Message> consumedMsgs = client.getConsumedMsgs();

        LOGGER.info("Ensure queue is empty");
        artemisBackup.ensureQueueCount(addressName, queueName, RoutingType.ANYCAST, 0);

        LOGGER.info("Ensuring produced and consumed messages are the same");
        ensureSameMessages(numOfMessages, producedMsgs, consumedMsgs);
    }
}
