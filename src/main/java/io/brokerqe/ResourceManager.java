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


public class ResourceManager {

    private static MixedOperation<ActiveMQArtemis, KubernetesResourceList<ActiveMQArtemis>, Resource<ActiveMQArtemis>> artemisClient;
    private static MixedOperation<ActiveMQArtemisAddress, KubernetesResourceList<ActiveMQArtemisAddress>, Resource<ActiveMQArtemisAddress>> artemisAddressClient;
    private static MixedOperation<ActiveMQArtemisSecurity, KubernetesResourceList<ActiveMQArtemisSecurity>, Resource<ActiveMQArtemisSecurity>> artemisSecurityClient;
    private static MixedOperation<ActiveMQArtemisScaledown, KubernetesResourceList<ActiveMQArtemisScaledown>, Resource<ActiveMQArtemisScaledown>> artemisScaledownClient;

    private static final ResourceManager RESOURCE_MANAGER = new ResourceManager();

    private ResourceManager() {
        KubeClient kubeClient = new KubeClient("default");
        artemisClient = kubeClient.getKubernetesClient().resources(ActiveMQArtemis.class);
        artemisAddressClient = kubeClient.getKubernetesClient().resources(ActiveMQArtemisAddress.class);
        artemisSecurityClient = kubeClient.getKubernetesClient().resources(ActiveMQArtemisSecurity.class);
        artemisScaledownClient = kubeClient.getKubernetesClient().resources(ActiveMQArtemisScaledown.class);
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

}
