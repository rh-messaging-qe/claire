/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.security;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisAddress;
import io.amq.broker.v1beta1.activemqartemisspec.Acceptors;
import io.brokerqe.AbstractSystemTests;
import io.brokerqe.Constants;
import io.brokerqe.ResourceManager;
import io.brokerqe.clients.AmqpQpidClient;
import io.brokerqe.clients.MessagingClient;
import io.brokerqe.operator.ArtemisFileProvider;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

public class TLSSecurityTests extends AbstractSystemTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(TLSSecurityTests.class);
    private final String testNamespace = getRandomNamespaceName("tls-tests", 6);
    private final String brokerDN = "C=CZ, L=Brno, O=ArtemisCloud, OU=Broker, CN=artemis.io";
    private final String clientDN = "C=CZ, L=Brno, O=ArtemisCloud, OU=Client, CN=artemis.io";

    @BeforeAll
    void setupClusterOperator() {
        getClient().createNamespace(testNamespace, true);
        LOGGER.info("[{}] Creating new namespace to {}", testNamespace, testNamespace);
        operator = ResourceManager.deployArtemisClusterOperator(testNamespace);
    }

    @AfterAll
    void teardownClusterOperator() {
        ResourceManager.undeployArtemisClusterOperator(operator);
        if (!ResourceManager.isClusterOperatorManaged()) {
            LOGGER.info("[{}] Deleting namespace {}", testNamespace, testNamespace);
            getClient().deleteNamespace(testNamespace);
        }
        ResourceManager.undeployAllClientsContainers();
        getClient().deleteNamespace(testNamespace);
    }

    public void testTlsMessaging(Pod brokerPod, ActiveMQArtemisAddress myAddress, String externalBrokerUri, String secretName, String clientKeyStore, String clientKeyStorePassword, String clientTrustStore, String clientTrustStorePassword) {
        Deployment clients = ResourceManager.deploySecuredClientsContainer(testNamespace, secretName);
        Pod clientsPod = getClient().getFirstPodByPrefixName(testNamespace, Constants.PREFIX_SYSTEMTESTS_CLIENTS);
        int msgsExpected = 10;
        int sent = -1;
        int received = 0;

        // Publisher - Receiver
        MessagingClient messagingClient = new AmqpQpidClient(clientsPod, externalBrokerUri, myAddress, msgsExpected, clientKeyStore, clientKeyStorePassword, clientTrustStore, clientTrustStorePassword);
        sent = messagingClient.sendMessages();
        received = messagingClient.receiveMessages();
        assertThat(sent, equalTo(msgsExpected));
        assertThat(sent, equalTo(received));
        assertThat(messagingClient.compareMessages(), is(true));
        ResourceManager.undeployClientsContainer(testNamespace, clients);
    }

    @Test
    public void testMutualAuthentication() {
        String brokerSecretName = "broker-tls-secret";
        //  Bug must have secret for each acceptor https://issues.redhat.com/browse/ENTMQBR-4268
        String bugBrokerSecretName = brokerSecretName + "-openwire";
        String clientSecret = "client-tls-secret";
        String amqpAcceptorName = "my-amqp";
        String owireAcceptorName = "my-owire";
        Map<String, String> brokerTlsSecret = new HashMap<>();
        Map<String, String> clientTlsSecret = new HashMap<>();

        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, ArtemisFileProvider.getArtemisSingleExampleFile(), true);
        ActiveMQArtemisAddress tlsAddress = ResourceManager.createArtemisAddress(testNamespace, ArtemisFileProvider.getAddressQueueExampleFile());
        Acceptors amqpAcceptors = createAcceptor(amqpAcceptorName, "amqp", 5672, true, true, brokerSecretName);
        Acceptors owireAcceptors = createAcceptor(owireAcceptorName, "openwire", 61618, true, true, bugBrokerSecretName);

//        Map<String, KeyStoreData> keystores = CertificateManager.reuseDefaultGeneratedKeystoresFromFiles();
        Map<String, KeyStoreData> keystores = CertificateManager.generateCertificateKeystores(testNamespace, broker, brokerDN, clientDN,
                List.of(CertificateManager.generateSanDnsNames(getClient(), broker, List.of(amqpAcceptors, owireAcceptors))));
        // One Way TLS
        brokerTlsSecret.put(Constants.BROKER_KEYSTORE_ID, keystores.get(Constants.BROKER_KEYSTORE_ID).getEncodedKeystoreFileData());
        // broker expects `client.ts` key
        brokerTlsSecret.put(Constants.CLIENT_TRUSTSTORE_ID, keystores.get(Constants.BROKER_TRUSTSTORE_ID).getEncodedKeystoreFileData());
        brokerTlsSecret.put(Constants.KEY_KEYSTORE_PASSWORD, keystores.get(Constants.BROKER_KEYSTORE_ID).getEncodedPassword());
        brokerTlsSecret.put(Constants.KEY_TRUSTSTORE_PASSWORD, keystores.get(Constants.BROKER_TRUSTSTORE_ID).getEncodedPassword());

        // Clients TLS secret
        clientTlsSecret.put(Constants.CLIENT_KEYSTORE_ID, keystores.get(Constants.CLIENT_KEYSTORE_ID).getEncodedKeystoreFileData());
        clientTlsSecret.put(Constants.KEY_KEYSTORE_PASSWORD, keystores.get(Constants.CLIENT_KEYSTORE_ID).getEncodedPassword());
        clientTlsSecret.put(Constants.CLIENT_TRUSTSTORE_ID, keystores.get(Constants.CLIENT_TRUSTSTORE_ID).getEncodedKeystoreFileData());
        clientTlsSecret.put(Constants.KEY_TRUSTSTORE_PASSWORD, keystores.get(Constants.CLIENT_TRUSTSTORE_ID).getEncodedPassword());

        getClient().createSecret(testNamespace, brokerSecretName, brokerTlsSecret, true);
        getClient().createSecret(testNamespace, bugBrokerSecretName, brokerTlsSecret, true);
        getClient().createSecret(testNamespace, clientSecret, clientTlsSecret, true);

        broker = addAcceptorsWaitForPodReload(testNamespace, List.of(amqpAcceptors, owireAcceptors), broker);
        String brokerName = broker.getMetadata().getName();
        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, brokerName);
        List<String> brokerUris = getClient().getExternalAccessServiceUrlPrefixName(testNamespace, brokerName + "-" + amqpAcceptorName);
        LOGGER.info("[{}] Broker {} is up and running with TLS", testNamespace, brokerName);

        testTlsMessaging(brokerPod, tlsAddress, brokerUris.get(0), clientSecret, Constants.CLIENT_KEYSTORE_ID, keystores.get(Constants.CLIENT_KEYSTORE_ID).getPassword(), Constants.CLIENT_TRUSTSTORE_ID, keystores.get(Constants.CLIENT_TRUSTSTORE_ID).getPassword());

        ResourceManager.deleteArtemis(testNamespace, broker);
        ResourceManager.deleteArtemisAddress(testNamespace, tlsAddress);
        getClient().deleteSecret(testNamespace, brokerSecretName);
        getClient().deleteSecret(testNamespace, bugBrokerSecretName);
        getClient().deleteSecret(testNamespace, clientSecret);
    }

}
