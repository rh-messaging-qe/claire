/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.plugins;

import io.brokerqe.claire.Constants;
import io.brokerqe.claire.CustomTool;
import io.brokerqe.claire.KubeClient;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.helpers.DataStorer;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * ArtemisCloud Self-provisioning Plugin
 * https://github.com/artemiscloud/activemq-artemis-self-provisioning-plugin
 * https://github.com/artemiscloud/activemq-artemis-jolokia-api-server
 *
 */
public class ACSelfProvisioningPlugin implements CustomTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(ACSelfProvisioningPlugin.class);
    protected static final String JOLOKIA_API_DEFAULT_NAMESPACE = "activemq-artemis-jolokia-api-server";
    protected static final String SPP_DEFAULT_NAMESPACE = "activemq-artemis-self-provisioning-plugin";
    protected static KubeClient kubeClient = ResourceManager.getKubeClient();
    protected static List<HasMetadata> deployedResources = new ArrayList<>();
    private static final List<URL> JOLOKIA_API_DEPLOYMENT_URLS;
    private static final List<URL> SPP_DEPLOYMENT_URLS;
    static {
        try {
            SPP_DEPLOYMENT_URLS = List.of(
                    new URL("https://raw.githubusercontent.com/artemiscloud/activemq-artemis-self-provisioning-plugin/refs/heads/main/deploy/base/deployment.yaml"),
                    new URL("https://raw.githubusercontent.com/artemiscloud/activemq-artemis-self-provisioning-plugin/refs/heads/main/deploy/base/nginx-configmap.yaml"),
                    new URL("https://raw.githubusercontent.com/artemiscloud/activemq-artemis-self-provisioning-plugin/refs/heads/main/deploy/base/plugin.yaml"),
                    new URL("https://raw.githubusercontent.com/artemiscloud/activemq-artemis-self-provisioning-plugin/refs/heads/main/deploy/base/service.yaml")
            );
            JOLOKIA_API_DEPLOYMENT_URLS = List.of(
                    new URL("https://raw.githubusercontent.com/artemiscloud/activemq-artemis-jolokia-api-server/refs/heads/main/deploy/deployment.yaml"),
                    new URL("https://raw.githubusercontent.com/artemiscloud/activemq-artemis-jolokia-api-server/refs/heads/main/deploy/service.yaml")
            );
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ACSelfProvisioningPlugin deploy() {
        kubeClient.createNamespace(JOLOKIA_API_DEFAULT_NAMESPACE);
        kubeClient.createNamespace(SPP_DEFAULT_NAMESPACE);

        try {
            LOGGER.info("[{}] Deploying ActiveMQ Artemis Jolokia API Server", JOLOKIA_API_DEFAULT_NAMESPACE);
            for (URL url : JOLOKIA_API_DEPLOYMENT_URLS) {
                List<HasMetadata> resources = kubeClient.getKubernetesClient().load(url.openStream()).items();
                resources = kubeClient.getKubernetesClient().resourceList(resources).inNamespace(JOLOKIA_API_DEFAULT_NAMESPACE).createOrReplace();
                deployedResources.addAll(resources);
            }
            Deployment jolokiaDeployment = ResourceManager.getKubeClient().getDeployment(JOLOKIA_API_DEFAULT_NAMESPACE, JOLOKIA_API_DEFAULT_NAMESPACE);
            kubeClient.getKubernetesClient().resource(jolokiaDeployment).waitUntilReady(1, TimeUnit.MINUTES);
            LOGGER.info("[{}] Successfully deployed Jolokia API Server", JOLOKIA_API_DEFAULT_NAMESPACE);

            LOGGER.info("[{}] Deploying ActiveMQ Artemis Self-provisioning Plugin", SPP_DEFAULT_NAMESPACE);
            for (URL url : SPP_DEPLOYMENT_URLS) {
                List<HasMetadata> resources = kubeClient.getKubernetesClient().load(url.openStream()).items();
                resources = kubeClient.getKubernetesClient().resourceList(resources).inNamespace(SPP_DEFAULT_NAMESPACE).createOrReplace();
                deployedResources.addAll(resources);
            }

            Deployment sppDeployment = ResourceManager.getKubeClient().getDeployment(SPP_DEFAULT_NAMESPACE, SPP_DEFAULT_NAMESPACE);
            kubeClient.getKubernetesClient().resource(sppDeployment).waitUntilReady(2, TimeUnit.MINUTES);
            applyPatch();

            // check logs, possibly restart pod if issue with cert
            TestUtils.threadSleep(Constants.DURATION_10_SECONDS);
            List<Event> events = kubeClient.getKubernetesClient().v1().events().inNamespace(SPP_DEFAULT_NAMESPACE).list().getItems();
            Pod sppPod;
            for (Event event : events) {
                if (event.getReason().equals("FailedMount")) {
                    LOGGER.warn("[{}] Detected FailedMount, restarting spp-pod", SPP_DEFAULT_NAMESPACE);
                    sppPod = kubeClient.getFirstPodByPrefixName(SPP_DEFAULT_NAMESPACE, "activemq-artemis-self-provisioning-plugin");
                    kubeClient.restartPod(SPP_DEFAULT_NAMESPACE, sppPod, SPP_DEFAULT_NAMESPACE);
                    break;
                }
            }

            sppPod = kubeClient.getFirstPodByPrefixName(SPP_DEFAULT_NAMESPACE, "activemq-artemis-self-provisioning-plugin");
            String podLogs = kubeClient.getLogsFromPod(sppPod);
            if (podLogs.contains("SSL routines:ssl3_read_bytes:sslv3 alert bad certificate:SSL alert number ")) {
                LOGGER.info("[{}] Problem with deployed SPP pod - cert-mount-issues. Restarting pod.", SPP_DEFAULT_NAMESPACE);
                kubeClient.restartPod(SPP_DEFAULT_NAMESPACE, sppPod, SPP_DEFAULT_NAMESPACE);
            }

            LOGGER.info("[{}] Successfully deployed Self-provisioning plugin.", SPP_DEFAULT_NAMESPACE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        DataStorer.dumpResourceToFile(deployedResources);
        return this;
    }

    @Override
    public void undeploy() {
        LOGGER.info("[{}] Undeploying ActiveMQ Artemis Self-provisioning Plugin & ActiveMQ Artemis Jolokia API Server", JOLOKIA_API_DEFAULT_NAMESPACE);
        kubeClient.getKubernetesClient().resourceList(deployedResources).delete();
        kubeClient.deleteNamespace(JOLOKIA_API_DEFAULT_NAMESPACE);
        kubeClient.deleteNamespace(SPP_DEFAULT_NAMESPACE);
        removePatch();
    }

    private static void applyPatch() {
        LOGGER.info("[PATCH] Applying 'activemq-artemis-self-provisioning-plugin' to consoles.operator.openshift.io cluster");
        String[] patchOc = {"/bin/bash", "-c",
                            "oc patch consoles.operator.openshift.io cluster --type=json --patch '[{ \"op\": \"add\", \"path\": \"/spec/plugins/-\", \"value\": \"activemq-artemis-self-provisioning-plugin\" }]'"};
        TestUtils.executeLocalCommand(patchOc);
        printCurrentPlugins();
    }

    private static void printCurrentPlugins() {
        String[] actualPluginsCmd = {"/bin/bash", "-c", "oc get consoles.operator.openshift.io cluster -o json | jq '.spec.plugins'"};
        String actualPlugins = TestUtils.executeLocalCommand(actualPluginsCmd).stdout;
        LOGGER.debug("[PATCH] Actually present plugins: {}", actualPlugins);
    }

    private static void removePatch() {
        LOGGER.info("[PATCH] Removing 'activemq-artemis-self-provisioning-plugin' from consoles.operator.openshift.io cluster");
//        plugins=$(oc get consoles.operator.openshift.io cluster -o json | jq '.spec.plugins | map(select(. != "activemq-artemis-self-provisioning-plugin"))')
//        oc patch consoles.operator.openshift.io cluster --type=merge --patch '{ "spec": { "plugins": $plugins } }'
        String[] getPluginsCmd = {"/bin/bash", "-c",
                                  "oc get consoles.operator.openshift.io cluster -o json | jq '.spec.plugins | map(select(. != \"activemq-artemis-self-provisioning-plugin\"))'"};
        JSONArray jsonPlugins = new JSONArray(TestUtils.executeLocalCommand(getPluginsCmd).stdout.replaceAll("\"", "'"));
        String pluginsStr = jsonPlugins.toString().replaceAll("\"", "\\\"");

        String[] patchCmd = {"/bin/bash", "-c", String.format("oc patch consoles.operator.openshift.io cluster --type=merge --patch '{ \"spec\": { \"plugins\": %s } }'", pluginsStr)};
        TestUtils.executeLocalCommand(patchCmd);
        printCurrentPlugins();
    }
}
