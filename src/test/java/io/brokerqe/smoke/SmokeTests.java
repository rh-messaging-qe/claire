/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.smoke;

import io.amq.broker.v2alpha3.ActiveMQArtemisAddress;
import io.brokerqe.AbstractSystemTests;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.Namespace;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.is;

public class SmokeTests extends AbstractSystemTests {

    static final Logger LOGGER = LoggerFactory.getLogger(SmokeTests.class);

    @Test
    void simpleBrokerDeploymentTest() {
        String myNamespace = "lala";
        Namespace ns = getClient().getKubernetesClient().namespaces().withName("default").get();
        assertThat(ns, is(notNullValue()));

        Namespace lalaNs = getClient().createNamespace(myNamespace);
        assertThat(lalaNs, is(notNullValue()));
        LOGGER.info(getClient().getNamespace());
        deployClusterOperator(myNamespace);

        String artemisExampleFilePath = "artemis/examples/artemis/artemis-basic-deployment.yaml";
        String artemisAddressQueueExampleFilePath = "artemis/examples/address/address-queue-create.yaml";
        GenericKubernetesResource broker = createArtemisTypeless(myNamespace, artemisExampleFilePath);
//        ActiveMQArtemis broker = createArtemisTyped(myNamespace, artemisExampleFilePath, true);
        LOGGER.info(String.valueOf(broker));
        ActiveMQArtemisAddress myAddress = createArtemisAddress(myNamespace, artemisAddressQueueExampleFilePath);

        deleteArtemisTypeless(myNamespace, broker.getMetadata().getName());
//        deleteArtemisTyped(myNamespace, broker, true);

        deleteArtemisAddress(myNamespace, myAddress);
        undeployClusterOperator(myNamespace);
    }
}
