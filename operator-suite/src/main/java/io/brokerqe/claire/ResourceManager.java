/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisAddress;
import io.amq.broker.v1beta1.ActiveMQArtemisAddressBuilder;
import io.amq.broker.v1beta1.ActiveMQArtemisBuilder;
import io.amq.broker.v1beta1.ActiveMQArtemisScaledown;
import io.amq.broker.v1beta1.ActiveMQArtemisSecurity;
import io.amq.broker.v1beta1.activemqartemisstatus.Conditions;
import io.brokerqe.claire.clients.BundledClientDeployment;
import io.brokerqe.claire.clients.CliCppDeployment;
import io.brokerqe.claire.clients.CliJavaDeployment;
import io.brokerqe.claire.clients.CliProtonDotnetDeployment;
import io.brokerqe.claire.clients.CliProtonPythonDeployment;
import io.brokerqe.claire.clients.CliRheaDeployment;
import io.brokerqe.claire.clients.ClientType;
import io.brokerqe.claire.clients.DeployableClient;
import io.brokerqe.claire.clients.KubernetesDeployableClient;
import io.brokerqe.claire.clients.MessagingClient;
import io.brokerqe.claire.clients.MqttDeployment;
import io.brokerqe.claire.clients.bundled.BundledAmqpMessagingClient;
import io.brokerqe.claire.clients.bundled.BundledClientOptions;
import io.brokerqe.claire.clients.bundled.BundledCoreMessagingClient;
import io.brokerqe.claire.clients.container.AmqpProtonCppClient;
import io.brokerqe.claire.clients.container.AmqpProtonDotnetClient;
import io.brokerqe.claire.clients.container.AmqpProtonPythonClient;
import io.brokerqe.claire.clients.container.AmqpQpidClient;
import io.brokerqe.claire.clients.container.AmqpRheaClient;
import io.brokerqe.claire.clients.container.MqttV3Client;
import io.brokerqe.claire.clients.container.MqttV5Client;
import io.brokerqe.claire.db.Postgres;
import io.brokerqe.claire.exception.ClaireNotImplementedException;
import io.brokerqe.claire.exception.ClaireRuntimeException;
import io.brokerqe.claire.helpers.DataStorer;
import io.brokerqe.claire.operator.ArtemisCloudClusterOperator;
import io.brokerqe.claire.operator.ArtemisCloudClusterOperatorFile;
import io.brokerqe.claire.operator.ArtemisCloudClusterOperatorOlm;
import io.brokerqe.claire.security.KeyStoreData;
import io.brokerqe.claire.security.Keycloak;
import io.brokerqe.claire.security.Openldap;
import io.brokerqe.claire.security.Rhsso;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@SuppressWarnings({"checkstyle:ClassDataAbstractionCoupling", "checkstyle:ClassFanOutComplexity"})
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
    private static EnvironmentOperator environmentOperator;
    private static TestInfo testInfo;

    private ResourceManager(EnvironmentOperator env) {
        kubeClient = env.getKubeClient();
        artemisClient = kubeClient.getKubernetesClient().resources(ActiveMQArtemis.class);
        artemisAddressClient = kubeClient.getKubernetesClient().resources(ActiveMQArtemisAddress.class);
        artemisSecurityClient = kubeClient.getKubernetesClient().resources(ActiveMQArtemisSecurity.class);
        artemisScaledownClient = kubeClient.getKubernetesClient().resources(ActiveMQArtemisScaledown.class);
        projectCODeploy = env.isProjectManagedClusterOperator();
        environmentOperator = env;
    }
    public static ResourceManager getInstance(EnvironmentOperator environmentOperator) {
        if (resourceManager == null) {
            resourceManager = new ResourceManager(environmentOperator);
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
            if (environmentOperator.isOlmInstallation()) {
                if (environmentOperator.isOlmReleased()) {
                    clusterOperator = new ArtemisCloudClusterOperatorOlm(namespace, isNamespaced, watchedNamespaces, environmentOperator.isOlmLts());
                } else {
                    clusterOperator = new ArtemisCloudClusterOperatorOlm(namespace, isNamespaced, watchedNamespaces,
                            environmentOperator.getOlmIndexImageBundle(), environmentOperator.getOlmChannel());
                }
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
        if (environmentOperator.isOlmInstallation()) {
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
        return createArtemis(namespace, name, size, false, false, false, false);
    }

    public static ActiveMQArtemis createArtemis(String namespace, String name, int size, boolean upgradeEnabled, boolean upgradeMinor) {
        return createArtemis(namespace, name, size, upgradeEnabled, upgradeMinor, false, false);
    }
    public static ActiveMQArtemis createArtemis(String namespace, String name, int size, boolean upgradeEnabled, boolean upgradeMinor, boolean exposeConsole, boolean enableMigration) {
        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
            .editOrNewMetadata()
                .withName(name)
                .withNamespace(namespace)
            .endMetadata()
            .editOrNewSpec()
                .withNewDeploymentPlan()
                    .withSize(size)
                    .withPersistenceEnabled()
                    .withMessageMigration(enableMigration)
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
            waitForBrokerDeployment(namespace, artemisBroker, false, null, maxTimeout);
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
        return createArtemisAddress(namespace, addressName, queueName, ArtemisConstants.ROUTING_TYPE_ANYCAST);
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

    public static boolean getArtemisStatus(String namespace, ActiveMQArtemis artemis, String expectedType, String expectedReason, String message) {
        waitForArtemisStatusUpdate(namespace, artemis);
        ActiveMQArtemis updatedArtemis = getArtemisClient().inNamespace(namespace).resource(artemis).get();
        List<Conditions> conditions = updatedArtemis.getStatus().getConditions();
        boolean foundCondition = false;
        for (Conditions condition : conditions) {
            if (condition.getType().equals(expectedType) && condition.getReason().equals(expectedReason)) {
                foundCondition = message != null && condition.getMessage().contains(message);
                break;
            }
        }
        return foundCondition;
    }

    public static void waitForArtemisGenerationUpdate(String namespace, ActiveMQArtemis expectedBroker, long maxTimeout) {
        TestUtils.waitFor("Wait for next generation", Constants.DURATION_2_SECONDS, maxTimeout, () -> {
            ActiveMQArtemis updatedArtemis = getArtemisClient().inNamespace(namespace).resource(expectedBroker).get();
//                return updatedArtemis.getMetadata().getGeneration().equals(broker.getMetadata().getGeneration());
            // TODO kept for debugging purposes
            if (updatedArtemis.getMetadata().getGeneration().equals(expectedBroker.getMetadata().getGeneration())) {
                LOGGER.debug("[{}] ActiveMQArtemis Generation is same, moving forward.", namespace);
                return true;
            } else if (updatedArtemis.getMetadata().getGeneration() > (expectedBroker.getMetadata().getGeneration())) {
                LOGGER.error("[{}] Unexpected generation number {} is too high! Possible issue with Artemis CR /Operator.", namespace, updatedArtemis.getMetadata().getGeneration());
                throw new ClaireRuntimeException("Unexpectedly high generation number in ActiveMQArtemis CR!");
            } else {
                LOGGER.debug("[{}] ActiveMQArtemis Generation is different, waiting: {} vs {}",
                        namespace, updatedArtemis.getMetadata().getGeneration(), expectedBroker.getMetadata().getGeneration());
                return false;
            }
        });
    }

    public static void waitForArtemisStatusUpdate(String namespace, ActiveMQArtemis initialArtemis, String updateType, String expectedReason, long timeoutMillis) {
        waitForArtemisStatusUpdate(namespace, initialArtemis, updateType, expectedReason, timeoutMillis, true);
    }
    public static void waitForArtemisStatusUpdate(String namespace, ActiveMQArtemis initialArtemis, String updateType, String expectedReason, long timeoutMillis, boolean checkDate) {
        LOGGER.info("[{}] Waiting for broker {} custom resource status update, limit: {} seconds, period: 5 seconds", namespace, initialArtemis.getMetadata().getName(), timeoutMillis / 1000);
//        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        TestUtils.waitFor("Broker CR status to reach correct status", Constants.DURATION_5_SECONDS, timeoutMillis, () -> {
            ActiveMQArtemis updatedBroker = getArtemisClient().inNamespace(namespace).resource(initialArtemis).get();
            if (updatedBroker.getStatus() != null && updatedBroker.getStatus().getConditions() != null) {
                // if either is null, no status or conditions are yet published
                for (Conditions condition : updatedBroker.getStatus().getConditions()) {
                    if (condition.getType().equals(updateType)) {
                        if (condition.getReason().equals(expectedReason)) {
                            if (checkDate) {
                                ZonedDateTime updateDate = condition.getLastTransitionTime();
                                ZonedDateTime initialDate = ZonedDateTime.parse(initialArtemis.getMetadata().getCreationTimestamp());
                                LOGGER.debug("[{}] Comparing time of Broker creation ({}) to time of BrokerProperties application ({})", namespace, initialDate, updateDate);
                                if (updateDate.isAfter(initialDate)) {
                                    return true;
                                }
                            } else {
                                LOGGER.debug("[{}] Time/date of the condition update doesn't matter due to call options", namespace);
                                return true;
                            }
                        }
                    } else {
                        LOGGER.trace("[{}] Found condition: {}, it is not expected one (yet)", namespace, condition);
                    }
                }
            }
            return false;
        });
    }

    public static void waitForBrokerDeployment(String namespace, ActiveMQArtemis broker) {
        waitForBrokerDeployment(namespace, broker, false, null, Constants.DURATION_1_MINUTE);
    }

    public static void waitForBrokerDeployment(String namespace, ActiveMQArtemis broker, boolean reloadExisting, Pod existingPod) {
        waitForBrokerDeployment(namespace, broker, reloadExisting, existingPod, Constants.DURATION_1_MINUTE);
    }

    public static void waitForBrokerDeployment(String namespace, ActiveMQArtemis broker, boolean reloadExisting, Pod existingPod, long maxTimeout) {
        waitForBrokerDeployment(namespace, broker, reloadExisting, existingPod, maxTimeout, null);
    }

    public static void waitForBrokerDeployment(String namespace, ActiveMQArtemis broker, boolean reloadExisting, Pod brokerPod, long maxTimeout, StatefulSet oldStatefulSet) {
        LOGGER.info("[{}] Waiting {}s for creation of broker {}", namespace, Duration.ofMillis(maxTimeout).toSeconds(), broker.getMetadata().getName());
        String brokerName = broker.getMetadata().getName();
        int expectedPodCount = 1;
        if (broker.getSpec() != null && broker.getSpec().getDeploymentPlan() != null) {
            expectedPodCount = broker.getSpec().getDeploymentPlan().getSize();
        }

        TestUtils.waitFor("StatefulSet to be ready", Constants.DURATION_5_SECONDS, maxTimeout, () -> {
            StatefulSet ss = kubeClient.getStatefulSet(namespace, brokerName + "-ss");
            boolean toReturn = ss != null && ss.getStatus().getReadyReplicas() != null && ss.getStatus().getReadyReplicas().equals(ss.getSpec().getReplicas());
            if (reloadExisting && oldStatefulSet != null) {
                LOGGER.debug("[{}] Wait for reload & readiness of older Statefulset", namespace);
                toReturn = toReturn && !oldStatefulSet.getMetadata().getUid().equals(ss.getMetadata().getUid());
            }
            if (ss != null && ss.getSpec().getReplicas() == 0 && ss.getStatus().getReadyReplicas() == null) {
                toReturn = true;
            }
            return toReturn;
        });

        if (reloadExisting) {
            LOGGER.info("[{}] Reloading existing broker {}, sleeping for some time", namespace, broker.getMetadata().getName());
            waitForArtemisGenerationUpdate(namespace, broker, maxTimeout);
            if (brokerPod != null) {
                long timeout = maxTimeout;
                if (expectedPodCount > 1) {
                    timeout += Constants.DURATION_30_SECONDS + Constants.DURATION_1_MINUTE * expectedPodCount;
                }
                getKubeClient().waitForPodReload(namespace, brokerPod, brokerPod.getMetadata().getName(), timeout);
            }
        }
        waitForBrokerPodsExpectedCount(namespace, broker, expectedPodCount, maxTimeout);
    }

    public static void waitForBrokerPodsExpectedCount(String namespace, ActiveMQArtemis broker, int expectedSize, long maxTimeout) {
        LOGGER.debug("[{}] Waiting for expected broker pods count: {}", namespace, expectedSize);
        TestUtils.waitFor("all broker pods to start", Constants.DURATION_5_SECONDS, maxTimeout, () -> {
            List<Pod> brokers = kubeClient.listPodsByPrefixName(namespace, broker.getMetadata().getName());
            boolean isExpectedSize = brokers.size() == expectedSize;
            if (isExpectedSize) {
                for (Pod brokerPod : brokers) {
                    kubeClient.waitUntilPodIsReady(namespace, brokerPod);
                }
            }
            return isExpectedSize;
        });
    }

    public static void waitForBrokerDeletion(String namespace, String brokerName, long maxTimeout) {
        LOGGER.info("[{}] Waiting {}s for deletion of broker {}", namespace, Duration.ofMillis(maxTimeout).toSeconds(), brokerName);
        TestUtils.waitFor("ActiveMQArtemis statefulSet & related pods to be removed", Constants.DURATION_5_SECONDS, maxTimeout, () -> {
            StatefulSet ss = kubeClient.getStatefulSet(namespace, brokerName + "-ss");
            return ss == null && kubeClient.listPodsByPrefixName(namespace, brokerName).size() == 0;
        });
    }

    // Deployed Artemis Broker CRs
    protected static void addArtemisBroker(ActiveMQArtemis broker) {
        deployedBrokers.add(broker);
        DataStorer.dumpResourceToFile(broker);
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
        DataStorer.dumpResourceToFile(address);
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
        DataStorer.dumpResourceToFile(security);
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

    public static Deployment deployClientsContainer(String testNamespace) {
        DeployableClient deployableClient = new CliJavaDeployment(testNamespace);
        Deployment deployment = (Deployment) deployableClient.deployContainer();
        deployedContainers.put(deployment, testNamespace);
        DataStorer.dumpResourceToFile(deployment);
        return deployment;
    }

    public static Deployment deploySecuredClientsContainer(String testNamespace, List<String> secrets) {
        DeployableClient<Deployment, Pod> deployableClient = new CliJavaDeployment(testNamespace);
        Deployment deployment = deployableClient.deployContainer(true, secrets);
        deployedContainers.put(deployment, testNamespace);
        DataStorer.dumpResourceToFile(deployment);
        return deployment;
    }

    public static void undeployClientsContainer(KubernetesDeployableClient deployableClient) {
        deployableClient.undeployContainer();
        deployedContainers.remove(deployableClient.getDeployment(), deployableClient.getNamespace());
    }

    public static void undeployClientsContainer(String namespace, Deployment deployment) {
        LOGGER.debug("[{}] Removed clients deployment {}", deployment.getMetadata().getNamespace(), deployment.getMetadata().getName());
        kubeClient.getKubernetesClient().apps().deployments().inNamespace(namespace).resource(deployment).delete();
        deployedContainers.remove(deployment);
    }

    public static Deployment deployCliProtonDotnetContainer(String testNamespace) {
        DeployableClient deployableClient = new CliProtonDotnetDeployment(testNamespace);
        Deployment deployment = (Deployment) deployableClient.deployContainer();
        deployedContainers.put(deployment, testNamespace);
        DataStorer.dumpResourceToFile(deployment);
        return deployment;
    }

    public static Deployment deploySecuredCliProtonDotnetContainer(String testNamespace, List<String> secrets) {
        DeployableClient deployableClient = new CliProtonDotnetDeployment(testNamespace);
        Deployment deployment = (Deployment) deployableClient.deployContainer(true, secrets);
        deployedContainers.put(deployment, testNamespace);
        DataStorer.dumpResourceToFile(deployment);
        return deployment;
    }

    public static Deployment deployCliCppContainer(String testNamespace) {
        DeployableClient deployableClient = new CliCppDeployment(testNamespace);
        Deployment deployment = (Deployment) deployableClient.deployContainer();
        deployedContainers.put(deployment, testNamespace);
        DataStorer.dumpResourceToFile(deployment);
        return deployment;
    }

    public static Deployment deploySecuredCliCppContainer(String testNamespace, List<String> secrets) {
        DeployableClient deployableClient = new CliCppDeployment(testNamespace);
        Deployment deployment = (Deployment) deployableClient.deployContainer(true, secrets);
        deployedContainers.put(deployment, testNamespace);
        DataStorer.dumpResourceToFile(deployment);
        return deployment;
    }

    public static Deployment deployCliProtonPythonContainer(String testNamespace) {
        DeployableClient deployableClient = new CliProtonPythonDeployment(testNamespace);
        Deployment deployment = (Deployment) deployableClient.deployContainer();
        deployedContainers.put(deployment, testNamespace);
        DataStorer.dumpResourceToFile(deployment);
        return deployment;
    }

    public static Deployment deploySecuredCliProtonPythonContainer(String testNamespace, List<String> secrets) {
        DeployableClient deployableClient = new CliProtonPythonDeployment(testNamespace);
        Deployment deployment = (Deployment) deployableClient.deployContainer(true, secrets);
        deployedContainers.put(deployment, testNamespace);
        DataStorer.dumpResourceToFile(deployment);
        return deployment;
    }

    public static Deployment deployCliRheaContainer(String testNamespace) {
        DeployableClient deployableClient = new CliRheaDeployment(testNamespace);
        Deployment deployment = (Deployment) deployableClient.deployContainer();
        deployedContainers.put(deployment, testNamespace);
        DataStorer.dumpResourceToFile(deployment);
        return deployment;
    }

    public static Deployment deploySecuredCliRheaContainer(String testNamespace, List<String> secrets) {
        DeployableClient deployableClient = new CliRheaDeployment(testNamespace);
        Deployment deployment = (Deployment) deployableClient.deployContainer(true, secrets);
        deployedContainers.put(deployment, testNamespace);
        DataStorer.dumpResourceToFile(deployment);
        return deployment;
    }

    public static Deployment deployMqttClientsContainer(String testNamespace) {
        DeployableClient deployableClient = new MqttDeployment(testNamespace);
        Deployment deployment = (Deployment) deployableClient.deployContainer();
        deployedContainers.put(deployment, testNamespace);
        DataStorer.dumpResourceToFile(deployment);
        return deployment;
    }

    public static MessagingClient createMessagingClient(ClientType clientType, Pod execPod, String port, String address, String queue, int messages) {
        return createMessagingClient(clientType, execPod, execPod.getStatus().getPodIP(), port, address, queue, messages, null, null);
    }
    // ExecutionPod and serviceUrl are on same pod/IP
    public static MessagingClient createMessagingClient(ClientType clientType, Pod execPod, String port, ActiveMQArtemisAddress address, int messages) {
        return createMessagingClient(clientType, execPod, execPod.getStatus().getPodIP(), port, address, messages);
    }
    public static MessagingClient createMessagingClient(ClientType clientType, Pod execPod, String port, ActiveMQArtemisAddress address, int messages, boolean persistenceDisabled) {
        return createMessagingClient(clientType, execPod, execPod.getStatus().getPodIP(), port, address, messages, persistenceDisabled);
    }

    public static MessagingClient createMessagingClient(ClientType clientType, Pod execPod, String serviceUrl, String port, ActiveMQArtemisAddress address, int messages) {
        return createMessagingClient(clientType, execPod, serviceUrl, port, address, messages, null, null);
    }

    public static MessagingClient createMessagingClient(ClientType clientType, Pod execPod, String serviceUrl, String port, ActiveMQArtemisAddress address, int messages, boolean persistenceDisabled) {
        return createMessagingClient(clientType, execPod, serviceUrl, port, address, messages, null, null, persistenceDisabled);
    }

    public static MessagingClient createMessagingClient(ClientType clientType, Pod execPod, String port, ActiveMQArtemisAddress address, int messages, String username, String password) {
        return createMessagingClient(clientType, execPod, execPod.getStatus().getPodIP(), port, address, messages, username, password);
    }
    public static MessagingClient createMessagingClient(ClientType clientType, Pod execPod, String serviceUrl, String port, ActiveMQArtemisAddress address, int messages, String username, String password, boolean persistenceDisabled) {
        return createMessagingClient(clientType, execPod, serviceUrl, port, address.getSpec().getAddressName(), address.getSpec().getQueueName(), messages, username, password, persistenceDisabled);
    }
    public static MessagingClient createMessagingClient(ClientType clientType, Pod execPod, String serviceUrl, String port, ActiveMQArtemisAddress address, int messages, String username, String password) {
        return createMessagingClient(clientType, execPod, serviceUrl, port, address.getSpec().getAddressName(), address.getSpec().getQueueName(), messages, username, password);
    }
    public static MessagingClient createMessagingClient(ClientType clientType, Pod execPod, String serviceUrl, String port, String address, String queue, int messages, String username, String password, Boolean persistenceDisabled) {
        DeployableClient deployableClient;
        MessagingClient messagingClient;
        switch (clientType) {
            case BUNDLED_AMQP, BUNDLED_CORE -> {
                deployableClient = new BundledClientDeployment(execPod.getMetadata().getNamespace());
            }
            case ST_AMQP_QPID_JMS, ST_MQTT_V5, ST_MQTT_V3 -> {
                deployableClient = new CliJavaDeployment(execPod.getMetadata().getNamespace());
            }
            case ST_AMQP_PROTON_DOTNET -> {
                deployableClient = new CliProtonDotnetDeployment(execPod.getMetadata().getNamespace());
            }
            case ST_AMQP_PROTON_CPP -> {
                deployableClient = new CliCppDeployment(execPod.getMetadata().getNamespace());
            }
            case ST_AMQP_PROTON_PYTHON -> {
                deployableClient = new CliProtonPythonDeployment(execPod.getMetadata().getNamespace());
            }
            case ST_AMQP_RHEA -> {
                deployableClient = new CliRheaDeployment(execPod.getMetadata().getNamespace());
            }
            case ST_MQTT_PAHO -> {
                throw new ClaireNotImplementedException("MQTT PAHO client is not yet implemented");
            }
            default -> {
                throw new ClaireRuntimeException("Unknown Messaging Client type " + clientType);
            }
        }
        deployableClient.setContainer(execPod);
        BundledClientOptions options = new BundledClientOptions()
                .withDeployableClient(deployableClient)
                .withDestinationAddress(address)
                .withDestinationPort(port)
                .withMessageCount(messages)
                .withPassword(password)
                .withUsername(username)
                .withPersistenceDisabled(persistenceDisabled)
                .withDestinationQueue(queue)
                .withDestinationUrl(serviceUrl);
        if (clientType.equals(ClientType.BUNDLED_AMQP)) {
            messagingClient = new BundledAmqpMessagingClient(options);
        } else if (clientType.equals(ClientType.BUNDLED_CORE)) {
            messagingClient = new BundledCoreMessagingClient(options);
        } else if (clientType.equals(ClientType.ST_AMQP_QPID_JMS)) {
            messagingClient = new AmqpQpidClient(deployableClient, serviceUrl, port, address, queue, messages, username, password);
        } else if (clientType.equals(ClientType.ST_AMQP_PROTON_DOTNET)) {
            messagingClient = new AmqpProtonDotnetClient(deployableClient, serviceUrl, port, address, queue, messages, username, password);
        } else if (clientType.equals(ClientType.ST_AMQP_PROTON_CPP)) {
            messagingClient = new AmqpProtonCppClient(deployableClient, serviceUrl, port, address, queue, messages, username, password);
        } else if (clientType.equals(ClientType.ST_AMQP_PROTON_PYTHON)) {
            messagingClient = new AmqpProtonPythonClient(deployableClient, serviceUrl, port, address, queue, messages, username, password);
        } else if (clientType.equals(ClientType.ST_AMQP_RHEA)) {
            messagingClient = new AmqpRheaClient(deployableClient, serviceUrl, port, address, queue, messages, username, password);
        } else if (clientType.equals(ClientType.ST_MQTT_V5)) {
            messagingClient = new MqttV5Client(deployableClient, serviceUrl, port, address, queue, messages, username, password);
        } else if (clientType.equals(ClientType.ST_MQTT_V3)) {
            messagingClient = new MqttV3Client(deployableClient, serviceUrl, port, address, queue, messages, username, password);
        } else if (clientType.equals(ClientType.ST_MQTT_PAHO)) {
            throw new ClaireNotImplementedException("MQTT PAHO client is not yet implemented");
        } else {
            throw new ClaireRuntimeException("Unknown client " + clientType.toString());
        }

        return messagingClient;
    }
    public static MessagingClient createMessagingClient(ClientType clientType, Pod execPod, String serviceUrl, String port, String address, String queue, int messages, String username, String password) {
        return createMessagingClient(clientType, execPod, serviceUrl, port, address, queue, messages, username, password, false);
    }


    public static MessagingClient createMessagingClientTls(Pod execPod, String serviceUri, ActiveMQArtemisAddress address, int messages,
                                                           String saslMechanism, KeyStoreData keystoreData, KeyStoreData truststoreData, String secretName) {
        return createMessagingClientTls(execPod, serviceUri, address.getSpec().getAddressName(), address.getSpec().getQueueName(), messages, saslMechanism, keystoreData, truststoreData, secretName);
    }


    public static MessagingClient createMessagingClientTls(Pod execPod, String serviceUri, String address, String queue, int messages,
                                                           String saslMechanism, KeyStoreData keystoreData, KeyStoreData truststoreData, String secretName) {
        return createMessagingClientTls(execPod, serviceUri, address, queue, messages, saslMechanism,
                "/etc/" + secretName + "/" + keystoreData.getIdentifier(), keystoreData.getPassword(),
                "/etc/" + secretName + "/" + truststoreData.getIdentifier(), truststoreData.getPassword());
    }

    public static MessagingClient createMessagingClientTls(Pod execPod, String serviceUri, ActiveMQArtemisAddress address, int messages,
                                                           String saslMechanism, String keystore, String keystorePassword, String trustStore, String trustStorePassword) {
        return createMessagingClientTls(execPod, serviceUri, address.getSpec().getAddressName(), address.getSpec().getQueueName(), messages,
                saslMechanism, keystore, keystorePassword, trustStore, trustStorePassword);
    }

    public static MessagingClient createMessagingClientTls(Pod execPod, String serviceUri, String address, String queue, int messages,
                                                           String saslMechanism, String keystore, String keystorePassword, String trustStore, String trustStorePassword) {
        DeployableClient deployableClient = new CliJavaDeployment(execPod.getMetadata().getNamespace());
        deployableClient.setContainer(execPod);
        MessagingClient messagingClient = new AmqpQpidClient(deployableClient, serviceUri, address, queue, messages,
                saslMechanism, keystore, keystorePassword, trustStore, trustStorePassword);
        return messagingClient;
    }

    // Keycloak/Rhsso Resources
    public static Keycloak getKeycloakInstance(String namespace) {
        // Keycloak resources managed
        if (environmentOperator.isUpstreamArtemis()) {
            return new Keycloak(environmentOperator, kubeClient, namespace);
        } else {
            return new Rhsso(environmentOperator, kubeClient, namespace);
        }
    }

    public static Openldap getOpenldapInstance(String namespace) {
        // Keycloak resources managed
        return new Openldap(kubeClient, namespace);
    }

    public static Postgres getPostgresInstance(String namespace) {
        // Keycloak resources managed
        return new Postgres(kubeClient, namespace);
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
            LOGGER.warn("[{}] Not found any CR! {} ", updatedCr.getMetadata().getNamespace(), updatedCr.getMetadata().getName());
        } else {
            LOGGER.warn("[{}] Found too many {} CRs! {} ", updatedCr.getMetadata().getNamespace(), removeTs.size(), updatedCr.getMetadata().getName());
        }
    }

    public static EnvironmentOperator getEnvironment() {
        if (environmentOperator == null) {
            environmentOperator = new EnvironmentOperator();
        }
        return environmentOperator;
    }

    public static Extension generateSanDnsNames(ActiveMQArtemis broker, List<String> serviceNames) {
        // DNS:$APPLICATION_NAME-$ACCEPTOR-$ORDINAL-svc-rte-$NAMESPACE.$DOMAIN_NAME"
        // Route
        // artemis-broker-my-amqp-0-svc-rte    artemis-broker-my-amqp-0-svc-rte-namespacename.apps.lala.amq-broker-qe.my-host.com
        // Ingress
        // artemis-broker-my-amqp-0-svc-ing    artemis-broker-my-amqp-0-svc-ing.apps.artemiscloud.io
        Extension sanExtension;
        String appName = broker.getMetadata().getName();
        String namespace = broker.getMetadata().getNamespace();

        // platform specific
        String domain = kubeClient.getPlatformIngressDomainUrl(namespace);

        ASN1EncodableVector sanNames = new ASN1EncodableVector();
        int size = broker.getSpec().getDeploymentPlan().getSize();
        for (int i = 0; i < size; i++) {
            for (String acceptorName : serviceNames) {
                sanNames.add(new GeneralName(GeneralName.dNSName, appName + "-" + acceptorName + "-" + i + "-" + domain));
//                sanNames.add(new GeneralName(GeneralName.dNSName, "*.app-META-INF-dev.net"));
            }
        }

        for (int i = 0; i < sanNames.size(); i++) {
            LOGGER.debug("[TLS] Created SAN=DNS:{}", sanNames.get(i).toString());
        }

        try {
            GeneralNames sans = GeneralNames.getInstance(new DERSequence(sanNames));
            sanExtension = new Extension(Extension.subjectAlternativeName, false, sans.getEncoded());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return sanExtension;
    }

    public static String generateDefaultBrokerDN() {
        // Make sure you use correct kubernetesClient when using multi-cluster deployment
        return generateDefaultBrokerDN("Broker");
    }
    public static String generateDefaultBrokerDN(String ou) {
        return "C=CZ, L=Brno, O=ArtemisCloud, OU=" + ou + ", CN=" +
                getKubeClient().getKubernetesClient().getMasterUrl().getHost().replace("api", "*");
    }

    public static String generateDefaultClientDN() {
        return generateDefaultClientDN("Client");
    }
    public static String generateDefaultClientDN(String ou) {
        return "C=CZ, L=Brno, O=ArtemisCloud, OU=" + ou + ", CN=" +
                getKubeClient().getKubernetesClient().getMasterUrl().getHost().replace("api", "*");
    }

    public static void setTestInfo(TestInfo testInfoNew) {
        testInfo = testInfoNew;
    }

    public static TestInfo getTestInfo() {
        return testInfo;
    }
}
