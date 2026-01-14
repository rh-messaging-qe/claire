/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.monitoring;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisBuilder;
import io.amq.broker.v1beta1.activemqartemisspec.Acceptors;
import io.amq.broker.v1beta1.activemqartemisspec.addresssettings.AddressSetting;
import io.amq.broker.v1beta1.activemqartemisspec.addresssettings.AddressSettingBuilder;
import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.KubernetesPlatform;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.clients.ClientType;
import io.brokerqe.claire.clients.MessagingClient;
import io.brokerqe.claire.junit.DisabledTestPlatform;
import io.brokerqe.claire.junit.TestValidSince;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@DisabledTestPlatform(platforms = { KubernetesPlatform.MICROSHIFT, KubernetesPlatform.AWS_EKS })
public class AddressMetricsTests extends PrometheusTests {

    static final Logger LOGGER = LoggerFactory.getLogger(AddressMetricsTests.class);

    @BeforeAll
    void setup() {
        prometheus = new Prometheus(testNamespace);
        setupDefaultClusterOperator(testNamespace);
        prometheus.enablePrometheusUserMonitoring();
        String[] threadStates = {"runnable", "blocked", "terminated", "waiting", "timed-waiting", "new"};
        for (String item : threadStates) {
            String key = "jvm_threads_states_threads\\{.*state=\"" + item + "\",.*\\}";
            keysThreads.add(key);
        }
    }

    @AfterAll
    void teardownClusterOperator() {
        teardownDefaultClusterOperator(testNamespace);
        prometheus.disablePrometheusUserMonitoring();
    }

    @Test
    @TestValidSince(ArtemisVersion.VERSION_2_28)
    void autoDeletedAddressTest() {
        String regularKey = "artemis_message_count" + getAddressQueueParam(ADDRESS_NAME, QUEUE_NAME);
        String durableKey = "artemis_durable_message_count" + getAddressQueueParam(ADDRESS_NAME, QUEUE_NAME);
        Acceptors amqpAcceptors = createAcceptor(MSG_ACCEPTOR_NAME, "amqp", 5672, true, false, null, true);
        AddressSetting addressSetting = new AddressSettingBuilder()
            .withAutoCreateAddresses(true)
            .withAutoDeleteAddresses(true)
            .withMatch("#")
            .withAutoDeleteQueuesDelay((int) Constants.DURATION_5_SECONDS)
            .withAutoDeleteQueues(true)
            .build();

        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
            .editOrNewMetadata()
                .withName(MONITORING_BROKER_NAME)
                .withNamespace(testNamespace)
            .endMetadata()
            .editOrNewSpec()
                .editOrNewDeploymentPlan()
                    .withSize(1)
                    .withEnableMetricsPlugin(true)
                .endDeploymentPlan()
                .editOrNewConsole()
                    .withExpose(true)
                .endConsole()
                .withAcceptors(amqpAcceptors)
                .editOrNewAddressSettings()
                    .addAllToAddressSetting(List.of(addressSetting))
                .endAddressSettings()
                .endSpec().build();

        ResourceManager.createArtemis(testNamespace, broker);

        HashMap<String, String> metrics = getPluginMetrics(broker.getMetadata().getName());
        LOGGER.info("[{}] checking for default metrics to not have messaging statistics included", testNamespace);
        assertThat(String.format("Metrics published did have an unexpected key by default (%s)", durableKey), metrics.containsKey(durableKey), is(false));

        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, MONITORING_BROKER_NAME);
        Service amqp = getClient().getFirstServiceBrokerAcceptor(testNamespace, MONITORING_BROKER_NAME, MSG_ACCEPTOR_NAME);
        MessagingClient messagingClientAmqp = ResourceManager.createMessagingClient(ClientType.BUNDLED_AMQP, brokerPod, amqp.getSpec().getPorts().get(0).getPort().toString(), ADDRESS_NAME, QUEUE_NAME, 1);
        messagingClientAmqp.sendMessages();
        metrics = prometheus.getMetrics(0, broker.getMetadata().getName());
        LOGGER.info("[{}] Checking for metrics correctness after sending one message", testNamespace);
        LOGGER.trace("[{}] Got metrics: {}", testNamespace, metrics);
        assertThat(String.format("Metrics after sending a message didn't have expected key (%s)", durableKey), metrics.containsKey(durableKey), is(true));
        assertThat(String.format("Metrics after sending a message didn't have expected key (%s)", regularKey), metrics.containsKey(regularKey), is(true));

        String messageRegularCount = metrics.get(regularKey);
        String messageDurableCount = metrics.get(durableKey);
        assertThat("Durable messages count was not 1", messageDurableCount, equalTo("1.0"));
        assertThat("Regular messages count was not 1", messageRegularCount, equalTo("1.0"));

        messagingClientAmqp.receiveMessages();
        metrics = prometheus.getMetrics(0, broker.getMetadata().getName());
        LOGGER.info("[{}] Checking for metrics correctness after consuming previously sent message", testNamespace);
        messageDurableCount = metrics.get(durableKey);
        messageRegularCount = metrics.get(regularKey);
        assertThat("Durable message metrics were not updated after message consumption", messageDurableCount, equalTo("0.0")); //consumed
        assertThat("Regular message metrics were not updated after message consumption", messageRegularCount, equalTo("0.0"));
        ResourceManager.deleteArtemis(testNamespace, broker);
    }
}
