/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.container;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.shaded.com.google.common.primitives.Ints;

import java.util.List;

public final class ZookeeperContainer extends AbstractGenericContainer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZookeeperContainer.class);

    static final int ADMIN_SERVER_PORT = 8080;
    static final int CLIENT_PORT = 2181;
    static final int ELECTION_PORT = 3888;
    static final int FOLLOWER_PORT = 2888;
    static final  List<Integer> DEFAULT_PORTS = List.of(CLIENT_PORT, FOLLOWER_PORT, ELECTION_PORT, ADMIN_SERVER_PORT);
    static final String ADMIN_SERVER_LOG_REGEX = ".*Started AdminServer on address.*\\n";
    private static final String EV_ZOO_MY_ID = "ZOO_MY_ID";
    private static final String EV_ZOO_SERVERS = "ZOO_SERVERS";
    private static final String EV_ZOO_STANDALONE_ENABLED = "ZOO_STANDALONE_ENABLED";

    public ZookeeperContainer(String name) {
        super(name, ENVIRONMENT_STANDALONE.getZookeeperContainerImage());
        container.addExposedPorts(Ints.toArray(ZookeeperContainer.DEFAULT_PORTS));
        this.name = name;
        this.type = ContainerType.ZOOKEEPER;
    }

    public void withZooMyId(int id) {
        LOGGER.debug("[{}] - with env var {} = {}", name, EV_ZOO_MY_ID, id);
        container.withEnv(EV_ZOO_MY_ID, String.valueOf(id));
    }

    public void withZooServers(String zooServers) {
        LOGGER.debug("[{}] - with env var {} = {}", name, EV_ZOO_SERVERS, zooServers);
        container.withEnv(EV_ZOO_SERVERS, zooServers);
    }

    public void withStandAloneEnabled(boolean value) {
        LOGGER.debug("[{}] - with env var {} = {}", name, EV_ZOO_STANDALONE_ENABLED, value);
        container.withEnv(EV_ZOO_STANDALONE_ENABLED, Boolean.toString(value));
    }

}
