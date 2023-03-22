/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisAddress;
import io.amq.broker.v1beta1.ActiveMQArtemisAddressBuilder;
import io.amq.broker.v1beta1.ActiveMQArtemisBuilder;
import io.amq.broker.v1beta1.ActiveMQArtemisScaledown;
import io.amq.broker.v1beta1.ActiveMQArtemisSecurity;
import io.amq.broker.v1beta1.activemqartemisstatus.Conditions;
import io.brokerqe.clients.MessagingAmqpClient;
import io.brokerqe.operator.ArtemisCloudClusterOperator;
import io.brokerqe.operator.ArtemisCloudClusterOperatorFile;
import io.brokerqe.operator.ArtemisCloudClusterOperatorOlm;
import io.brokerqe.security.Keycloak;
import io.brokerqe.security.Openldap;
import io.brokerqe.security.Rhsso;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

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
    private static Environment environment;

    private ResourceManager(Environment env) {
        kubeClient = env.getKubeClient();
        artemisClient = kubeClient.getKubernetesClient().resources(ActiveMQArtemis.class);
        artemisAddressClient = kubeClient.getKubernetesClient().resources(ActiveMQArtemisAddress.class);
        artemisSecurityClient = kubeClient.getKubernetesClient().resources(ActiveMQArtemisSecurity.class);
        artemisScaledownClient = kubeClient.getKubernetesClient().resources(ActiveMQArtemisScaledown.class);
        projectCODeploy = env.isProjectManagedClusterOperator();
        environment = env;
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
            ArtemisCloudClusterOperator clusterOperator;
            if (environment.isOlmInstallation()) {
                clusterOperator = new ArtemisCloudClusterOperatorOlm(namespace, isNamespaced, watchedNamespaces);
            } else {
                clusterOperator = new ArtemisCloudClusterOperatorFile(namespace, isNamespaced, watchedNamespaces);
            }
            clusterOperator.deployOperator(true);
            deployedOperators.add(clusterOperator);
            return clusterOperator;
        } else {
            LOGGER.warn("Not deploying operator! " + "'" + Constants.EV_CLUSTER_OPERATOR_MANAGED + "' is 'false'");
            return null;
        }
    }

    public static ArtemisCloudClusterOperatorOlm deployArtemisClusterOperatorOlm(String namespace, List<String> watchedNamespaces, String channel, String indexImageBundle) {
        if (environment.isOlmInstallation()) {
            ArtemisCloudClusterOperatorOlm clusterOperator = new ArtemisCloudClusterOperatorOlm(namespace, true, watchedNamespaces, indexImageBundle, channel);
            clusterOperator.deployOperator(true);
            deployedOperators.add(clusterOperator);
            return clusterOperator;
        } else {
            LOGGER.warn("Not deploying operator! Not an OLM installation type.");
            return null;
        }
    }

    public static void deployArtemisClusterOperatorCRDs() {
        if (projectCODeploy) {
            ArtemisCloudClusterOperatorFile.deployOperatorCRDs();
        } else {
            LOGGER.warn("Not undeploying operator CRDs! " + "'" + Constants.EV_CLUSTER_OPERATOR_MANAGED + "' is 'false'");
        }
    }

    public static void undeployArtemisClusterOperatorCRDs() {
        if (projectCODeploy) {
            ArtemisCloudClusterOperatorFile.undeployOperatorCRDs(false);
        } else {
            LOGGER.warn("Not undeploying operator CRDs! " + "'" + Constants.EV_CLUSTER_OPERATOR_MANAGED + "' is 'false'");
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
        return deployedOperators.stream().filter(operator -> operator.getDeploymentNamespace().equals(namespace)).findFirst().orElse(null);
    }

    /*******************************************************************************************************************
     *  ActiveMQArtemis Usage of generated typed API
     ******************************************************************************************************************/

    public static ActiveMQArtemis createArtemis(String namespace, String name) {
        return createArtemis(namespace, name, 1);
    }
    public static ActiveMQArtemis createArtemis(String namespace, String name, int size) {
        return createArtemis(namespace, name, size, false, false, false);
    }

    public static ActiveMQArtemis createArtemis(String namespace, String name, int size, boolean upgradeEnabled, boolean upgradeMinor) {
        return createArtemis(namespace, name, size, upgradeEnabled, upgradeMinor, false);
    }
    public static ActiveMQArtemis createArtemis(String namespace, String name, int size, boolean upgradeEnabled, boolean upgradeMinor, boolean exposeConsole) {
        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
            .editOrNewMetadata()
                .withName(name)
                .withNamespace(namespace)
            .endMetadata()
            .editOrNewSpec()
                .withNewDeploymentPlan()
                    .withSize(size)
                    .withPersistenceEnabled()
                    .withMessageMigration()
                .endDeploymentPlan()
                .editOrNewUpgrades()
                    .withEnabled(upgradeEnabled)
                    .withMinor(upgradeMinor)
                .endUpgrades()
                .editOrNewConsole()
                    .withExpose(exposeConsole)
                .endConsole()
            .endSpec()
            .build();

        long waitTime = Constants.DURATION_1_MINUTE + Constants.DURATION_30_SECONDS;
        if (size > 1) {
            waitTime += Constants.DURATION_1_MINUTE * size;
        }
        return createArtemis(namespace, broker, true, waitTime);
    }

    public static ActiveMQArtemis createArtemis(String namespace, Path filePath) {
        return createArtemis(namespace, filePath, true);
    }

    public static ActiveMQArtemis createArtemis(String namespace, Path filePath, boolean waitForDeployment) {
        ActiveMQArtemis artemisBroker = TestUtils.configFromYaml(filePath.toFile(), ActiveMQArtemis.class);
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
        return createArtemis(namespace, artemisBroker, waitForDeployment, Constants.DURATION_1_MINUTE);
    }

    public static ActiveMQArtemis createArtemis(String namespace, ActiveMQArtemis artemisBroker, boolean waitForDeployment, long maxTimeout) {
        artemisBroker = ResourceManager.getArtemisClient().inNamespace(namespace).resource(artemisBroker).createOrReplace();
        LOGGER.info("Created ActiveMQArtemis {} in namespace {}", artemisBroker, namespace);
        if (waitForDeployment) {
            waitForBrokerDeployment(namespace, artemisBroker, false, maxTimeout);
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

    public static ActiveMQArtemisAddress createArtemisAddress(String namespace, Path filePath) {
        ActiveMQArtemisAddress artemisAddress = TestUtils.configFromYaml(filePath.toFile(), ActiveMQArtemisAddress.class);
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

    public static ActiveMQArtemisAddress createArtemisAddress(String namespace, String addressName, String queueName) {
        return createArtemisAddress(namespace, addressName, queueName, Constants.ROUTING_TYPE_ANYCAST);
    }

    public static ActiveMQArtemisAddress createArtemisAddress(String namespace, String addressName, String queueName, String routingType) {
        String compoundName = addressName;
        if (queueName == null || queueName.equals("")) {
            queueName = addressName;
        } else {
            compoundName += "-" + queueName;
        }

        ActiveMQArtemisAddress artemisAddress = new ActiveMQArtemisAddressBuilder()
                .editOrNewMetadata()
                    .withName(compoundName)
                .endMetadata()
                .editOrNewSpec()
                    .withAddressName(addressName)
                    .withQueueName(queueName)
                    .withRoutingType(routingType)
                .endSpec()
                .build();
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

    public static void deleteArtemisAddress(String namespace, ActiveMQArtemisAddress artemisAddress) {
        ResourceManager.getArtemisAddressClient().inNamespace(namespace).resource(artemisAddress).delete();
        ResourceManager.removeArtemisAddress(artemisAddress);
        LOGGER.info("[{}] Deleted ActiveMQArtemisAddress {}", namespace, artemisAddress.getMetadata().getName());
    }

    public static ActiveMQArtemisSecurity createArtemisSecurity(String namespace, ActiveMQArtemisSecurity artemisSecurity) {
        artemisSecurity = ResourceManager.getArtemisSecurityClient().inNamespace(namespace).resource(artemisSecurity).createOrReplace();
        LOGGER.info("[{}] Created ActiveMQArtemisSecurity {}", namespace, artemisSecurity.getMetadata().getName());
        ResourceManager.addArtemisSecurity(artemisSecurity);
        return artemisSecurity;
    }

    public static void deleteArtemisSecurity(String namespace, ActiveMQArtemisSecurity artemisSecurity) {
        ResourceManager.getArtemisSecurityClient().inNamespace(namespace).resource(artemisSecurity).delete();
        LOGGER.info("[{}] Deleted ActiveMQArtemisSecurity {}", namespace, artemisSecurity.getMetadata().getName());
        waitForArtemisSecurityDeletion(namespace, artemisSecurity, Constants.DURATION_1_MINUTE);
        ResourceManager.removeArtemisSecurity(artemisSecurity);
    }

    public static void waitForArtemisSecurityDeletion(String namespace, ActiveMQArtemisSecurity artemisSecurity, long maxTimeout) {
        LOGGER.info("[{}] Waiting {}s for deletion of security CR {}", namespace, Duration.ofMillis(maxTimeout).toSeconds(), artemisSecurity);
        TestUtils.waitFor("deletion of ActiveMQArtemisSecurity CR", Constants.DURATION_5_SECONDS, maxTimeout, () -> {
            ActiveMQArtemisSecurity security = ResourceManager.getArtemisSecurityClient().resource(artemisSecurity).get();
            return security == null;
        });
    }
    
    public static void waitForArtemisStatusUpdate(String namespaceName, ActiveMQArtemis artemis) {
        LOGGER.info("Waiting for Artemis status to be updated");
        Long originalGeneration = artemis.getMetadata().getGeneration() == null ? 0L : artemis.getMetadata().getGeneration();
        TestUtils.waitFor("ArtemisStatus update", Constants.DURATION_2_SECONDS, Constants.DURATION_30_SECONDS, () -> {
            ActiveMQArtemis updatedArtemis = getArtemisClient().inNamespace(namespaceName).resource(artemis).get();
            if (updatedArtemis.getStatus() != null) {
                Optional<Conditions> condition = updatedArtemis.getStatus().getConditions().stream().filter(e -> e.getObservedGeneration() != null).findFirst();
                Long currentGeneration = -1L;
                if (condition.isPresent() && condition.get().getObservedGeneration() != null) {
                    currentGeneration = condition.get().getObservedGeneration();
                }
                return currentGeneration >= originalGeneration && updatedArtemis.getMetadata().getGeneration().equals(currentGeneration);
            } else {
                return false;
            }
        });
    }
    public static void getArtemisStatus(String namespace, ActiveMQArtemis artemis, String expectedType, String expectedReason) {
        getArtemisStatus(namespace, artemis, expectedType, expectedReason, null);
    }

    public static boolean getArtemisStatus(String namespace, ActiveMQArtemis artemis, String expectedType, String expectedReason,
                                                  String message) {
        waitForArtemisStatusUpdate(namespace, artemis);
        ActiveMQArtemis updatedArtemis = getArtemisClient().inNamespace(namespace).resource(artemis).get();
        List<Conditions> conditions = updatedArtemis.getStatus().getConditions();
        for (Conditions condition : conditions) {
            if (condition.getType().equals(expectedType) && condition.getReason().equals(expectedReason)) {
                return message != null && condition.getMessage().matches(message);
            }
            return false;
        }
        return false;
    }

    public static void waitForArtemisResourceStatusUpdate(ActiveMQArtemis initialArtemis, String namespace, String updateType, long timeoutMillis) {
        LOGGER.info("[{}] Waiting for 5 minutes for broker {} custom resource status update", namespace, initialArtemis.getMetadata().getName());
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        getArtemisClient().inNamespace(namespace).resource(initialArtemis).waitUntilCondition(updatedBroker -> {
            for (Conditions condition : updatedBroker.getStatus().getConditions()) {
                if (condition.getType().equals(updateType)) {
                    if (condition.getReason().equals(Constants.CONDITION_REASON_APPLIED)) {
                        LocalDateTime updateDate = LocalDateTime.parse(condition.getLastTransitionTime(), dtf);
                        LocalDateTime initialDate = LocalDateTime.parse(initialArtemis.getMetadata().getManagedFields().get(0).getTime(), dtf);
                        if (updateDate.isAfter(initialDate)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }, timeoutMillis, TimeUnit.MILLISECONDS);
    }

    public static void waitForBrokerDeployment(String namespace, ActiveMQArtemis broker) {
        waitForBrokerDeployment(namespace, broker, false, Constants.DURATION_1_MINUTE);
    }

    public static void waitForBrokerDeployment(String namespace, ActiveMQArtemis broker, boolean reloadExisting) {
        waitForBrokerDeployment(namespace, broker, reloadExisting, Constants.DURATION_1_MINUTE);
    }

    public static void waitForBrokerDeployment(String namespace, ActiveMQArtemis broker, boolean reloadExisting, long maxTimeout) {
        waitForBrokerDeployment(namespace, broker, reloadExisting, maxTimeout, null);
    }

    public static void waitForBrokerDeployment(String namespace, ActiveMQArtemis broker, boolean reloadExisting, long maxTimeout, StatefulSet oldStatefulSet) {
        LOGGER.info("[{}] Waiting {}s for creation of broker {}", namespace, Duration.ofMillis(maxTimeout).toSeconds(), broker.getMetadata().getName());
        String brokerName = broker.getMetadata().getName();

        if (reloadExisting) {
            LOGGER.info("[{}] Reloading existing broker {}, sleeping for some time", namespace, broker.getMetadata().getName());
            TestUtils.threadSleep(Constants.DURATION_5_SECONDS);
        }
        TestUtils.waitFor("StatefulSet to be ready", Constants.DURATION_5_SECONDS, maxTimeout, () -> {
            boolean toReturn = false;
            StatefulSet ss = kubeClient.getStatefulSet(namespace, brokerName + "-ss");
            toReturn = ss != null && ss.getStatus().getReadyReplicas() != null && ss.getStatus().getReadyReplicas().equals(ss.getSpec().getReplicas());
            if (reloadExisting && oldStatefulSet != null) {
                LOGGER.warn("WAIT FOR RELOAD OF SS");
                toReturn = toReturn && !oldStatefulSet.getMetadata().getUid().equals(ss.getMetadata().getUid());
            }
            return toReturn;
        });
    }

    public static void waitForBrokerDeletion(String namespace, String brokerName, long maxTimeout) {
        LOGGER.info("[{}] Waiting {}s for deletion of broker {}", namespace, Duration.ofMillis(maxTimeout).toSeconds(), brokerName);
        TestUtils.waitFor("ActiveMQArtemis statefulSet & related pods to be removed", Constants.DURATION_5_SECONDS, maxTimeout, () -> {
            StatefulSet ss = kubeClient.getStatefulSet(namespace, brokerName + "-ss");
            return ss == null && kubeClient.listPodsByPrefixName(namespace, brokerName).size() == 0;
        });
    }

    // MessagingClient Deployment
    public static Deployment deployClientsContainer(String testNamespace) {
        Deployment deployment = MessagingAmqpClient.deployClientsContainer(testNamespace);
        deployedContainers.put(deployment, testNamespace);
        return deployment;
    }

    public static Deployment deploySecuredClientsContainer(String testNamespace, List<String> secrets) {
        Deployment deployment = MessagingAmqpClient.deployClientsContainer(testNamespace, true, secrets);
        deployedContainers.put(deployment, testNamespace);
        return deployment;
    }

    public static void undeployClientsContainer(String namespace, Deployment deployment) {
        kubeClient.getKubernetesClient().apps().deployments().inNamespace(namespace).resource(deployment).delete();
        deployedContainers.remove(deployment);
    }

    // Deployed Artemis Broker CRs
    protected static void addArtemisBroker(ActiveMQArtemis broker) {
        deployedBrokers.add(broker);
    }

    protected static void removeArtemisBroker(ActiveMQArtemis broker) {
        boolean result = deployedBrokers.remove(broker);
        if (!result && getArtemisClient().resource(broker).get() == null) {
            removeUpdatedCrObject(deployedBrokers, broker);
            LOGGER.debug("[{}] Removed updated ActiveMQArtemis object {}", broker.getMetadata().getNamespace(), broker.getMetadata().getName());
        }
    }

    protected static void addArtemisAddress(ActiveMQArtemisAddress address) {
        deployedAddresses.add(address);
    }

    protected static void removeArtemisAddress(ActiveMQArtemisAddress address) {
        boolean result = deployedAddresses.remove(address);
        if (!result && getArtemisAddressClient().resource(address).get() == null) {
            removeUpdatedCrObject(deployedAddresses, address);
            LOGGER.debug("[{}] Removed updated ActiveMQArtemisAddress object {}", address.getMetadata().getNamespace(), address.getMetadata().getName());
        }
    }

    protected static void addArtemisSecurity(ActiveMQArtemisSecurity security) {
        deployedSecurity.add(security);
    }

    protected static void removeArtemisSecurity(ActiveMQArtemisSecurity security) {
        boolean result = deployedSecurity.remove(security);
        if (!result && getArtemisSecurityClient().resource(security).get() == null) {
            removeUpdatedCrObject(deployedSecurity, security);
            LOGGER.debug("[{}] Removed updated ActiveMQArtemisSecurity object {}", security.getMetadata().getNamespace(), security.getMetadata().getName());
        }
    }

    // Kubernetes Resources - Namespace
    protected static void addNamespace(String namespaceName) {
        deployedNamespaces.add(namespaceName);
    }

    protected static void removeNamespace(String namespaceName) {
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

    // Keycloak/Rhsso Resources
    public static Keycloak getKeycloakInstance(String namespace) {
        // Keycloak resources manage
        if (kubeClient.getKubernetesPlatform().equals(KubernetesPlatform.OPENSHIFT)) {
            return new Rhsso(environment, kubeClient, namespace);
        } else {
            return new Keycloak(environment, kubeClient, namespace);
        }
    }

    public static Openldap getOpenldapInstance(String namespace) {
        // Keycloak resources manage
        return new Openldap(environment, kubeClient, namespace);
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
        for (ArtemisCloudClusterOperator operator : List.copyOf(deployedOperators)) {
            LOGGER.warn("[{}] Undeploying orphaned ArtemisOperator {} !", operator.getDeploymentNamespace(), operator.getOperatorName());
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

    private static <T extends HasMetadata> void removeUpdatedCrObject(List<T> deployedCrObjects, T updatedCr) {
        List<T> removeTs = deployedCrObjects.stream().filter(item -> {
            String itemName = item.getMetadata().getName();
            String itemNamespace = item.getMetadata().getNamespace();
            return updatedCr.getMetadata().getName().equals(itemName) && updatedCr.getMetadata().getNamespace().equals(itemNamespace);
        }).toList();

        if (removeTs.size() == 1) {
            deployedCrObjects.remove(removeTs.get(0));
        } else if (removeTs.size() == 0) {
            LOGGER.error("[{}] Not found any CR! {} ", updatedCr.getMetadata().getNamespace(), updatedCr.getMetadata().getName());
        } else {
            LOGGER.error("[{}] Found too many CRs! {} ", updatedCr.getMetadata().getNamespace(), updatedCr.getMetadata().getName());
        }
    }

    public static Environment getEnvironment() {
        if (environment == null) {
            environment = new Environment();
        }
        return environment;
    }
}
