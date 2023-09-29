/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.monitoring;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisBuilder;
import io.amq.broker.v1beta1.activemqartemisspec.Acceptors;
import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.clients.ClientType;
import io.brokerqe.claire.clients.MessagingClient;
import io.brokerqe.claire.junit.TestValidSince;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

public class PrometheusTests extends AbstractSystemTests {
    static final Logger LOGGER = LoggerFactory.getLogger(PrometheusTests.class);
    final static String PROMETHEUS_POD_NAME = "prometheus-user-workload-0";

    final static String MONITORING_BROKER_NAME = "monitoring-broker";
    final static String MSG_ACCEPTOR_NAME = "msg-acceptor";

    final static String ARTEMIS_METRIC_KEY = "artemis";

    final static String QUEUE_NAME = "tests";
    final static String ADDRESS_NAME = "prometheus";

    final static String PROMETHEUS_LOCAL = "http://127.0.0.1:9090/api/v1/query?query=";

    final String testNamespace = getRandomNamespaceName("monitoring-tests", 3);
    Prometheus prometheus;

    static String getAddressParam(String address) {
        return String.format("{address=\"%s\",broker=\"amq-broker\",}", address);
    }

    static String getAddressQueueParam(String address, String queue) {
        return String.format("{address=\"%s\",broker=\"amq-broker\",queue=\"%s\",}", address, queue);
    }

    List<String> keysGc = List.of("jvm_gc_live_data_size_bytes", "jvm_gc_max_data_size_bytes", "jvm_gc_memory_allocated_bytes_total", "jvm_gc_memory_promoted_bytes_total");

    List<String> keysThreads = new ArrayList<>(List.of("jvm_threads_daemon_threads", "jvm_threads_live_threads", "jvm_threads_peak_threads"));

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

    HashMap<String, String> getPluginMetrics(String brokerName) {
        TestUtils.waitFor("Broker Prometheus plugin to initialize", Constants.DURATION_5_SECONDS, Constants.DURATION_1_MINUTE, () -> {
            HashMap<String, String> metrics = prometheus.getMetrics(0, brokerName);
            return metrics != null;
        });
        return prometheus.getMetrics(0, brokerName);
    }

    private boolean checkPrometheusMetrics(Pod prometheusPod, String curlCmd) {
        boolean foundArtemis = false;
        String prometheusResponse = getClient().executeCommandInPod(prometheusPod, curlCmd, Constants.DURATION_1_MINUTE);
        try {
            JSONObject prometheusJson = new JSONObject(prometheusResponse);
            JSONArray actualResult = prometheusJson.getJSONObject("data").getJSONArray("result");
            for (int idx = 0; idx < actualResult.length(); idx++) {
                JSONObject item = actualResult.getJSONObject(idx).getJSONObject("metric");
                String name = item.getString("__name__");
                if (name.contains("artemis")) {
                    foundArtemis = true;
                    LOGGER.debug("[{}] found the artemis item in json: {}", testNamespace, item);
                    break;
                }
            }
        } catch (Exception e) {
            LOGGER.info("[{}] Failed to retrieve prometheus metrics: {}", testNamespace, e.getMessage());
            LOGGER.debug("[{}] Prometheus raw data: {}", testNamespace, prometheusResponse);
        }
        return foundArtemis;
    }

    @Test
    void defaultMetricsOnPluginEndpointTest() {
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
            .endSpec().build();

        ResourceManager.createArtemis(testNamespace, broker);
        HashMap<String, String> metrics = getPluginMetrics(broker.getMetadata().getName());
        assertThat("Metrics are published by Broker Prometheus Plugin", metrics, is(notNullValue()));
        LOGGER.info("Verifying that metrics are exposed by plugin and default values are published");
        String dlqSizeKey = ARTEMIS_METRIC_KEY + "_address_size" + getAddressParam("DLQ");
        String expirySizeKey = ARTEMIS_METRIC_KEY + "_address_size" + getAddressParam("ExpiryQueue");
        String expiryExpiredKey = ARTEMIS_METRIC_KEY + "_messages_expired" + getAddressQueueParam("ExpiryQueue", "ExpiryQueue");
        String dlqPagingKey = ARTEMIS_METRIC_KEY + "_number_of_pages" + getAddressParam("DLQ");
        String expiryPagingKey = ARTEMIS_METRIC_KEY + "_number_of_pages" + getAddressParam("ExpiryQueue");
        String dlqKilledKey = ARTEMIS_METRIC_KEY + "_messages_killed" + getAddressQueueParam("DLQ", "DLQ");
        String expiryKilledKey = ARTEMIS_METRIC_KEY + "_messages_killed" + getAddressQueueParam("ExpiryQueue", "ExpiryQueue");
        assertThat("Default DLQ is not empty", metrics.get(dlqSizeKey), equalTo("0.0"));
        assertThat("Default Expiry queue is not empty", metrics.get(expirySizeKey), equalTo("0.0"));
        assertThat("Default Expiry queue has expired messages", metrics.get(expiryExpiredKey), equalTo("0.0"));
        assertThat("Default DLQ is paged", metrics.get(dlqPagingKey), equalTo("0.0"));
        assertThat("Default Expiry queue is paged", metrics.get(expiryPagingKey), equalTo("0.0"));
        assertThat("Default DLQ has killed messages", metrics.get(dlqKilledKey), equalTo("0.0"));
        assertThat("Default Expiry has killed messages", metrics.get(expiryKilledKey), equalTo("0.0"));

        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    @Test
    void defaultMetricsOnPrometheusTest() {
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
                .endSpec()
                .build();
        ResourceManager.createArtemis(testNamespace, broker);

        HashMap<String, String> metrics = getPluginMetrics(broker.getMetadata().getName());
        assertThat(metrics, is(notNullValue())); // Metrics exposed & default values are published
        prometheus.createServiceMonitor(MONITORING_BROKER_NAME);

        Pod prometheusPod = getClient().getPod(Constants.MONITORING_NAMESPACE_USER, PROMETHEUS_POD_NAME);
        String queryParams = "{namespace=\"" + testNamespace + "\",broker=\"amq-broker\"}";
        LOGGER.debug("[{}] request url: {}", testNamespace, PROMETHEUS_LOCAL + queryParams);
        String curlCmd = "curl -g --silent '" + PROMETHEUS_LOCAL + queryParams + "'";
        LOGGER.info("[{}][{}] Executing curl call {}", Constants.MONITORING_NAMESPACE_USER, PROMETHEUS_POD_NAME, PROMETHEUS_LOCAL + queryParams);

        TestUtils.waitFor("prometheus to scrape broker statistics", Constants.DURATION_5_SECONDS, Constants.DURATION_2_MINUTES, () -> checkPrometheusMetrics(prometheusPod, curlCmd));

        assertThat("Metrics are being published into cluster-owned Prometheus instance", checkPrometheusMetrics(prometheusPod, curlCmd), is(true));
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    @Test
    @TestValidSince(ArtemisVersion.VERSION_2_28)
    void messagingStatisticsTest() {
        String regularKey = "artemis_message_count" + getAddressQueueParam(ADDRESS_NAME, QUEUE_NAME);
        String durableKey = "artemis_durable_message_count" + getAddressQueueParam(ADDRESS_NAME, QUEUE_NAME);
        Acceptors amqpAcceptors = createAcceptor(MSG_ACCEPTOR_NAME, "amqp", 5672, true, false, null, true);
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
                .endSpec().build();

        ResourceManager.createArtemis(testNamespace, broker);

        HashMap<String, String> metrics = getPluginMetrics(broker.getMetadata().getName());
        LOGGER.info("[{}] checking for default metrics to not have messaging statistics included", testNamespace);
        assertThat(String.format("Metrics published did have an unexpected key by default (%s)", durableKey), metrics.containsKey(durableKey), is(false));

        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, MONITORING_BROKER_NAME);
        Service amqp = getClient().getFirstServiceBrokerAcceptor(testNamespace, MONITORING_BROKER_NAME, MSG_ACCEPTOR_NAME);
        MessagingClient messagingClientAmqp = ResourceManager.createMessagingClient(ClientType.BUNDLED_AMQP, brokerPod,
                amqp.getSpec().getPorts().get(0).getPort().toString(), ADDRESS_NAME, QUEUE_NAME, 1);
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

    @Test
    @TestValidSince(ArtemisVersion.VERSION_2_28)
    void jvmMetricsTest() {
        LOGGER.info("[{}] Expected keys for GC metrics: {}", testNamespace, keysGc);
        LOGGER.info("[{}] Expected keys for JVM Thread metrics: {}", testNamespace, keysThreads);

        List<String> brokerProperties = new ArrayList<>();
        brokerProperties.add("metricsConfiguration.jvmThread=true");
        brokerProperties.add("metricsConfiguration.jvmGc=true");

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
                .withBrokerProperties(brokerProperties)
                .editOrNewConsole()
                    .withExpose(true)
                .endConsole()
            .endSpec().build();

        ResourceManager.createArtemis(testNamespace, broker);
        HashMap<String, String> metrics = getPluginMetrics(broker.getMetadata().getName());
        LOGGER.trace("[{}] Got metrics: {}", testNamespace, metrics);
        for (String item : keysGc) {
            assertThat(String.format("%s metric was not published in GC metrics", item), prometheus.metricsContainKey(metrics, item), is(true));
        }
        for (String item : keysThreads) {
            assertThat(String.format("%s metric was not published in JVM Thread metrics", item), prometheus.metricsContainKeyValue(metrics, item), is(true));
        }
        ResourceManager.deleteArtemis(testNamespace, broker);
    }

    // TODO: This test should be enabled in the future once
    @Test
    @Disabled("ENTMQBR-7811")
    void jvmMetricsBrokerConfigurationUpdateTest() {
        LOGGER.info("[{}] Expected keys for GC metrics: {}", testNamespace, keysGc);
        LOGGER.info("[{}] Expected keys for JVM Thread metrics: {}", testNamespace, keysThreads);

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
            .endSpec().build();

        broker = ResourceManager.createArtemis(testNamespace, broker);
        HashMap<String, String> metrics = getPluginMetrics(broker.getMetadata().getName());
        for (String item : keysGc) {
            assertThat(String.format("%s was published by GC metrics when its not expected to be", item),
                    prometheus.metricsContainKey(metrics, item), is(false));
        }
        for (String item : keysThreads) {
            assertThat(String.format("%s  was published by JVM Thread metrics when its not expected to be", item),
                    prometheus.metricsContainKeyValue(metrics, item), is(false));
        }

        List<String> brokerProperties = new ArrayList<>();
        brokerProperties.add("metricsConfiguration.jvmThread=true");
        broker.getSpec().setBrokerProperties(brokerProperties);
        LOGGER.info("[{}] Updating Broker deployment to enable JVM Threads metrics", testNamespace);
        LOGGER.debug("[{}] Broker Properties: {}", testNamespace, brokerProperties);
        // DateTime needs to be measured before call to createOrReplace due to timing issues otherwise
        ActiveMQArtemis initialArtemis = ResourceManager.createArtemis(testNamespace, broker);
        ResourceManager.waitForArtemisStatusUpdate(testNamespace, initialArtemis, ArtemisConstants.CONDITION_TYPE_BROKER_PROPERTIES_APPLIED, ArtemisConstants.CONDITION_REASON_APPLIED, Constants.DURATION_5_MINUTES);
        metrics = prometheus.getMetrics(0, broker.getMetadata().getName());
        LOGGER.trace("[{}] Got metrics: {}", testNamespace, metrics);
        for (String item : keysGc) {
            assertThat(String.format("%s was published by GC metrics when its not expected to be", item),
                    prometheus.metricsContainKey(metrics, item), is(false));
        }
        for (String item : keysThreads) {
            assertThat(String.format("%s was not published by JVM Thread metrics when its expected to be", item),
                    prometheus.metricsContainKeyValue(metrics, item), is(true));
        }

        brokerProperties.add("metricsConfiguration.jvmGc=true");
        broker.getSpec().setBrokerProperties(brokerProperties);
        LOGGER.info("[{}] Updating Broker deployment to enable GC metrics", testNamespace);
        LOGGER.debug("[{}] Broker Properties: {}", testNamespace, brokerProperties);
        initialArtemis = ResourceManager.createArtemis(testNamespace, broker);
        ResourceManager.waitForArtemisStatusUpdate(testNamespace, initialArtemis, ArtemisConstants.CONDITION_TYPE_BROKER_PROPERTIES_APPLIED, ArtemisConstants.CONDITION_REASON_APPLIED, Constants.DURATION_5_MINUTES);
        LOGGER.info("[{}] waiting for Broker pod {} to reload BrokerProperties configuration", testNamespace, broker.getMetadata().getName());
        TestUtils.threadSleep(Constants.DURATION_5_SECONDS);
        metrics = prometheus.getMetrics(0, broker.getMetadata().getName());
        LOGGER.trace("[{}] Got metrics: {}", testNamespace, metrics);
        for (String item : keysGc) {
            assertThat(String.format("%s was not published by GC metrics when its expected to be", item),
                    prometheus.metricsContainKey(metrics, item), is(true));
        }
        for (String item : keysThreads) {
            assertThat(String.format("%s was not published by JVM Thread metrics when its expected to be", item),
                    prometheus.metricsContainKeyValue(metrics, item), is(true));
        }
        ResourceManager.deleteArtemis(testNamespace, broker);
    }
}
