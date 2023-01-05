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
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ResourceManager {

    final static Logger LOGGER = LoggerFactory.getLogger(ResourceManager.class);

    private static MixedOperation<ActiveMQArtemis, KubernetesResourceList<ActiveMQArtemis>, Resource<ActiveMQArtemis>> artemisClient;
    private static MixedOperation<ActiveMQArtemisAddress, KubernetesResourceList<ActiveMQArtemisAddress>, Resource<ActiveMQArtemisAddress>> artemisAddressClient;
    private static MixedOperation<ActiveMQArtemisSecurity, KubernetesResourceList<ActiveMQArtemisSecurity>, Resource<ActiveMQArtemisSecurity>> artemisSecurityClient;
    private static MixedOperation<ActiveMQArtemisScaledown, KubernetesResourceList<ActiveMQArtemisScaledown>, Resource<ActiveMQArtemisScaledown>> artemisScaledownClient;

    private static List<ArtemisCloudClusterOperator> operatorList = new ArrayList<>();
    private static Boolean projectCODeploy;
    private static ResourceManager resourceManager = null;

    private ResourceManager(Environment environment) {
        KubeClient kubeClient = new KubeClient("default");
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
            operatorList.add(clusterOperator);
            return clusterOperator;
        } else {
            LOGGER.warn("Not deploying operator! " + "'" + Constants.PROJECT_CO_MANAGE_KEY + "' is 'false'");
            return null;
        }
    }

    public static void undeployArtemisClusterOperator(ArtemisCloudClusterOperator clusterOperator) {
        if (projectCODeploy) {
            clusterOperator.undeployOperator(true);
            operatorList.remove(clusterOperator);
        } else {
            LOGGER.warn("Not deploying operator! " + "'" + Constants.PROJECT_CO_MANAGE_KEY + "' is 'false'");
        }
    }

    public static void undeployAllClientsContainers() {
        MessagingAmqpClient.undeployAllClientsContainers();
    }

    public static boolean isClusterOperatorManaged() {
        return projectCODeploy;
    }

    public static List<ArtemisCloudClusterOperator> getArtemisClusterOperators() {
        return operatorList;
    }

    public static ArtemisCloudClusterOperator getArtemisClusterOperator(String namespace) {
        return operatorList.stream().filter(operator -> operator.getNamespace().equals(namespace)).findFirst().orElse(null);
    }

}
