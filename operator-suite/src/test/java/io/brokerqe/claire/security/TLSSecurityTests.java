/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.security;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.activemqartemisspec.Acceptors;
import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.KubernetesArchitecture;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.helpers.brokerproperties.BPActiveMQArtemisAddress;
import io.brokerqe.claire.junit.DisabledTestArchitecture;
import io.brokerqe.claire.junit.TestValidSince;
import io.brokerqe.claire.junit.TestValidUntil;
import io.fabric8.kubernetes.api.model.HasMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class TLSSecurityTests extends AbstractSystemTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(TLSSecurityTests.class);
    private final String testNamespace = getRandomNamespaceName("tls-tests", 3);

    private final static String ROOT_CERT = """
                apiVersion: cert-manager.io/v1
                kind: Certificate
                metadata:
                 name: root-ca
                 namespace: cert-manager
                spec:
                 isCA: true
                 commonName: “amq.io.root”
                 secretName: root-ca-secret
                 subject:
                   organizations:\s
                   - “www.amq.io”
                 issuerRef:
                   name: selfsigned-issuer
                   kind: ClusterIssuer
                """;
    private static final String KEYSTORE_PW_TEMPLATE = """
                kind: Secret
                metadata:
                  name: password-secret
                  namespace: %s
                apiVersion: v1
                data:
                  keystorePassword: %s
                type: Opaque
                """;
    private final static String CLIENT_CERT_TEMPLATE = """
                apiVersion: cert-manager.io/v1
                kind: Certificate
                metadata:
                 name: %s
                spec:
                 isCA: false
                 commonName: “amq.io”
                 dnsNames:
                %s
                 secretName: %s
                 subject:
                   organizations:\s
                   - “www.amq.io”
                 issuerRef:
                   name: root-ca-issuer
                   kind: ClusterIssuer
                 privateKey:
                  algorithm: RSA
                  encoding: PKCS1
                  size: 4096
                 keystores:
                  jks:
                    create: true
                    passwordSecretRef:
                      name: password-secret
                      key: keystorePassword
                """;
    private final static String ROOT_CA_ISSUER = """
            apiVersion: cert-manager.io/v1
            kind: ClusterIssuer
            metadata:
             name: root-ca-issuer
            spec:
             ca:
               secretName: root-ca-secret""";
    private final static String BROKER_CERT_TEMPLATE = """
                apiVersion: cert-manager.io/v1
                kind: Certificate
                metadata:
                 name: %s
                spec:
                 isCA: false
                 commonName: “amq.io”
                 dnsNames:
                %s
                 secretName: %s
                 subject:
                   organizations:\s
                   - “www.amq.io”
                 issuerRef:
                   name: root-ca-issuer
                   kind: ClusterIssuer
                """;
    private static final String SELF_SIGNED_ISSUER = """
                                     apiVersion: cert-manager.io/v1
                                     kind: ClusterIssuer
                                     metadata:
                                      name: selfsigned-issuer
                                     spec:
                                      selfSigned: {}""";
    private static final String BUNDLE = """
                apiVersion: trust.cert-manager.io/v1alpha1
                kind: Bundle
                metadata:
                    name: %s
                spec:
                    sources:
                    - secret:
                        name: %s
                        key: ca.crt
                    target:
                        secret:
                            key: root-certs.pem
                        namespaceSelector:
                            matchLabels:
                                kubernetes.io/metadata.name: %s
                """;
    private List<HasMetadata> leafObjects;

    @BeforeEach
    void setupClusterOperator() {
        setupDefaultClusterOperator(testNamespace);
        leafObjects = new ArrayList<>();
    }

    @AfterEach
    void teardownClusterOperator() {
        for (HasMetadata item: leafObjects) {
            getKubernetesClient().resource(item).delete();
        }
        teardownDefaultClusterOperator(testNamespace);
    }

    @Test
    @TestValidSince(ArtemisVersion.VERSION_2_33)
    @DisabledTestArchitecture(archs = {KubernetesArchitecture.S390X, KubernetesArchitecture.PPC64LE})
    public void testMutualAuthentication() {
        doTestTlsMessaging(true, Constants.SECRETSOURCE.TRUST_MANAGER, true);
    }

    @Test
    @TestValidSince(ArtemisVersion.VERSION_2_33)
    public void testWithoutClientAuthentication() {
        doTestTlsMessaging(true, Constants.SECRETSOURCE.CERT_MANAGER, false);
    }

    @Test
    @TestValidSince(ArtemisVersion.VERSION_2_21)
    public void testSslManualMutual() {
        doTestTlsMessaging(true, Constants.SECRETSOURCE.MANUAL, true);
    }

    @Test
    @TestValidSince(ArtemisVersion.VERSION_2_21)
    public void testSslManualNonMutual() {
        doTestTlsMessaging(true, Constants.SECRETSOURCE.MANUAL, false);
    }

    @Test
    @TestValidUntil(ArtemisVersion.VERSION_2_21)
    public void testMutualAuthenticationOldVersion() {
        doTestTlsMessaging(false, Constants.SECRETSOURCE.MANUAL, true);
    }

    private void deployTrustManagerBundle(String bundleName, String certificateName, String namespace) {
        String trustManagerBundle = String.format(BUNDLE, bundleName, certificateName, namespace);
        HasMetadata trustBundleObj = getKubernetesClient().resource(trustManagerBundle).inNamespace(testNamespace).createOrReplace();
        leafObjects.add(trustBundleObj);
    }


    private void deployCertManagerIssuer() {
        HasMetadata issuerObj = getKubernetesClient().resource(SELF_SIGNED_ISSUER).inNamespace(testNamespace).createOrReplace();
        leafObjects.add(issuerObj);
    }

    private void deployCertManagerRootCert() {
        HasMetadata rootCertObj = getKubernetesClient().resource(ROOT_CERT).createOrReplace();
        HasMetadata rootCertCAObj = getKubernetesClient().resource(ROOT_CA_ISSUER).inNamespace(testNamespace).createOrReplace();
        leafObjects.add(rootCertObj);
        leafObjects.add(rootCertCAObj);
    }

    private void deployKeystorePassword(String password, String namespace) {
        String pass64 =  Base64.getEncoder().encodeToString(password.getBytes());
        String secret = String.format(KEYSTORE_PW_TEMPLATE, namespace, pass64);
        HasMetadata secretObj = getKubernetesClient().resource(secret).inNamespace(namespace).createOrReplace();
        leafObjects.add(secretObj);

    }
    private void deployCertManagerClientCert(String certificateName, String urls, String namespace) {
        String certificate = String.format(CLIENT_CERT_TEMPLATE, certificateName, urls, certificateName);
        //client.ts, client.ks
        HasMetadata certObj = getKubernetesClient().resource(certificate).inNamespace(namespace).createOrReplace();
        leafObjects.add(certObj);
    }


    private void deployCertManagerBrokerCert(String certificateName, String urls) {
        String certificate = String.format(BROKER_CERT_TEMPLATE, certificateName, urls, certificateName);
        HasMetadata brokerCertificateObj = getKubernetesClient().resource(certificate).inNamespace(testNamespace).createOrReplace();
        leafObjects.add(brokerCertificateObj);
    }

    private void deployCertManagerCertificates() {
        deployCertManagerIssuer();
        deployCertManagerRootCert();
    }

    private String formatUrlForYaml(String url) {
        return String.format("   - \"%s\"\n", url);
    }

    private String getInternalUrls(String brokerName, int brokerCount) {
        String internalUrl = "%s-ss-%d.amq-broker-svc-rte-default.cluster.local";
        StringBuilder internalUrlBlock = new StringBuilder(formatUrlForYaml(String.format(internalUrl, brokerName, 0)));
        int i = 1;
        while (i < brokerCount) {
            internalUrlBlock.append("\n").append(formatUrlForYaml(String.format(internalUrl, brokerName, i - 1)));
            i++;
        }
        return internalUrlBlock.toString();
    }

    private void deployTrustStoreCerts(String password, String clientSecretName, String neededUrls, String namespace) {
        deployKeystorePassword(password, namespace);
        deployCertManagerClientCert(clientSecretName, neededUrls, namespace);
    }

    public void doTestTlsMessaging(boolean singleSecret, Constants.SECRETSOURCE source, boolean mutualAuthentication) {
        String brokerSecretName = "broker-tls-secret";
        String bugBrokerSecretName = brokerSecretName + "-openwire";
        String clientSecretName = "client-tls-secret";
        String amqpAcceptorName = "my-amqp";
        String owireAcceptorName = "my-owire";

        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, "tls-broker");
        BPActiveMQArtemisAddress tlsAddress = ResourceManager.createBPArtemisAddress(ArtemisConstants.ROUTING_TYPE_ANYCAST);
        Acceptors amqpAcceptors = createAcceptor(amqpAcceptorName, "amqp", 5672, true, true, brokerSecretName, mutualAuthentication);
        Acceptors owireAcceptors;

        if (singleSecret) {
            owireAcceptors = createAcceptor(owireAcceptorName, "openwire", 61618, true, true, brokerSecretName, mutualAuthentication);
        } else {
            //  Bug must have secret for each acceptor https://issues.redhat.com/browse/ENTMQBR-4268
            owireAcceptors = createAcceptor(owireAcceptorName, "openwire", 61618, true, true, bugBrokerSecretName, mutualAuthentication);
        }

        Map<String, KeyStoreData> keystores = new HashMap<>();
        if (source == Constants.SECRETSOURCE.MANUAL) {
//            keystores = CertificateManager.reuseDefaultGeneratedKeystoresFromFiles(); // kept for future debugging purposes
            keystores = CertificateManager.generateDefaultCertificateKeystores(
                    ResourceManager.generateDefaultBrokerDN(),
                    ResourceManager.generateDefaultClientDN(),
                    List.of(ResourceManager.generateSanDnsNames(broker, List.of(amqpAcceptorName, owireAcceptorName))),
                    null
            );

            getClient().createSecretEncodedData(testNamespace, brokerSecretName, CertificateManager.createBrokerKeystoreSecret(keystores));
            if (!singleSecret) {
                getClient().createSecretEncodedData(testNamespace, bugBrokerSecretName, CertificateManager.createBrokerKeystoreSecret(keystores));
            }

            // Two Way - Mutual Authentication (Clients TLS secret)
            getClient().createSecretEncodedData(testNamespace, clientSecretName, CertificateManager.createClientKeystoreSecret(keystores));
        } else {
            //acceptorConfigurations.new-acceptor.params.sslAutoReload=true
            List<String> autoReload = List.of(String.format(ArtemisConstants.AUTO_RELOAD_PROPERTY, amqpAcceptorName),
                    String.format(ArtemisConstants.AUTO_RELOAD_PROPERTY, owireAcceptorName));

            broker.getSpec().setBrokerProperties(autoReload);
            String brokerName = broker.getMetadata().getName();
            int brokerCount = broker.getSpec().getDeploymentPlan().getSize();
            deployCertManagerCertificates();
            String catchallNamespaceDomain = "*.apps";
            String externalUrl = formatUrlForYaml(getKubernetesClient().getMasterUrl().getHost().replace("api", catchallNamespaceDomain));
            String neededUrls = getInternalUrls(brokerName, brokerCount) + externalUrl;
            deployCertManagerBrokerCert(brokerSecretName, neededUrls);
            String password = "passwordthisislong";

            deployTrustStoreCerts(password, clientSecretName, neededUrls, testNamespace);
            deployTrustStoreCerts(password, clientSecretName, neededUrls, "cert-manager");

            KeyStoreData ksData = new KeyStoreData(null, "keystore.jks", "", password);
            KeyStoreData tsData = new KeyStoreData(null, "truststore.jks", "", password);

            keystores.put(Constants.CLIENT_KEYSTORE_ID, ksData);
            keystores.put(Constants.CLIENT_TRUSTSTORE_ID, tsData);
            if (source == Constants.SECRETSOURCE.TRUST_MANAGER) {
                deployTrustManagerBundle("amq-io", clientSecretName, testNamespace);
                amqpAcceptors.setTrustSecret("amq-io");
                owireAcceptors.setTrustSecret("amq-io");
            }
        }
        maybeAddSpec(broker).getSpec().setBrokerProperties(tlsAddress.getPropertiesList());
        broker = addAcceptorsWaitForPodReload(testNamespace, List.of(amqpAcceptors, owireAcceptors), broker);
        String brokerName = broker.getMetadata().getName();
        List<String> brokerUris = getClient().getExternalAccessServiceUrlPrefixName(testNamespace, brokerName + "-" + amqpAcceptorName);
        LOGGER.info("[{}] Broker {} is up and running with TLS", testNamespace, brokerName);

        // TLS Authentication for netty, but for Artemis as Guest due to JAAS settings
        if (source == Constants.SECRETSOURCE.MANUAL) {
            testTlsMessaging(testNamespace, tlsAddress, brokerUris.get(0), null, clientSecretName,
                    Constants.CLIENT_KEYSTORE_ID, keystores.get(Constants.CLIENT_KEYSTORE_ID).getPassword(),
                    Constants.CLIENT_TRUSTSTORE_ID, keystores.get(Constants.CLIENT_TRUSTSTORE_ID).getPassword());
        } else {
            testTlsMessaging(testNamespace, tlsAddress, brokerUris.get(0), null, clientSecretName,
                    "keystore.jks", keystores.get(Constants.CLIENT_KEYSTORE_ID).getPassword(),
                    "truststore.jks", keystores.get(Constants.CLIENT_TRUSTSTORE_ID).getPassword());
        }

        ResourceManager.deleteArtemis(testNamespace, broker);
        getClient().deleteSecret(testNamespace, brokerSecretName);
        if (!singleSecret) {
            getClient().deleteSecret(testNamespace, bugBrokerSecretName);
        }
        getClient().deleteSecret(testNamespace, clientSecretName);
    }

}
