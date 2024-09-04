/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.address;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisAddress;
import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.helpers.AddressData;
import io.brokerqe.claire.helpers.JMXHelper;
import io.brokerqe.claire.operator.ArtemisFileProvider;
import io.fabric8.kubernetes.api.model.Pod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;

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

    private void verifyAddresses(List<AddressData> allAddresses, ActiveMQArtemisAddress myAddress) {
        assertThat("Address list didn't contain expected address name", allAddresses, hasItem(
                hasProperty("address", is(myAddress.getSpec().getAddressName()))
        ));
        assertThat("Address list didn't contain expected queue name", allAddresses, hasItem(
                hasProperty("queueName", is(myAddress.getSpec().getQueueName()))
        ));
    }
    @Test
    @Tag(Constants.TAG_OPERATOR)
    void persistAddressAfterCOBrokerRestart() {
        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, "addresses", 1, false, false, true, false);
        ActiveMQArtemisAddress myAddress = ResourceManager.createArtemisAddress(testNamespace, ArtemisFileProvider.getAddressQueueExampleFile());
        String brokerName = broker.getMetadata().getName();
        String operatorName = operator.getOperatorName();
        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, brokerName);
        Pod operatorPod = getClient().getFirstPodByPrefixName(testNamespace, operatorName);
        String allDefaultPort = getServicePortNumber(testNamespace, getArtemisServiceHdls(testNamespace, broker), "all");
        JMXHelper jmx = new JMXHelper().withKubeClient(getClient());
        LOGGER.info("[{}] Getting info from {} with uid {}", testNamespace, brokerPod.getMetadata().getName(), brokerPod.getMetadata().getUid());
        List<AddressData> allAddresses = jmx.getAllAddressesQueues(brokerName, ArtemisConstants.ROUTING_TYPE_ANYCAST, 0);
        verifyAddresses(allAddresses, myAddress);
        getClient().reloadPodWithWait(testNamespace, operatorPod, operatorName);
        getClient().reloadPodWithWait(testNamespace, brokerPod, brokerName);

        brokerPod = getClient().getFirstPodByPrefixName(testNamespace, brokerName);
        LOGGER.info("[{}] Getting info from {} with uid {}", testNamespace, brokerPod.getMetadata().getName(), brokerPod.getMetadata().getUid());

        Pod finalBrokerPod = brokerPod;
        String finalCommand = "amq-broker/bin/artemis address show --url tcp://" + brokerPod.getStatus().getPodIP() + ":" + allDefaultPort;
        // Need to wait for this since address is not populated on pod boot but rather on update from operator,
        // thus executing command immediately won't return needed address
        TestUtils.waitFor("Addresses to show up in artemis address call", Constants.DURATION_10_SECONDS, Constants.DURATION_5_MINUTES, () -> {
            String commandOutput = getClient().executeCommandInPod(finalBrokerPod, finalCommand, Constants.DURATION_1_MINUTE);
            LOGGER.info(commandOutput);
            return commandOutput.contains(myAddress.getSpec().getAddressName());
        });

        TestUtils.waitFor("[JMX] Addresses to show up in artemis address call", Constants.DURATION_5_SECONDS, Constants.DURATION_30_SECONDS, () -> {
            List<AddressData> updatedAddressesTmp = new ArrayList<>();
            try {
                updatedAddressesTmp = jmx.getAllAddressesQueues(brokerName, ArtemisConstants.ROUTING_TYPE_ANYCAST, 0);
            } catch (RuntimeException e) {
                LOGGER.warn("JMX is not fully up on broker, let's try again");
            }
            return !updatedAddressesTmp.isEmpty();
        });

        List<AddressData> updatedAddresses = jmx.getAllAddressesQueues(brokerName, ArtemisConstants.ROUTING_TYPE_ANYCAST, 0);
        verifyAddresses(updatedAddresses, myAddress);

        ResourceManager.deleteArtemisAddress(testNamespace, myAddress);
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

}
