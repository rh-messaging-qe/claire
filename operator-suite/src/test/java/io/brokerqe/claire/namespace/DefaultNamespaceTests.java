/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.namespace;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisAddress;
import io.amq.broker.v1beta1.ActiveMQArtemisBuilder;
import io.amq.broker.v1beta1.activemqartemisspec.Acceptors;
import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.clients.ClientType;
import io.brokerqe.claire.clients.MessagingClient;
import io.brokerqe.claire.junit.TestValidSince;
import io.brokerqe.claire.operator.ArtemisFileProvider;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

public class DefaultNamespaceTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultNamespaceTests.class);
    private final String testNamespace = "default";

    @BeforeAll
    void setupClusterOperator() {
        operator = ResourceManager.deployArtemisClusterOperator(testNamespace);
    }

    @AfterAll
    void teardownClusterOperator() {
        if (ResourceManager.isClusterOperatorManaged()) {
            if (operator == null) {
                LOGGER.warn("[{}] Skipping teardown of cluster Operator as it is null! (Already removed?)", testNamespace);
            } else {
                ResourceManager.undeployArtemisClusterOperator(operator);
            }
        }
    }

    @Test
    @Tag(Constants.TAG_OPERATOR)
    @TestValidSince(ArtemisVersion.VERSION_2_33)
    void testNonRootDeployment() {
        Acceptors amqpAcceptors = createAcceptor("amqp-owire-acceptor", "amqp,openwire", 5672);
        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
            .editOrNewMetadata()
                .withName("my-artemis")
                .withNamespace(testNamespace)
            .endMetadata()
            .editOrNewSpec()
                .editOrNewDeploymentPlan()
                    .withSize(1)
                    .withImage("placeholder")
                    .editOrNewContainerSecurityContext()
                        .withRunAsNonRoot(true)
                    .endContainerSecurityContext()
                    .editOrNewPodSecurityContext()
                        .withRunAsNonRoot(true)
                    .endPodSecurityContext()
                .endDeploymentPlan()
                .withAcceptors(List.of(amqpAcceptors))
            .endSpec()
            .build();

        broker = ResourceManager.createArtemis(testNamespace, broker, true, Constants.DURATION_2_MINUTES);
        ActiveMQArtemisAddress myAddress = ResourceManager.createArtemisAddress(testNamespace, ArtemisFileProvider.getAddressQueueExampleFile());
        // sending & receiving messages
        String brokerName = broker.getMetadata().getName();
        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, brokerName);

        // Get service/amqp acceptor name - svcName = "brokerName-XXXX-svc"
        Service amqp = getClient().getFirstServiceBrokerAcceptor(testNamespace, brokerName, "amqp-owire-acceptor");
        Integer amqpPort = amqp.getSpec().getPorts().get(0).getPort();
        assertThat(amqpPort, equalTo(5672));

        // Messaging tests
        int msgsExpected = 10;

        MessagingClient messagingClientAmqp = ResourceManager.createMessagingClient(ClientType.BUNDLED_AMQP, brokerPod,
                amqpPort.toString(), myAddress, msgsExpected);
        int sent = messagingClientAmqp.sendMessages();
        int received = messagingClientAmqp.receiveMessages();

        LOGGER.info("[{}] Sent {} - Received {}", testNamespace, sent, received);
        assertThat(sent, equalTo(msgsExpected));
        assertThat(sent, equalTo(received));
        assertThat(messagingClientAmqp.compareMessages(), is(true));

        ResourceManager.deleteArtemisAddress(testNamespace, myAddress);
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

}
