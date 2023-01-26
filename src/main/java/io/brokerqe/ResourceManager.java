/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe;

import io.amq.broker.v1beta1.ActiveMQArtemisSecurity;
import io.amq.broker.v1beta1.ActiveMQArtemisScaledown;
import io.amq.broker.v1beta1.ActiveMQArtemisAddress;
import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.brokerqe.clients.MessagingAmqpClient;
import io.brokerqe.operator.ArtemisCloudClusterOperator;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.apache.commons.lang.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResourceManager {

    final static Logger LOGGER = LoggerFactory.getLogger(ResourceManager.class);

    private static MixedOperation<ActiveMQArtemis, KubernetesResourceList<ActiveMQArtemis>, Resource<ActiveMQArtemis>> artemisClient;
    private static MixedOperation<ActiveMQArtemisAddress, KubernetesResourceList<ActiveMQArtemisAddress>, Resource<ActiveMQArtemisAddress>> artemisAddressClient;
    private static MixedOperation<ActiveMQArtemisSecurity, KubernetesResourceList<ActiveMQArtemisSecurity>, Resource<ActiveMQArtemisSecurity>> artemisSecurityClient;
    private static MixedOperation<ActiveMQArtemisScaledown, KubernetesResourceList<ActiveMQArtemisScaledown>, Resource<ActiveMQArtemisScaledown>> artemisScaledownClient;

    private static List<ArtemisCloudClusterOperator> deployedOperators = new ArrayList<>();
    private static Map<Deployment, String> deployedContainers = new HashMap<>();
    private static List<String> deployedNamespaces = new ArrayList<>();
    private static List<ActiveMQArtemis> deployedBrokers = new ArrayList<>();
    private static List<ActiveMQArtemisAddress> deployedAddresses = new ArrayList<>();
    private static List<ActiveMQArtemisSecurity> deployedSecurity = new ArrayList<>();
    private static Boolean projectCODeploy;
    private static ResourceManager resourceManager = null;
    private static KubeClient kubeClient;

    private ResourceManager(Environment environment) {
        kubeClient = new KubeClient("default");
        artemisClient = kubeClient.getKubernetesClient().resources(ActiveMQArtemis.class);
        artemisAddressClient = kubeClient.getKubernetesClient().resources(ActiveMQArtemisAddress.class);
        artemisSecurityClient = kubeClient.getKubernetesClient().resources(ActiveMQArtemisSecurity.class);
        artemisScaledownClient = kubeClient.getKubernetesClient().resources(ActiveMQArtemisScaledown.class);
        projectCODeploy = environment.isProjectManagedClusterOperator();
    }
    public static ResourceManager getInstance(Environment environment) {
        if (resourceManager == null) {
            resourceManager = new ResourceManager(environment);
        }
        return resourceManager;
    }

    public static KubeClient getKubeClient() {
        return kubeClient;
    }

    public static MixedOperation<ActiveMQArtemis, KubernetesResourceList<ActiveMQArtemis>, Resource<ActiveMQArtemis>> getArtemisClient() {
        return artemisClient;
    }

    public static MixedOperation<ActiveMQArtemisAddress, KubernetesResourceList<ActiveMQArtemisAddress>, Resource<ActiveMQArtemisAddress>> getArtemisAddressClient() {
        return artemisAddressClient;
    }

    public static MixedOperation<ActiveMQArtemisSecurity, KubernetesResourceList<ActiveMQArtemisSecurity>, Resource<ActiveMQArtemisSecurity>> getArtemisSecurityClient() {
        return artemisSecurityClient;
    }

    public static MixedOperation<ActiveMQArtemisScaledown, KubernetesResourceList<ActiveMQArtemisScaledown>, Resource<ActiveMQArtemisScaledown>> getArtemisScaledownClient() {
        return artemisScaledownClient;
    }

    // Artemis ClusterOperator
    public static ArtemisCloudClusterOperator deployArtemisClusterOperator(String namespace) {
        return deployArtemisClusterOperator(namespace, true, null);
    }

    public static ArtemisCloudClusterOperator deployArtemisClusterOperatorClustered(String namespace, List<String> watchedNamespaces) {
        return deployArtemisClusterOperator(namespace, false, watchedNamespaces);
    }

    public static ArtemisCloudClusterOperator deployArtemisClusterOperator(String namespace, boolean isNamespaced, List<String> watchedNamespaces) {
        if (projectCODeploy) {
            LOGGER.info("Deploying Artemis CO");
            ArtemisCloudClusterOperator clusterOperator = null;
            clusterOperator = new ArtemisCloudClusterOperator(namespace, isNamespaced);

            if (!isNamespaced) {
                clusterOperator.watchNamespaces(watchedNamespaces);
                clusterOperator.updateClusterRoleBinding(namespace);
            }
            clusterOperator.deployOperator(true);
            deployedOperators.add(clusterOperator);
            return clusterOperator;
        } else {
            LOGGER.warn("Not deploying operator! " + "'" + Constants.EV_CLUSTER_OPERATOR_MANAGED + "' is 'false'");
            return null;
        }
    }

    public static void undeployArtemisClusterOperator(ArtemisCloudClusterOperator clusterOperator) {
        if (projectCODeploy) {
            clusterOperator.undeployOperator(true);
            deployedOperators.remove(clusterOperator);
        } else {
            LOGGER.warn("Not undeploying operator! " + "'" + Constants.EV_CLUSTER_OPERATOR_MANAGED + "' is 'false'");
        }
    }

    public static boolean isClusterOperatorManaged() {
        return projectCODeploy;
    }

    public static List<ArtemisCloudClusterOperator> getArtemisClusterOperators() {
        return deployedOperators;
    }

    public static ArtemisCloudClusterOperator getArtemisClusterOperator(String namespace) {
        return deployedOperators.stream().filter(operator -> operator.getNamespace().equals(namespace)).findFirst().orElse(null);
    }

    /*******************************************************************************************************************
     *  ActiveMQArtemis Usage of generated typed API
     ******************************************************************************************************************/
    public static ActiveMQArtemis createArtemis(String namespace, String filePath) {
        return createArtemis(namespace, filePath, true);
    }

    public static ActiveMQArtemis createArtemis(String namespace, String filePath, boolean waitForDeployment) {
        ActiveMQArtemis artemisBroker = TestUtils.configFromYaml(filePath, ActiveMQArtemis.class);
        artemisBroker = ResourceManager.getArtemisClient().inNamespace(namespace).resource(artemisBroker).createOrReplace();
        LOGGER.info("[{}] Created ActiveMQArtemis {}", namespace, artemisBroker);
        if (waitForDeployment) {
            waitForBrokerDeployment(namespace, artemisBroker);
        }
        ResourceManager.addArtemisBroker(artemisBroker);
        return artemisBroker;
    }

    public static ActiveMQArtemis createArtemisFromString(String namespace, InputStream yamlStream, boolean waitForDeployment) {
        LOGGER.trace("[{}] Deploying broker using stringYaml {}", namespace, yamlStream);
        ActiveMQArtemis brokerCR = ResourceManager.getArtemisClient().inNamespace(namespace).load(yamlStream).get();
        brokerCR = ResourceManager.getArtemisClient().inNamespace(namespace).resource(brokerCR).createOrReplace();
        if (waitForDeployment) {
            waitForBrokerDeployment(namespace, brokerCR);
        }
        ResourceManager.addArtemisBroker(brokerCR);
        LOGGER.info("[{}] Created ActiveMQArtemis {} in namespace {}", namespace, brokerCR);
        return brokerCR;
    }

    public static ActiveMQArtemis createArtemis(String namespace, ActiveMQArtemis artemisBroker) {
        return createArtemis(namespace, artemisBroker, true);
    }

    public static ActiveMQArtemis createArtemis(String namespace, ActiveMQArtemis artemisBroker, boolean waitForDeployment) {
        artemisBroker = ResourceManager.getArtemisClient().inNamespace(namespace).resource(artemisBroker).createOrReplace();
        LOGGER.info("Created ActiveMQArtemis {} in namespace {}", artemisBroker, namespace);
        if (waitForDeployment) {
            waitForBrokerDeployment(namespace, artemisBroker);
        }
        ResourceManager.addArtemisBroker(artemisBroker);
        return artemisBroker;
    }

    public static void deleteArtemis(String namespace, ActiveMQArtemis broker) {
        deleteArtemis(namespace, broker, true, Constants.DURATION_1_MINUTE);
    }

    public static void deleteArtemis(String namespace, ActiveMQArtemis broker, boolean waitForDeletion, long maxTimeout) {
        String brokerName = broker.getMetadata().getName();
        ResourceManager.getArtemisClient().inNamespace(namespace).resource(broker).delete();
        if (waitForDeletion) {
            waitForBrokerDeletion(namespace, brokerName, maxTimeout);
        }
        ResourceManager.removeArtemisBroker(broker);
        LOGGER.info("[{}] Deleted ActiveMQArtemis {}", namespace, broker.getMetadata().getName());
    }

    public static ActiveMQArtemisAddress createArtemisAddress(String namespace, String filePath) {
        ActiveMQArtemisAddress artemisAddress = TestUtils.configFromYaml(filePath, ActiveMQArtemisAddress.class);
        artemisAddress = ResourceManager.getArtemisAddressClient().inNamespace(namespace).resource(artemisAddress).createOrReplace();
        // TODO check it programmatically
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        ResourceManager.addArtemisAddress(artemisAddress);
        LOGGER.info("[{}] Created ActiveMQArtemisAddress {}", namespace, artemisAddress.getMetadata().getName());
        return artemisAddress;
    }

    public static List<StatusDetails> deleteArtemisAddress(String namespace, String addressName) {
        throw new NotImplementedException();
    }
    public static void deleteArtemisAddress(String namespace, ActiveMQArtemisAddress artemisAddress) {
        List<StatusDetails> status = ResourceManager.getArtemisAddressClient().inNamespace(namespace).resource(artemisAddress).delete();
        ResourceManager.removeArtemisAddress(artemisAddress);
        LOGGER.info("[{}] Deleted ActiveMQArtemisAddress {}", namespace, artemisAddress.getMetadata().getName());
    }

    public static ActiveMQArtemisSecurity createArtemisSecurity(String namespace, String filePath) {
        ActiveMQArtemisSecurity artemisSecurity = TestUtils.configFromYaml(filePath, ActiveMQArtemisSecurity.class);
        artemisSecurity = ResourceManager.getArtemisSecurityClient().inNamespace(namespace).resource(artemisSecurity).createOrReplace();
        LOGGER.info("[{}] Created ActiveMQArtemisSecurity {}", namespace, artemisSecurity.getMetadata().getName());
        ResourceManager.addArtemisSecurity(artemisSecurity);
        return artemisSecurity;
    }

    public static List<StatusDetails> deleteArtemisSecurity(String namespace, ActiveMQArtemisSecurity artemisSecurity) {
        List<StatusDetails> status = ResourceManager.getArtemisSecurityClient().inNamespace(namespace).resource(artemisSecurity).delete();
        LOGGER.info("[{}] Deleted ActiveMQArtemisSecurity {}", namespace, artemisSecurity.getMetadata().getName());
        ResourceManager.removeArtemisSecurity(artemisSecurity);
        return status;
    }

    public static void waitForBrokerDeployment(String namespace, ActiveMQArtemis broker) {
        waitForBrokerDeployment(namespace, broker, false, Constants.DURATION_1_MINUTE);
    }

    public static void waitForBrokerDeployment(String namespace, ActiveMQArtemis broker, boolean reloadExisting) {
        waitForBrokerDeployment(namespace, broker, reloadExisting, Constants.DURATION_1_MINUTE);
    }

    public static void waitForBrokerDeployment(String namespace, ActiveMQArtemis broker, boolean reloadExisting, long maxTimeout) {
        LOGGER.info("[{}] Waiting for creation of broker {}", namespace, broker.getMetadata().getName());
        String brokerName = broker.getMetadata().getName();
        if (reloadExisting) {
            // TODO: make more generic and resource specific wait
            LOGGER.debug("[{}] Reloading existing broker {}, sleeping for some time", namespace, broker.getMetadata().getName());
            TestUtils.threadSleep(Constants.DURATION_5_SECONDS);
        }
        TestUtils.waitFor("StatefulSet to be ready", Constants.DURATION_5_SECONDS, maxTimeout, () -> {
            StatefulSet ss = kubeClient.getStatefulSet(namespace, brokerName + "-ss");
            return ss != null && ss.getStatus().getReadyReplicas() != null && ss.getStatus().getReadyReplicas().equals(ss.getSpec().getReplicas());
        });
    }

    public static void waitForBrokerDeletion(String namespace, String brokerName, long maxTimeout) {
        LOGGER.info("[{}] Waiting for deletion of broker {}", namespace, brokerName);
        TestUtils.waitFor("ActiveMQArtemis statefulSet & related pods to be removed", Constants.DURATION_5_SECONDS, maxTimeout, () -> {
            StatefulSet ss = kubeClient.getStatefulSet(namespace, brokerName + "-ss");
            return ss == null && kubeClient.listPodsByPrefixInName(namespace, brokerName).size() == 0;
        });
    }

    // MessagingClient Deployment
    public static Deployment deployClientsContainer(String testNamespace) {
        Deployment deployment = MessagingAmqpClient.deployClientsContainer(testNamespace);
        deployedContainers.put(deployment, testNamespace);
        return deployment;
    }

    public static Deployment deploySecuredClientsContainer(String testNamespace, String secret) {
        Deployment deployment = MessagingAmqpClient.deployClientsContainer(testNamespace, true, secret);
        deployedContainers.put(deployment, testNamespace);
        return deployment;
    }

    public static void undeployClientsContainer(String namespace, Deployment deployment) {
        kubeClient.getKubernetesClient().apps().deployments().inNamespace(namespace).resource(deployment).delete();
        deployedContainers.remove(deployment);
    }

    // Deployed Artemis Broker CRs
    public static void addArtemisBroker(ActiveMQArtemis broker) {
        deployedBrokers.add(broker);
    }

    public static void removeArtemisBroker(ActiveMQArtemis broker) {
        deployedBrokers.remove(broker);
    }

    public static void addArtemisAddress(ActiveMQArtemisAddress address) {
        deployedAddresses.add(address);
    }

    public static void removeArtemisAddress(ActiveMQArtemisAddress address) {
        deployedAddresses.remove(address);
    }

    public static void addArtemisSecurity(ActiveMQArtemisSecurity security) {
        deployedSecurity.add(security);
    }

    public static void removeArtemisSecurity(ActiveMQArtemisSecurity security) {
        deployedSecurity.remove(security);
    }

    // Kubernetes Resources - Namespace
    public static void addNamespace(String namespaceName) {
        deployedNamespaces.add(namespaceName);
    }

    public static void removeNamespace(String namespaceName) {
        deployedNamespaces.remove(namespaceName);
    }

    private static void undeployAllNamespaces() {
        // Issue command for all namespaces to be deleted
        for (String namespace : deployedNamespaces) {
            kubeClient.getKubernetesClient().namespaces().withName(namespace).delete();
            LOGGER.warn("Undeploying orphaned namespace {}!", namespace);
        }
        // Wait for namespaces to be deleted
        for (String namespace : deployedNamespaces) {
            TestUtils.waitFor("Deletion of namespace", Constants.DURATION_2_SECONDS, Constants.DURATION_3_MINUTES,
                    () -> !kubeClient.namespaceExists(namespace));
        }
    }

    public static void undeployAllResources() {
        ResourceManager.undeployAllClientsContainers();
        ResourceManager.undeployAllArtemisClusterOperators();
        ResourceManager.undeployAllArtemisSecurity();
        ResourceManager.undeployAllArtemisAddress();
        ResourceManager.undeployAllArtemisBroker();
        ResourceManager.undeployAllNamespaces();
    }

    public static void undeployAllArtemisClusterOperators() {
        for (ArtemisCloudClusterOperator operator : deployedOperators) {
            LOGGER.warn("[{}] Undeploying orphaned ArtemisOperator {} !", operator.getNamespace(), operator.getOperatorName());
            undeployArtemisClusterOperator(operator);
        }
    }

    private static void undeployAllArtemisBroker() {
        for (ActiveMQArtemis broker : deployedBrokers) {
            LOGGER.warn("[{}] Undeploying orphaned Artemis {}!", broker.getMetadata().getNamespace(), broker.getMetadata().getName());
            ResourceManager.getArtemisClient().inNamespace(broker.getMetadata().getNamespace()).resource(broker).delete();
        }
    }

    private static void undeployAllArtemisAddress() {
        for (ActiveMQArtemisAddress address : deployedAddresses) {
            LOGGER.warn("[{}] Undeploying orphaned ArtemisAddress {}!", address.getMetadata().getNamespace(), address.getMetadata().getName());
            ResourceManager.getArtemisAddressClient().inNamespace(address.getMetadata().getNamespace()).resource(address).delete();
        }
    }

    private static void undeployAllArtemisSecurity() {
        for (ActiveMQArtemisSecurity security : deployedSecurity) {
            LOGGER.warn("[{}] Undeploying orphaned ArtemisSecurity {}!", security.getMetadata().getNamespace(), security.getMetadata().getName());
            ResourceManager.getArtemisSecurityClient().inNamespace(security.getMetadata().getNamespace()).resource(security).delete();
        }
    }

    public static void undeployAllClientsContainers() {
        for (Deployment deployment : deployedContainers.keySet()) {
            LOGGER.info("[{}] Undeploying orphaned MessagingClient {}!", deployedContainers.get(deployment), deployment.getMetadata().getName());
            kubeClient.getKubernetesClient().apps().deployments().inNamespace(deployedContainers.get(deployment)).resource(deployment).delete();
        }
    }
}
