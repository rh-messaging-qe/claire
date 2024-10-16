/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.smoke;

import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.client.AmqpUtil;
import io.brokerqe.claire.client.JmsClient;
import io.brokerqe.claire.client.deployment.ArtemisDeployment;
import io.brokerqe.claire.container.ArtemisContainer;
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

public class SingleInstanceSmokeTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(SingleInstanceSmokeTests.class);

    private ArtemisContainer artemisInstance;

    @BeforeAll
    void setupEnv() {
        String artemisName = "artemis";
        LOGGER.info("Creating artemis instance: " + artemisName);
        artemisInstance = ArtemisDeployment.createArtemis(artemisName);
    }

    @Test
    @Tag(Constants.TAG_SMOKE)
    void queueProduceConsumeMsgTest() {
        int numOfMessages = 100;
        String addressName = "TestQueue1";
        String queueName = "TestQueue1";

        LOGGER.info("Generating the client URL to connect to artemis instance");
        String artemisAmqpHostAndPort = artemisInstance.getHostAndPort(ArtemisConstants.DEFAULT_ALL_PROTOCOLS_PORT);
        String url = AmqpUtil.buildAmqpUrl(artemisAmqpHostAndPort);

        LOGGER.info("Creating client");
        JmsClient client = ResourceManager.getJmsClient("client-1", new JmsConnectionFactory(url))
                .withCredentials(ArtemisConstants.ADMIN_NAME, ArtemisConstants.ADMIN_PASS)
                .withDestination(Queue.class, queueName);

        LOGGER.info("Producing {} messages to queue {}", numOfMessages, queueName);
        client.produce(numOfMessages);
        Map<String, Message> producedMsgs = client.getProducedMsgs();

        LOGGER.info("Ensure queue contains {} messages", numOfMessages);
        artemisInstance.ensureQueueCount(addressName, queueName, RoutingType.ANYCAST, numOfMessages);

        LOGGER.info("Consuming {} messages from queue {}", numOfMessages, queueName);
        client.consume(numOfMessages);
        Map<String, Message> consumedMsgs = client.getConsumedMsgs();

        client.disconnect();

        LOGGER.info("Ensure queue is empty");
        artemisInstance.ensureQueueCount(addressName, queueName, RoutingType.ANYCAST, 0);

        LOGGER.info("Ensuring produced and consumed messages are the same");
        ensureSameMessages(numOfMessages, producedMsgs, consumedMsgs);
    }

}
