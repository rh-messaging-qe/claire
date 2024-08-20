/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.brokerqe.claire.clients.bundled;

import io.brokerqe.claire.clients.DeployableClient;

public class BundledClientOptions {
    DeployableClient deployableClient;
    String destinationUrl;
    String destinationPort;
    String destinationAddress;
    String destinationQueue;
    int messageCount;
    String username;
    String password;
    Boolean persistenceDisabled = false;
    Boolean multicast = false;

    public BundledClientOptions withDeployableClient(DeployableClient deployableClient) {
        this.deployableClient = deployableClient;
        return this;
    }

    public BundledClientOptions withDestinationUrl(String destinationUrl) {
        this.destinationUrl = destinationUrl;
        return this;
    }

    public BundledClientOptions withDestinationPort(String destinationPort) {
        this.destinationPort = destinationPort;
        return this;
    }

    public BundledClientOptions withDestinationAddress(String destinationAddress) {
        this.destinationAddress = destinationAddress;
        return this;
    }

    public BundledClientOptions withDestinationQueue(String destinationQueue) {
        this.destinationQueue = destinationQueue;
        return this;
    }

    public BundledClientOptions withMessageCount(int messageCount) {
        this.messageCount = messageCount;
        return this;
    }

    public BundledClientOptions withUsername(String username) {
        this.username = username;
        return this;
    }

    public BundledClientOptions withPassword(String password) {
        this.password = password;
        return this;
    }

    public BundledClientOptions withPersistenceDisabled(Boolean persistenceDisabled) {
        this.persistenceDisabled = persistenceDisabled;
        return this;
    }
    public BundledClientOptions withMulticast(Boolean multicast) {
        this.multicast = multicast;
        return this;

    }
}