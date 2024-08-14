/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.federation;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.activemqartemisspec.Acceptors;
import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.junit.TestValidSince;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@TestValidSince(ArtemisVersion.VERSION_2_33)
public abstract class MirroringTests extends AbstractSystemTests {
    protected static final Logger LOGGER = LoggerFactory.getLogger(MirroringTests.class);
    protected final String prodNamespace = getRandomNamespaceName("mirror-prod-tests", 2);
    protected final String drNamespace = getRandomNamespaceName("mirror-dr-tests", 2);
    protected static final String LOGGER_SECRET_NAME = "artemis-secret-logging-config";
    protected static final String DEBUG_LOG_FILE = Constants.PROJECT_TEST_DIR + "/resources/logging/debug-log4j2.properties";

    protected static final String AMQP_ACCEPTOR_NAME = "amqp-acceptor";
    protected static final String ALL_ACCEPTOR_NAME = "all-acceptor";
    protected static final String DR_BROKER_NAME = "dr-broker";
    protected static final String PROD_BROKER_NAME = "prod-broker";
    protected static final String ADMIN = "admin";
    protected static final String ADMIN_PASS = "adminPass";

    // Secured variables
    protected static final String CLIENT_SECRET = "client-tls-secret";
    protected static final String DR_BROKER_TLS_SECRET = "dr-broker-tls-secret";
    protected static final String PROD_BROKER_TLS_SECRET = "prod-broker-tls-secret";

    protected Acceptors drAmqpAcceptor;
    protected Acceptors drAllAcceptor;
    protected Acceptors prodAmqpAcceptor;
    protected Acceptors prodAllAcceptor;
    protected String allDefaultPort;
    protected ActiveMQArtemis drBroker;
//    protected ActiveMQArtemis dr2Broker;
    protected ActiveMQArtemis prodBroker;
    protected String addressABPrefix = "queue";
    protected String addressA = "queuea";
    protected String addressB = "queueb";
    protected List<String> prodBrokerUris;
    protected List<String> drBrokerUris;

    @BeforeAll
    void setupClusterOperator() {
        LOGGER.info("[{}] Creating new namespaces {} {}", prodNamespace, prodNamespace, drNamespace);
        getClient().createNamespace(prodNamespace, true);
        getClient().createNamespace(drNamespace, true);
        // Operator will watch both namespaces
        operator = ResourceManager.deployArtemisClusterOperatorClustered(prodNamespace, List.of(prodNamespace, drNamespace));
    }

    @AfterAll
    void teardownClusterOperator() {
        if (ResourceManager.isClusterOperatorManaged()) {
            ResourceManager.undeployArtemisClusterOperator(operator);
        }
        ResourceManager.undeployAllClientsContainers();

        getClient().deleteNamespace(drNamespace);
        getClient().deleteNamespace(prodNamespace);
    }
    abstract void setupDeployment(int size);

    void teardownDeployment(boolean secured) {
        if (secured) {
            getClient().deleteSecret(prodNamespace, PROD_BROKER_TLS_SECRET);
            getClient().deleteSecret(drNamespace, DR_BROKER_TLS_SECRET);
            getClient().deleteSecret(prodNamespace, CLIENT_SECRET);
        }
        ResourceManager.deleteArtemis(prodBroker);
        ResourceManager.deleteArtemis(drBroker);
    }

    String createAmqpConnectionBrokerUri(ActiveMQArtemis broker, Acceptors acceptors, boolean secured) {
        String brokerUri;
        String namespace = broker.getMetadata().getNamespace();
        String brokerName = broker.getMetadata().getName();
        if (secured) {
            // tcp://broker-dr-amqp-${STATEFUL_SET_ORDINAL}-svc-rte-dr.apps.abouchama-amq5.emea.aws.cee.support:443?sslEnabled=true;trustStorePath=/amq/extra/secrets/mytlssecret/client.ts;trustStorePassword=password;verifyHost=false
            // create it manually

            // get it from deployed resources (too late)
            brokerUri = getClient().getExternalAccessServiceUrlPrefixName(namespace, brokerName + "-" + acceptors.getName())
                    .get(0).replace("-0-svc", "-${STATEFUL_SET_ORDINAL}-svc");
            String secretsUri = String.format(
                    "trustStorePath=/amq/extra/secrets/%s/client.ts;trustStorePassword=brokerPass;" +
                    "keyStorePath=/amq/extra/secrets/%s/broker.ks;keyStorePassword=brokerPass;",
                    PROD_BROKER_TLS_SECRET, PROD_BROKER_TLS_SECRET);
            brokerUri = String.format("tcp://%s:443?sslEnabled=true;verifyHost=false;%s", brokerUri, secretsUri);
        } else {
            // tcp://dr-broker-all-acceptor-${STATEFUL_SET_ORDINAL}-svc.mirror-dr-tests.svc.cluster.local:61616
            brokerUri = getClient().getFirstServiceBrokerAcceptor(namespace, brokerName, acceptors.getName())
                    .getMetadata().getName().replace("-0-svc", "-${STATEFUL_SET_ORDINAL}-svc") + "." + namespace + ".svc.cluster.local";
            brokerUri = String.format("tcp://%s:%d", brokerUri, acceptors.getPort());
        }
        return brokerUri;
    }


    @Test
    abstract void simpleMirroringTest();

}
