/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.plugins;

import io.brokerqe.claire.Constants;
import io.brokerqe.claire.CustomTool;
import io.brokerqe.claire.Environment;
import io.brokerqe.claire.KubeClient;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * ArtemisCloud Self-provisioning Plugin
 * https://github.com/artemiscloud/activemq-artemis-self-provisioning-plugin
 * https://github.com/artemiscloud/activemq-artemis-jolokia-api-server
 *
 */
public class AMQSelfProvisioningPlugin extends ACSelfProvisioningPlugin implements CustomTool {

    private static final Logger LOGGER = LoggerFactory.getLogger(AMQSelfProvisioningPlugin.class);
    private static KubeClient kubeClient = ResourceManager.getKubeClient();
    private static List<HasMetadata> deployedResources = new ArrayList<>();

    private static String jolokiaFolder;
    private static String sppFolder;

    @Override
    public AMQSelfProvisioningPlugin deploy() {
        String tmpDirLocation = Environment.get().getTmpDirLocation();
        String jolokiaInstallZipUrl = ResourceManager.getEnvironment().getJolokiaApiUrl();
        String sppInstallZipUrl = ResourceManager.getEnvironment().getSppUrl();
        String jolokiaZip = tmpDirLocation + Constants.FILE_SEPARATOR + "amq-broker-jolokia-api-server.zip";
        String sppZip = tmpDirLocation + Constants.FILE_SEPARATOR + "amq-broker-self-provisioning-plugin.zip";

        jolokiaFolder = new File(jolokiaZip).getParent() + Constants.FILE_SEPARATOR + new File(jolokiaInstallZipUrl).getName().replace("-rhel9.zip", "");
        sppFolder = new File(sppZip).getParent() + Constants.FILE_SEPARATOR + new File(sppInstallZipUrl).getName().replace("-rhel9.zip", "");

        LOGGER.info("[SPP] Downloading & unpacking \n{}\n{}", jolokiaInstallZipUrl, sppInstallZipUrl);
        TestUtils.getFileFromUrl(jolokiaInstallZipUrl, jolokiaZip);
        TestUtils.getFileFromUrl(sppInstallZipUrl, sppZip);
        TestUtils.unzip(jolokiaZip, tmpDirLocation);
        TestUtils.unzip(sppZip, tmpDirLocation);

        LOGGER.info("[{}] Deploying AMQ Artemis Jolokia API Server", JOLOKIA_API_DEFAULT_NAMESPACE);
        TestUtils.executeLocalCommand(120, new File(jolokiaFolder), "/bin/bash", "-lc", "sh deploy.sh -c cert-manager");
        TestUtils.threadSleep(Constants.DURATION_10_SECONDS);
        Pod jolokiaApiServerPod = kubeClient.getFirstPodByPrefixName(JOLOKIA_API_DEFAULT_NAMESPACE, JOLOKIA_API_DEFAULT_NAMESPACE);
        // BUG pod is null?!
        kubeClient.waitUntilPodIsReady(JOLOKIA_API_DEFAULT_NAMESPACE, jolokiaApiServerPod);

        LOGGER.info("[{}] Deploying AMQ Artemis Self-provisioning Plugin", SPP_DEFAULT_NAMESPACE);
        TestUtils.executeLocalCommand(120, new File(sppFolder), "/bin/bash", "-lc", "sh deploy-plugin.sh");
        TestUtils.threadSleep(Constants.DURATION_10_SECONDS);
        Pod sppPod = kubeClient.getFirstPodByPrefixName(SPP_DEFAULT_NAMESPACE, SPP_DEFAULT_NAMESPACE);
        kubeClient.waitUntilPodIsReady(JOLOKIA_API_DEFAULT_NAMESPACE, sppPod);

        TestUtils.threadSleep(Constants.DURATION_10_SECONDS);
        return this;
    }

    public void undeploy() {
        LOGGER.info("[{}] Undeploying AMQ Artemis Self-provisioning Plugin", SPP_DEFAULT_NAMESPACE);
        TestUtils.executeLocalCommand(60, new File(sppFolder), "/bin/bash", "-lc", "sh undeploy-plugin.sh");
        LOGGER.info("[{}] Undeploying AMQ Artemis Jolokia API Server", JOLOKIA_API_DEFAULT_NAMESPACE);
        TestUtils.executeLocalCommand(120, new File(jolokiaFolder), "/bin/bash", "-lc", "sh undeploy.sh -c cert-manager");

        kubeClient.deleteNamespace(JOLOKIA_API_DEFAULT_NAMESPACE);
        kubeClient.deleteNamespace(SPP_DEFAULT_NAMESPACE);
    }

}
