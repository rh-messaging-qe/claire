/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.brokerqe.claire.monitoring;

import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.KubeClient;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.openshift.api.model.monitoring.v1.EndpointBuilder;
import io.fabric8.openshift.api.model.monitoring.v1.ServiceMonitor;
import io.fabric8.openshift.api.model.monitoring.v1.ServiceMonitorBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

import java.util.HashMap;
import java.util.Map;

public class Prometheus {
    private static final Logger LOGGER = LoggerFactory.getLogger(Prometheus.class);

    private static final String PROMETHEUS_CONFIGMAP = "cluster-monitoring-config";
    private static final String SERVICE_MONITOR = "broker-service-monitor";

    protected final String namespace;
    protected final KubeClient kubeClient;

    public Prometheus(String namespace) {
        this.namespace = namespace;
        this.kubeClient = ResourceManager.getKubeClient();
    }

    public static void waitForPrometheusPodsInitialization() {
        TestUtils.waitFor("creation of Openshift user-workload statefulsets", Constants.DURATION_5_SECONDS, Constants.DURATION_2_MINUTES, () -> {
            StatefulSet prometheusSs =  ResourceManager.getKubeClient().getStatefulSet(Constants.MONITORING_NAMESPACE_USER, Constants.PROMETHEUS_USER_SS);
            StatefulSet thanosSs = ResourceManager.getKubeClient().getStatefulSet(Constants.MONITORING_NAMESPACE_USER, Constants.THANOS_USER_SS);

            return prometheusSs != null && prometheusSs.getStatus().getReadyReplicas().equals(prometheusSs.getSpec().getReplicas())
                    && thanosSs != null && thanosSs.getStatus().getReadyReplicas().equals(thanosSs.getSpec().getReplicas());
        });
    }

    public static void waitForPrometheusPodsDeletion() {
        TestUtils.waitFor("removal of Openshift user-workload statefulsets & pods", Constants.DURATION_5_SECONDS, Constants.DURATION_2_MINUTES, () -> {
            StatefulSet prometheusSs = ResourceManager.getKubeClient().getStatefulSet(Constants.MONITORING_NAMESPACE_USER, Constants.PROMETHEUS_USER_SS);
            StatefulSet thanosSs = ResourceManager.getKubeClient().getStatefulSet(Constants.MONITORING_NAMESPACE_USER, Constants.THANOS_USER_SS);
            return prometheusSs == null && thanosSs == null && ResourceManager.getKubeClient().listPods(Constants.MONITORING_NAMESPACE_USER).size() == 0;
        });
    }

    public void enablePrometheusUserMonitoring() {
        ConfigMap cm = new ConfigMapBuilder()
                .editOrNewMetadata()
                .withName(PROMETHEUS_CONFIGMAP)
                .endMetadata()
                .withData(Map.of("config.yaml", """
                              enableUserWorkload: true
                        """))
                .build();

        kubeClient.createConfigMap(Constants.MONITORING_NAMESPACE, cm);
        LOGGER.info("[{}] Created configmap for prometheus", Constants.MONITORING_NAMESPACE);

        LOGGER.info("[{}] Waiting for Prometheus & Thanos pods to show up", Constants.MONITORING_NAMESPACE_USER);
        waitForPrometheusPodsInitialization();
    }

    public void createServiceMonitor(String applicationName) {
        ArtemisVersion artemisVersion = ResourceManager.getEnvironment().getArtemisTestVersion();

        String webConsolePort = Constants.WEBCONSOLE_URI_PREFIX;
        if (artemisVersion.getVersionNumber() < ArtemisVersion.VERSION_2_28.getVersionNumber()) {
            webConsolePort = Constants.WEBCONSOLE_URI_PREFIX + "-0";
        }

        ServiceMonitor serviceMonitor = new ServiceMonitorBuilder()
            .editOrNewMetadata()
                .withName(SERVICE_MONITOR)
                .withLabels(Map.of("application", applicationName + "-app"))
            .endMetadata()
            .editOrNewSpec()
                .withEndpoints(new EndpointBuilder()
                    .withPort(webConsolePort)
                    .withScheme(Constants.HTTP).build())
                    .withSelector(
                        new LabelSelectorBuilder()
                            .withMatchLabels(Map.of("application", applicationName + "-app"))
                        .build()
                    )
            .endSpec()
            .build();
        kubeClient.getKubernetesClient().resource(serviceMonitor).inNamespace(namespace).createOrReplace();
    }

    public HashMap<String, String> getMetrics(int index, String brokerName) {
        try {
            HttpURLConnection con = (HttpURLConnection) TestUtils.makeHttpRequest(getMetricsUrl(index, brokerName), Constants.GET);
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            HashMap<String, String> result = new HashMap<>();
            while ((inputLine = in.readLine()) != null) {
                if (!inputLine.startsWith("#")) {
                    String[] data = inputLine.split(" ");
                    result.put(data[0], data[1]);
                }
            }
            in.close();
            return result;
        } catch (Exception e) {
            LOGGER.info("Failed to retrieve metrics from {}: {}", brokerName, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public String getMetricsUrl(String brokerName) {
        return getMetricsUrl(0, brokerName);
    }

    public String getMetricsUrl(int podIndex, String brokerName) {
        return Constants.HTTP + "://" + kubeClient.getExternalAccessServiceUrlPrefixName(namespace, brokerName + "-" + Constants.WEBCONSOLE_URI_PREFIX + "-").get(podIndex) + "/metrics/";
    }

    public void disablePrometheusUserMonitoring() {
        kubeClient.getKubernetesClient().configMaps().inNamespace(Constants.MONITORING_NAMESPACE).withName(PROMETHEUS_CONFIGMAP).delete();
        waitForPrometheusPodsDeletion();
    }
}
