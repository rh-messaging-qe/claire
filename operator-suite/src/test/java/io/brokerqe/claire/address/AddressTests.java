/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.address;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisAddress;
import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.operator.ArtemisFileProvider;
import io.fabric8.kubernetes.api.model.Pod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

public class AddressTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(AddressTests.class);
    private final String testNamespace = getRandomNamespaceName("address-tests", 3);

    @BeforeAll
    void setupClusterOperator() {
        setupDefaultClusterOperator(testNamespace);
    }

    @AfterAll
    void teardownClusterOperator() {
        teardownDefaultClusterOperator(testNamespace);
    }

    @Test
    @Tag(Constants.TAG_OPERATOR)
    void persistAddressAfterCoBrokerRestart() {
        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, ArtemisFileProvider.getArtemisSingleExampleFile(), true);
        ActiveMQArtemisAddress myAddress = ResourceManager.createArtemisAddress(testNamespace, ArtemisFileProvider.getAddressQueueExampleFile());

        String brokerName = broker.getMetadata().getName();
        String operatorName = operator.getOperatorName();
        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, brokerName);
        Pod operatorPod = getClient().getFirstPodByPrefixName(testNamespace, operatorName);
        String allDefaultPort = getServicePortNumber(testNamespace, getArtemisServiceHdls(testNamespace, broker), "all");

        LOGGER.info("[{}] Getting info from {} with uid {}", testNamespace, brokerPod.getMetadata().getName(), brokerPod.getMetadata().getUid());
        String command = "amq-broker/bin/artemis address show --url tcp://" + brokerPod.getStatus().getPodIP() + ":" + allDefaultPort;
        String cmdOutput = getClient().executeCommandInPod(brokerPod, command, Constants.DURATION_1_MINUTE);
        LOGGER.info(cmdOutput);
        assertThat(cmdOutput, containsString(myAddress.getSpec().getAddressName()));

        getClient().reloadPodWithWait(testNamespace, operatorPod, operatorName);
        getClient().reloadPodWithWait(testNamespace, brokerPod, brokerName);

        brokerPod = getClient().getFirstPodByPrefixName(testNamespace, brokerName);
        LOGGER.info("[{}] Getting info from {} with uid {}", testNamespace, brokerPod.getMetadata().getName(), brokerPod.getMetadata().getUid());

        Pod finalBrokerPod = brokerPod;
        String finalCommand = "amq-broker/bin/artemis address show --url tcp://" + brokerPod.getStatus().getPodIP() + ":" + allDefaultPort;
        TestUtils.waitFor("Address to show up in artemis address call", Constants.DURATION_10_SECONDS, Constants.DURATION_5_MINUTES, () -> {
            String commandOutput = getClient().executeCommandInPod(finalBrokerPod, finalCommand, Constants.DURATION_1_MINUTE);
            LOGGER.info(commandOutput);
            return commandOutput.contains(myAddress.getSpec().getAddressName());
        });

        ResourceManager.deleteArtemisAddress(testNamespace, myAddress);
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

}
