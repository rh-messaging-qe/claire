/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.container;

import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.exception.ClaireRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ToxiproxyContainer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ToxiProxyContainer extends AbstractGenericContainer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToxiProxyContainer.class);

    private ToxiproxyClient toxiproxyClient;
    private List<Proxy> proxyList;

    public ToxiProxyContainer(String name) {
        super(name, null);
        container = new ToxiproxyContainer(ENVIRONMENT_STANDALONE.getToxiProxyContainerImage());
        LOGGER.debug("[{}] With default network: {}", name, ResourceManager.getDefaultNetwork());
        container.withNetwork(ResourceManager.getDefaultNetwork());
        type = ContainerType.TOXI_PROXY;
        proxyList = new ArrayList<>();
    }

    public void start() {
        super.start();
        toxiproxyClient = new ToxiproxyClient(container.getHost(), ((ToxiproxyContainer) container).getControlPort());
    }

    public void createProxy(String name, String listenAddress, String upstreamAddress) {
        try {
            Proxy proxy = toxiproxyClient.createProxy(name, listenAddress, upstreamAddress);
            proxyList.add(proxy);
        } catch (IOException e) {
            String errMsg = String.format("Error on creating proxy %s", e.getMessage());
            LOGGER.error(errMsg);
            throw new ClaireRuntimeException(errMsg, e);
        }
    }
}
