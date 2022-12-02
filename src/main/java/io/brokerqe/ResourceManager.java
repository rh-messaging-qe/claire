/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe;

import io.amq.broker.v1alpha1.ActiveMQArtemisSecurity;
import io.amq.broker.v2alpha1.ActiveMQArtemisScaledown;
import io.amq.broker.v2alpha3.ActiveMQArtemisAddress;
import io.amq.broker.v2alpha5.ActiveMQArtemis;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class ResourceManager {

    final static Logger LOGGER = LoggerFactory.getLogger(ResourceManager.class);

    private static MixedOperation<ActiveMQArtemis, KubernetesResourceList<ActiveMQArtemis>, Resource<ActiveMQArtemis>> artemisClient;
    private static MixedOperation<ActiveMQArtemisAddress, KubernetesResourceList<ActiveMQArtemisAddress>, Resource<ActiveMQArtemisAddress>> artemisAddressClient;
    private static MixedOperation<ActiveMQArtemisSecurity, KubernetesResourceList<ActiveMQArtemisSecurity>, Resource<ActiveMQArtemisSecurity>> artemisSecurityClient;
    private static MixedOperation<ActiveMQArtemisScaledown, KubernetesResourceList<ActiveMQArtemisScaledown>, Resource<ActiveMQArtemisScaledown>> artemisScaledownClient;

    private static List<ActiveMQArtemisClusterOperator> operatorList = new ArrayList<>();
    private static String projectSettingsType;
    private static final ResourceManager RESOURCE_MANAGER = new ResourceManager();

    private ResourceManager() {
        KubeClient kubeClient = new KubeClient("default");
        artemisClient = kubeClient.getKubernetesClient().resources(ActiveMQArtemis.class);
        artemisAddressClient = kubeClient.getKubernetesClient().resources(ActiveMQArtemisAddress.class);
        artemisSecurityClient = kubeClient.getKubernetesClient().resources(ActiveMQArtemisSecurity.class);
        artemisScaledownClient = kubeClient.getKubernetesClient().resources(ActiveMQArtemisScaledown.class);
        setProjectProperties();
    }
    public static ResourceManager getInstance() {
        return RESOURCE_MANAGER;
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

    private void setProjectProperties() {
        Properties projectSettings = new Properties();
        FileInputStream projectSettingsFile = null;
        try {
            projectSettingsFile = new FileInputStream(Constants.PROJECT_SETTINGS_PATH);
            projectSettings.load(projectSettingsFile);
            projectSettingsType = String.valueOf(projectSettings.get("project.type"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ActiveMQArtemisClusterOperator deployArtemisClusterOperator(String namespace) {
        ActiveMQArtemisClusterOperator clusterOperator = null;
        switch (projectSettingsType) {
            case Constants.PROJECT_TYPE_AMQ:
                clusterOperator = new AMQClusterOperator(namespace);
                break;
            case Constants.PROJECT_TYPE_ARTEMIS:
                clusterOperator = new ArtemisClusterOperator(namespace);
                break;
            default:
                LOGGER.error("Unknown projectType! Exiting.");
                System.exit(5);
        }
        clusterOperator.deployOperator(true);
        operatorList.add(clusterOperator);
        return clusterOperator;
    }

    public static void removeArtemisClusterOperator(ActiveMQArtemisClusterOperator clusterOperator) {
        clusterOperator.undeployOperator(true);
        operatorList.remove(clusterOperator);
    }

    public static List<ActiveMQArtemisClusterOperator> getArtemisClusterOperators() {
        return operatorList;
    }


}
