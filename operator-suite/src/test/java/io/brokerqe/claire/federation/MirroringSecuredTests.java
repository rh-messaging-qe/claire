/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.federation;

import io.amq.broker.v1beta1.ActiveMQArtemisBuilder;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.junit.TestValidSince;
import io.brokerqe.claire.security.CertificateManager;
import io.brokerqe.claire.security.KeyStoreData;
import io.fabric8.kubernetes.api.model.Pod;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;

@TestValidSince(ArtemisVersion.VERSION_2_33)
public class MirroringSecuredTests extends MirroringTests {

    protected Map<String, KeyStoreData> drKeystores;
    protected Map<String, KeyStoreData> prodKeystores;

    @SuppressWarnings({"checkstyle:MethodLength"})
    void setupDeployment(int size) {
        getClient().createSecretEncodedData(prodNamespace, LOGGER_SECRET_NAME, Map.of(ArtemisConstants.LOGGING_PROPERTIES_CONFIG_KEY, TestUtils.getFileContentAsBase64(DEBUG_LOG_FILE)), true);
        getClient().createSecretEncodedData(drNamespace, LOGGER_SECRET_NAME, Map.of(ArtemisConstants.LOGGING_PROPERTIES_CONFIG_KEY, TestUtils.getFileContentAsBase64(DEBUG_LOG_FILE)), true);
        List<String> drBrokerProperties = List.of(
                "maxDiskUsage=85",
                "clusterConfigurations.my-cluster.producerWindowSize=-1",
                "addressSettings.#.redeliveryMultiplier=5",
                "criticalAnalyzer=true",
                "criticalAnalyzerTimeout=6000",
                "criticalAnalyzerCheckPeriod=-1",
                "criticalAnalyzerPolicy=LOG"
        );

        drAllAcceptor = createAcceptor(ALL_ACCEPTOR_NAME, "all", 61616, true, true, DR_BROKER_TLS_SECRET, true);
        drAmqpAcceptor = createAcceptor(AMQP_ACCEPTOR_NAME, "amqp", 5672, true, true, DR_BROKER_TLS_SECRET, true);
        prodAmqpAcceptor = createAcceptor(AMQP_ACCEPTOR_NAME, "amqp", 5672, true, true, PROD_BROKER_TLS_SECRET, true);
        prodAllAcceptor = createAcceptor(ALL_ACCEPTOR_NAME, "all", 61616, true, true, PROD_BROKER_TLS_SECRET, true);

        drBroker = new ActiveMQArtemisBuilder()
            .editOrNewMetadata()
                .withName(DR_BROKER_NAME)
                .withNamespace(drNamespace)
            .endMetadata()
            .editOrNewSpec()
                .withNewDeploymentPlan()
                    .withSize(size)
                    .withClustered(true)
                    .withPersistenceEnabled(true)
                    .withMessageMigration(true)
                    .withManagementRBACEnabled(true)
                    .withRequireLogin(true)
                    .withJournalType("aio")
                    .withEnableMetricsPlugin(true)
                    .withJolokiaAgentEnabled(true)
                    .withClustered(true)
                    .editOrNewExtraMounts()
                        .withSecrets(LOGGER_SECRET_NAME, DR_BROKER_TLS_SECRET)
                    .endExtraMounts()
                .endDeploymentPlan()
                .editOrNewConsole()
                    .withExpose(true)
                .endConsole()
                .withAcceptors(List.of(drAllAcceptor, drAmqpAcceptor))
                .withBrokerProperties(drBrokerProperties)
                .withAdminUser("drAdmin")
                .withAdminPassword("drAdminPass")
            .endSpec()
            .build();

        drKeystores = CertificateManager.generateCertificateKeystores(
                ResourceManager.generateDefaultBrokerDN("dr"),
                DR_BROKER_NAME,
                ResourceManager.generateDefaultClientDN("client"),
                "client",
                List.of(ResourceManager.generateSanDnsNames(drBroker, List.of(ALL_ACCEPTOR_NAME, AMQP_ACCEPTOR_NAME))),
                null
        );
        getClient().createSecretEncodedData(drNamespace, DR_BROKER_TLS_SECRET, CertificateManager.createBrokerKeystoreSecret(drKeystores));

        LOGGER.info("[{}] Deploying Disaster Recovery Broker {}", drNamespace, DR_BROKER_NAME);
        drBroker = ResourceManager.createArtemis(drBroker);

        String amqpConnectionDrUri = createAmqpConnectionBrokerUri(drBroker, drAllAcceptor, true);
        LOGGER.warn("[{}] Broker {} is using AMQPConnections.dr.uri {}", prodNamespace, PROD_BROKER_NAME, amqpConnectionDrUri);
        List<String> prodBrokerProperties = List.of(
                "maxDiskUsage=85",
                "clusterConfigurations.my-cluster.producerWindowSize=-1",
                "addressSettings.#.redeliveryMultiplier=5",
                "criticalAnalyzer=true",
                "criticalAnalyzerTimeout=6000",
                "criticalAnalyzerCheckPeriod=-1",
                "criticalAnalyzerPolicy=LOG",
                // Mirror DR
                // tcp://dr-broker-all-acceptor-${STATEFUL_SET_ORDINAL}-svc.mirror-dr-tests.svc.cluster.local:61616
                // tcp://dr-broker-amqp-acceptor-${STATEFUL_SET_ORDINAL}-svc-rte-mirror-dr-tests.apps.qe-41256-d1.broker.app-services-dev.net
                // tcp://dr-broker-all-acceptor-${STATEFUL_SET_ORDINAL}-svc-rte-mirror-dr-tests.apps.qe-41256-d1.broker.app-services-dev.net:443?sslEnabled=true;verifyHost=false;trustStorePath=/amq/extra/secrets/prod-broker-tls-secret/client.ts;trustStorePassword=brokerPass;keyStorePath=/amq/extra/secrets/prod-broker-tls-secret/broker.ks;keyStorePassword=brokerPass;
                "AMQPConnections.dr.uri=" + amqpConnectionDrUri,
                "AMQPConnections.dr.retryInterval=5000",
                "AMQPConnections.dr.user=drAdmin",
                "AMQPConnections.dr.password=drAdminPass",
                "AMQPConnections.dr.connectionElements.mirror.type=MIRROR",
                "AMQPConnections.dr.connectionElements.mirror.messageAcknowledgements=true",
                "AMQPConnections.dr.connectionElements.mirror.queueCreation=true",
                "AMQPConnections.dr.connectionElements.mirror.queueRemoval=true"
                // BackUp DR
                //                              tcp://dr-broker-all-acceptor-${STATEFUL_SET_ORDINAL}-svc-rte-mirror-dr-tests.apps.qe-41256-d1.broker.app-services-dev.net:443?sslEnabled=true;verifyHost=false;trustStorePath=/amq/extra/secrets/prod-broker-tls-secret/client.ts;trustStorePassword=brokerPass;keyStorePath=/amq/extra/secrets/prod-broker-tls-secret/broker.ks;keyStorePassword=brokerPass;
//                AMQPConnections.backup-dr.uri=tcp://broker-backup-dr-amqp-${STATEFUL_SET_ORDINAL}-svc-rte-dr.apps.abouchama-amq5.emea.aws.cee.support:443?sslEnabled=true;trustStorePath=/amq/extra/secrets/mytlssecret/client.ts;trustStorePassword=password;verifyHost=false
//        - AMQPConnections.backup-dr.retryInterval=5000
//                - AMQPConnections.backup-dr.user=admin
//                - AMQPConnections.backup-dr.password=admin
//                - AMQPConnections.backup-dr.connectionElements.mirror.type=MIRROR
        );

        prodBroker = new ActiveMQArtemisBuilder()
            .editOrNewMetadata()
                .withName(PROD_BROKER_NAME)
                .withNamespace(prodNamespace)
            .endMetadata()
            .editOrNewSpec()
                .withNewDeploymentPlan()
                    .withSize(size)
                    .withClustered(true)
                    .withPersistenceEnabled(true)
                    .withMessageMigration(true)
                    .withManagementRBACEnabled(true)
                    .withRequireLogin(true)
                    .withJournalType("aio")
                    .withEnableMetricsPlugin(true)
                    .withJolokiaAgentEnabled(true)
                    .withClustered(true)
                    .editOrNewExtraMounts()
                        .withSecrets(LOGGER_SECRET_NAME, PROD_BROKER_TLS_SECRET)
                    .endExtraMounts()
                .endDeploymentPlan()
                .editOrNewConsole()
                    .withExpose(true)
                .endConsole()
                .withAcceptors(List.of(prodAllAcceptor, prodAmqpAcceptor))
                .withBrokerProperties(prodBrokerProperties)
                .withAdminUser("prodAdmin")
                .withAdminPassword("prodAdminPass")
            .endSpec()
            .build();


        // ==== Prod broker settings ====
        prodKeystores = CertificateManager.generateCertificateKeystores(
                ResourceManager.generateDefaultBrokerDN("prod"),
                PROD_BROKER_NAME,
                ResourceManager.generateDefaultClientDN("client"),
                "client",
                List.of(ResourceManager.generateSanDnsNames(prodBroker, List.of(ALL_ACCEPTOR_NAME, AMQP_ACCEPTOR_NAME))),
                null
        );
        KeyStoreData truststoreDrBrokerData = drKeystores.get(Constants.BROKER_TRUSTSTORE_ID);
        KeyStoreData truststoreProdBrokerData = prodKeystores.get(Constants.BROKER_TRUSTSTORE_ID);
        CertificateManager.addToTruststore(truststoreProdBrokerData, truststoreDrBrokerData.getCertificateData().getCertificate(), truststoreDrBrokerData.getCertificateData().getAlias());
        CertificateManager.addToTruststore(truststoreDrBrokerData, truststoreProdBrokerData.getCertificateData().getCertificate(), truststoreProdBrokerData.getCertificateData().getAlias());

        getClient().createSecretEncodedData(prodNamespace, PROD_BROKER_TLS_SECRET, CertificateManager.createBrokerKeystoreSecret(prodKeystores));
        getClient().createSecretEncodedData(prodNamespace, CLIENT_SECRET, CertificateManager.createClientKeystoreSecret(prodKeystores));
        getClient().createSecretEncodedData(drNamespace, CLIENT_SECRET, CertificateManager.createClientKeystoreSecret(drKeystores));

        // delete original secret & recreate it with added truststore
        getClient().deleteSecret(drNamespace, DR_BROKER_TLS_SECRET);
        getClient().createSecretEncodedData(drNamespace, DR_BROKER_TLS_SECRET, CertificateManager.createBrokerKeystoreSecret(drKeystores));

        LOGGER.info("[{}] Deploying Production Broker {}", prodNamespace, PROD_BROKER_NAME);
        prodBroker = ResourceManager.createArtemis(prodBroker);

        // 2024-05-22 10:23:33,284 INFO  [org.apache.activemq.artemis.protocol.amqp.logger] AMQ111003:
        //*******************************************************************************************************************************
        //Connected on Server AMQP Connection dr on dr-broker-all-acceptor-0-svc.mirror-dr-tests.svc.cluster.local:61616 after 0 retries
        //*******************************************************************************************************************************
        Pod prodBrokerPod = getClient().getFirstPodByPrefixName(prodNamespace, PROD_BROKER_NAME);
        String prodLogs = getClient().getLogsFromPod(prodBrokerPod);
        LOGGER.info("[{}] Ensure {} logs contain successful AMQP Connection to {}", prodNamespace, PROD_BROKER_NAME, DR_BROKER_NAME);
        assertThat(prodLogs, allOf(
                containsString("AMQ111003"),
                containsString("Connected on Server AMQP Connection dr on " + DR_BROKER_NAME + "-" + ALL_ACCEPTOR_NAME)
        ));

        // TODO: use more complex address format to check using filters
        ResourceManager.createArtemisAddress(prodNamespace, "queuea", "queue.b");
        ResourceManager.createArtemisAddress(prodNamespace, "queueb", "queue.b");
        prodBrokerUris = getClient().getExternalAccessServiceUrlPrefixName(prodNamespace, PROD_BROKER_NAME);
        drBrokerUris = getClient().getExternalAccessServiceUrlPrefixName(drNamespace, DR_BROKER_NAME);
    }

    @Test
    void simpleMirroringTest() {
        setupDeployment(1);
        // client secrets are same for both broker Keystores
        testTlsMessaging(prodNamespace, addressA, addressA, prodBrokerUris.get(0), null, CLIENT_SECRET,
                Constants.CLIENT_KEYSTORE_ID, prodKeystores.get(Constants.CLIENT_KEYSTORE_ID).getPassword(),
                Constants.CLIENT_TRUSTSTORE_ID, prodKeystores.get(Constants.CLIENT_TRUSTSTORE_ID).getPassword());
        // TODO: check msg count on DR broker queue stat
        teardownDeployment(true);
    }
}
