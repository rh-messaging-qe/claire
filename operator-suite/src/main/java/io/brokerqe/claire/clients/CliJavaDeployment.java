/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.clients;

import io.brokerqe.claire.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CliJavaDeployment extends StClientDeployment implements KubernetesDeployableClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(CliJavaDeployment.class);

    @Override
    public String getPodName() {
        return Constants.PREFIX_SYSTEMTESTS_CLIENTS;
    }

    @Override
    public String getContainerImageName() {
        return Constants.IMAGE_SYSTEMTEST_CLIENTS;
    }

    public CliJavaDeployment(String namespace) {
        super(namespace);
    }

}
