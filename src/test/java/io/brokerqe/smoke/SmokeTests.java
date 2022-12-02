/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.smoke;

import io.amq.broker.v2alpha3.ActiveMQArtemisAddress;
import io.brokerqe.AbstractSystemTests;
import io.brokerqe.ActiveMQArtemisClusterOperator;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.Pod;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.is;

public class SmokeTests extends AbstractSystemTests {

    @Test
    void simpleBrokerDeploymentTest() {
        Namespace ns = getClient().getKubernetesClient().namespaces().withName("default").get();
        assertThat(ns, is(notNullValue()));
        assertThat(getClient().namespaceExists("lala"), is(false));

        String myNamespace = "lala";
        LOGGER.info("[{}] Creating new namespace to {}", ns, myNamespace);
        Namespace lalaNs = getClient().createNamespace(myNamespace);
        assertThat(lalaNs, is(notNullValue()));
        LOGGER.info(getClient().getNamespace());

        ActiveMQArtemisClusterOperator operator = getClient().deployClusterOperator(myNamespace);

        GenericKubernetesResource broker = createArtemisTypeless(myNamespace, operator.getArtemisSingleExamplePath());
//        ActiveMQArtemis broker = createArtemisTyped(myNamespace, artemisExampleFilePath, true);
        LOGGER.info(String.valueOf(broker));
        String brokerName = broker.getMetadata().getName();
        LOGGER.info("[{}] Check if broker pod with name {} is present.", myNamespace, brokerName);
        List<Pod> brokerPods = getClient().listPodsByPrefixInName(myNamespace, brokerName);
        assertThat(brokerPods.size(), is(1));
        ActiveMQArtemisAddress myAddress = createArtemisAddress(myNamespace, operator.getArtemisAddressQueueExamplePath());
//
        deleteArtemisTypeless(myNamespace, brokerName);
//        deleteArtemisTyped(myNamespace, broker, true);

        deleteArtemisAddress(myNamespace, myAddress);
        getClient().undeployClusterOperator(operator);

        LOGGER.info("[{}] Deleting namespace to {}", myNamespace, myNamespace);
        getClient().deleteNamespace(myNamespace);
        assertThat(getClient().namespaceExists(myNamespace), is(false));
    }
}
