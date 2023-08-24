/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.clients;

import io.brokerqe.claire.Constants;

public class MqttDeployment extends StClientDeployment implements KubernetesDeployableClient {

    public MqttDeployment(String namespace) {
        super(namespace);
    }

    @Override
    public String getPodName() {
        return Constants.PREFIX_MQTT_CLIENT;
    }

    @Override
    public String getContainerImageName() {
        return Constants.IMAGE_SYSTEMTEST_CLIENTS;
    }

}
