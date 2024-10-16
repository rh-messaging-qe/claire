/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.container;

import io.brokerqe.claire.helper.ContainerHelper;
import io.brokerqe.claire.ResourceManager;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ZookeeperContainerCluster {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZookeeperContainerCluster.class);

    private static final String REPLACEMENT_NODE_NAME = "%NODE_NAME%";
    private static final String REPLACEMENT_ID = "%ID%";

    private static final String ZOO_SERVERS_PATTERN = String.format("server.%s=%s:%d:%d;%d", REPLACEMENT_ID,
            REPLACEMENT_NODE_NAME, ZookeeperContainer.FOLLOWER_PORT, ZookeeperContainer.ELECTION_PORT,
            ZookeeperContainer.CLIENT_PORT);

    private final Map<String, ZookeeperContainer> nodesMap = new HashMap<>();
    private final List<String> zooServers = new ArrayList<>();

    public ZookeeperContainerCluster(int clusterSize, String nodeNamePrefix) {
        for (int i = 1; i <= clusterSize; i++) {
            createNode(i, nodeNamePrefix);
        }
    }

    public void start() {
        LOGGER.debug("[Zookeeper] Creating cluster");
        nodesMap.forEach((String name, ZookeeperContainer node) -> node.withZooServers(String.join(" ", zooServers)));
        ContainerHelper.startContainersInParallel(nodesMap.values().toArray(new ZookeeperContainer[0]));
    }

    public void stop() {
        ContainerHelper.stopContainers(nodesMap.values().toArray(new ZookeeperContainer[0]));
    }

    private void createNode(int id, String name) {
        String nodeName = name + id;
        LOGGER.trace("[Zookeeper {}] Creating node", nodeName);
        ZookeeperContainer node = ResourceManager.getZookeeperContainerInstance(nodeName);
        node.withNetworkAlias(nodeName);
        node.withZooMyId(id);
        node.withLogWait(ZookeeperContainer.ADMIN_SERVER_LOG_REGEX);
        node.withStandAloneEnabled(false);
        String zooServerWithId = StringUtils.replace(ZOO_SERVERS_PATTERN, REPLACEMENT_ID, String.valueOf(id));
        String zooServerWithIdAndNodeName = StringUtils.replace(zooServerWithId, REPLACEMENT_NODE_NAME, nodeName);
        zooServers.add(zooServerWithIdAndNodeName);
        nodesMap.put(nodeName, node);
    }

}
