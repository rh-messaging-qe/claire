/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.security;

import io.brokerqe.claire.Constants;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.operator.ArtemisFileProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(Constants.TAG_TLS)
@Tag(Constants.TAG_JAAS)
public class TLSCACertsTests extends TLSAuthorizationTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(TLSCACertsTests.class);

    @BeforeAll
    void setupDeployment() {
        testNamespace = getRandomNamespaceName("authz-ca-tests", 3);
        setupDefaultClusterOperator(testNamespace);
        setupCertificates();
    }

    @SuppressWarnings({"checkstyle:MethodLength"})
    private void setupCertificates() {
        tlsAddress = ResourceManager.createArtemisAddress(testNamespace, ArtemisFileProvider.getAddressQueueExampleFile());
        tlsAddressName = tlsAddress.getSpec().getAddressName();
        tlsAddressQueueName = tlsAddress.getSpec().getQueueName();
        Instant now = Instant.now();
        // START OF CERTIFICATE MAGIC
        Map<String, KeyStoreData> entityKeystores = new HashMap<>();

        //create RootCA, CA and sign following certs with CA certificate
        CertificateData rootCACertData = new CertificateData("rootca", "C=CZ, L=Brno, O=ArtemisCloud, OU=CertificateAuthority, CN=rootca", null);
        CertificateData myCACertData = new CertificateData("myca", "C=CZ, L=Brno, O=ArtemisCloud, OU=tls-tests, CN=myca", rootCACertData);

        // C=CZ, L=Brno, O=ArtemisCloud, OU=" + ou + ", CN=" + kubernetesClient.getMasterUrl().getHost().replace("api", "*");
        producerCertData = new CertificateData("producer", CertificateManager.generateArtemisCloudDN("tls-tests", "producer"), null, 30, myCACertData);
        consumerCertData = new CertificateData("consumer", CertificateManager.generateArtemisCloudDN("tls-tests", "consumer"), null, 30, myCACertData);
        browserCertData = new CertificateData("browser", CertificateManager.generateArtemisCloudDN("tls-tests", "browser"), null, 30, myCACertData);
        // certificate valid <-30, -10> days ago (expired)
        expiredBeforeCertData = new CertificateData("expiredBefore", CertificateManager.generateArtemisCloudDN("tls-tests", "expiredBefore"), null,
            Date.from(now.minus(Duration.ofDays(30L))), Date.from(now.minus(Duration.ofDays(10L))), myCACertData
        );

        // certificate valid <+10, +30> days in future (not valid yet)
        expiredAfterCertData = new CertificateData("expiredAfter", CertificateManager.generateArtemisCloudDN("tls-tests", "expiredAfter"), null,
            Date.from(now.plus(Duration.ofDays(10L))), Date.from(now.plus(Duration.ofDays(30L))), myCACertData);

        // certificate invalid validity startDate in future, endDate in past
        expiredCertData = new CertificateData("expired", CertificateManager.generateArtemisCloudDN("tls-tests", "expired"), null,
            Date.from(now.plus(Duration.ofDays(10L))), Date.from(now.minus(Duration.ofDays(10L))), myCACertData);

        producerKeystores = CertificateManager.createEntityKeystores(producerCertData, "producerPass");
        consumerKeystores = CertificateManager.createEntityKeystores(consumerCertData, "consumerPass");
        browserKeystores = CertificateManager.createEntityKeystores(browserCertData, "browserPass");
        expiredBeforeKeystores = CertificateManager.createEntityKeystores(expiredBeforeCertData, "expiredPass");
        expiredAfterKeystores = CertificateManager.createEntityKeystores(expiredAfterCertData, "expiredPass");
        expiredKeystores = CertificateManager.createEntityKeystores(expiredCertData, "expiredPass");

        entityKeystores.putAll(producerKeystores);
        entityKeystores.putAll(consumerKeystores);
        entityKeystores.putAll(browserKeystores);
        entityKeystores.putAll(expiredBeforeKeystores);
        entityKeystores.putAll(expiredAfterKeystores);
        entityKeystores.putAll(expiredKeystores);
        // END OF FIRST PART OF CERTIFICATE MAGIC

        createArtemisDeployment();

        // START OF SECOND PART OF CERTIFICATE MAGIC
        Map<String, KeyStoreData> keystores = CertificateManager.generateDefaultCertificateKeystores(
            ResourceManager.generateDefaultBrokerDN(),
            ResourceManager.generateDefaultClientDN(),
            List.of(ResourceManager.generateSanDnsNames(broker, List.of(amqpAcceptorName, Constants.WEBCONSOLE_URI_PREFIX))),
            myCACertData
        );

        KeyStoreData truststoreBrokerData = keystores.get(Constants.BROKER_TRUSTSTORE_ID);
        CertificateManager.addToTruststore(truststoreBrokerData, rootCACertData.getCertificate(), rootCACertData.getAlias());
        CertificateManager.addToTruststore(truststoreBrokerData, myCACertData.getCertificate(), myCACertData.getAlias());

        CertificateManager.addToTruststore(entityKeystores.get("producer.ts"), rootCACertData.getCertificate(), rootCACertData.getAlias());
        CertificateManager.addToTruststore(entityKeystores.get("producer.ts"), myCACertData.getCertificate(), myCACertData.getAlias());
        CertificateManager.addToTruststore(entityKeystores.get("consumer.ts"), rootCACertData.getCertificate(), rootCACertData.getAlias());
        CertificateManager.addToTruststore(entityKeystores.get("consumer.ts"), myCACertData.getCertificate(), myCACertData.getAlias());
        CertificateManager.addToTruststore(entityKeystores.get("browser.ts"), rootCACertData.getCertificate(), rootCACertData.getAlias());
        CertificateManager.addToTruststore(entityKeystores.get("browser.ts"), myCACertData.getCertificate(), myCACertData.getAlias());
        CertificateManager.addToTruststore(entityKeystores.get("expiredBefore.ts"), rootCACertData.getCertificate(), rootCACertData.getAlias());
        CertificateManager.addToTruststore(entityKeystores.get("expiredBefore.ts"), myCACertData.getCertificate(), myCACertData.getAlias());
        CertificateManager.addToTruststore(entityKeystores.get("expiredAfter.ts"), rootCACertData.getCertificate(), rootCACertData.getAlias());
        CertificateManager.addToTruststore(entityKeystores.get("expiredAfter.ts"), myCACertData.getCertificate(), myCACertData.getAlias());
        CertificateManager.addToTruststore(entityKeystores.get("expired.ts"), rootCACertData.getCertificate(), rootCACertData.getAlias());
        CertificateManager.addToTruststore(entityKeystores.get("expired.ts"), myCACertData.getCertificate(), myCACertData.getAlias());

        getClient().createSecretEncodedData(testNamespace, brokerSecretName, CertificateManager.createBrokerKeystoreSecret(keystores));
        getClient().createSecretEncodedData(testNamespace, clientSecretName, CertificateManager.createClientKeystoreSecret(keystores));
        getClient().createSecretEncodedData(testNamespace, producerSecretName, CertificateManager.createKeystoreSecret(producerKeystores, producerCertData.getAlias()));
        getClient().createSecretEncodedData(testNamespace, consumerSecretName, CertificateManager.createKeystoreSecret(consumerKeystores, consumerCertData.getAlias()));
        getClient().createSecretEncodedData(testNamespace, browserSecretName, CertificateManager.createKeystoreSecret(browserKeystores, browserCertData.getAlias()));
        getClient().createSecretEncodedData(testNamespace, expiredBeforeSecretName, CertificateManager.createKeystoreSecret(expiredBeforeKeystores, expiredBeforeCertData.getAlias()));
        getClient().createSecretEncodedData(testNamespace, expiredAfterSecretName, CertificateManager.createKeystoreSecret(expiredAfterKeystores, expiredAfterCertData.getAlias()));
        getClient().createSecretEncodedData(testNamespace, expiredSecretName, CertificateManager.createKeystoreSecret(expiredKeystores, expiredCertData.getAlias()));
        // END OF SECOND PART OF CERTIFICATE MAGIC

        broker = ResourceManager.createArtemis(testNamespace, broker, true);
        LOGGER.info("[{}] Broker {} is up and running with CertTextLoginModule", testNamespace, brokerName);
        clients = ResourceManager.deploySecuredClientsContainer(testNamespace, List.of(producerSecretName, consumerSecretName, browserSecretName, expiredBeforeSecretName, expiredAfterSecretName, expiredSecretName));
        forbiddenAddress = ResourceManager.createArtemisAddress(testNamespace, "forbidden-address", "forbidden-queue", "anycast");
        brokerUris = getClient().getExternalAccessServiceUrlPrefixName(testNamespace, brokerName + "-" + amqpAcceptorName);
        clientsPod = getClient().getFirstPodByPrefixName(testNamespace, Constants.PREFIX_SYSTEMTESTS_CLIENTS);
    }

}
